package eu.komars.forgescanner.model

/** One visible task at a time; inventory tasks finish only after the user confirms the list end. */
enum class ScanTarget(
    val title: String,
    val instruction: String,
    val field: ResourceField,
    val step: ScanStep,
    val isInventory: Boolean = false,
    val baseCount: Int = 0
) {
    MOUNT_KEYS(
        "Klucze wierzchowców",
        "Pokaż klucze i koszt przyzwania jednego wierzchowca.",
        ResourceField.MOUNT_KEYS, ScanStep.MOUNT_CURRENCY
    ),
    MOUNT_COST(
        "Koszt wierzchowca",
        "Pokaż koszt przyzwania jednego wierzchowca.",
        ResourceField.MOUNT_COST, ScanStep.MOUNT_CURRENCY
    ),
    MOUNTS(
        "Wierzchowce",
        "Pokaż górę listy wierzchowców. Uruchom skanowanie i przewiń do końca. Jeśli lista mieści się na ekranie, zaczekaj na odczyt.",
        ResourceField.MOUNTS, ScanStep.MOUNT_INVENTORY, true, 1
    ),
    TICKETS(
        "Bilety umiejętności",
        "Pokaż bilety i koszt pakietu pięciu przyzwań umiejętności.",
        ResourceField.TICKETS, ScanStep.SKILL_CURRENCY
    ),
    SKILL_COST(
        "Koszt umiejętności x5",
        "Pokaż koszt pakietu pięciu przyzwań umiejętności.",
        ResourceField.SKILL_COST, ScanStep.SKILL_CURRENCY
    ),
    PETS(
        "Zwierzaki lub jajka",
        "Pokaż górę listy zwierzaków lub jajek. Uruchom skanowanie i przewiń do końca listy.",
        ResourceField.PETS, ScanStep.PET_INVENTORY, true, 3
    ),
    HAMMERS(
        "Młotki",
        "Pokaż liczbę młotków.",
        ResourceField.HAMMERS, ScanStep.HAMMERS
    ),
    COINS("Monety", "Licznik monet na górnym pasku ekranu głównego (np. 2.12m).", ResourceField.COINS, ScanStep.EXTRAS),
    GEMS("Diamenty", "Różowy licznik diamentów na górnym pasku.", ResourceField.GEMS, ScanStep.EXTRAS),
    SHELLS("Skorupki", "Licznik skorupek w lewym górnym rogu ekranu zwierzaków.", ResourceField.SHELLS, ScanStep.EXTRAS),
    RED_VIALS("Czerwone fiolki", "Licznik czerwonych fiolek na ekranie drzewa technologii.", ResourceField.RED_VIALS, ScanStep.EXTRAS),
    GREEN_VIALS("Zielone fiolki", "Licznik zielonych fiolek na ekranie drzewa technologii.", ResourceField.GREEN_VIALS, ScanStep.EXTRAS);

    val optional: Boolean get() = this.field.optional

    val owner: ScanTarget get() = when (this) { MOUNT_COST -> MOUNT_KEYS; SKILL_COST -> TICKETS; else -> this }
    val counters: List<ScanTarget> get() = when (owner) {
        MOUNT_KEYS -> listOf(MOUNT_KEYS, MOUNT_COST)
        TICKETS -> listOf(TICKETS, SKILL_COST)
        else -> listOf(this)
    }
    val position: Int get() = when (owner) { HAMMERS, COINS, GEMS -> 1; MOUNT_KEYS, MOUNTS -> 2; TICKETS -> 3; else -> 4 }
    val displayTitle: String get() = when (this) {
        MOUNT_KEYS -> "Klucze i koszt wierzchowca"
        TICKETS -> "Bilety i koszt x5"
        else -> title
    }
    companion object { val workflow = listOf(HAMMERS, MOUNT_KEYS, MOUNTS, TICKETS, PETS) }
}

/**
 * Session-only workflow. Starting/resuming does not erase values; the application owns creation of
 * a fresh [ScanSession]. Neither partial inventory scans nor leaving the overlay commit a count.
 */
class GuidedScanFlow(private val sessionProvider: () -> ScanSession) {
    private var started = false
    private var targetIndex = 0
    private val completed = linkedSetOf<ScanTarget>()
    private val rawInventory = linkedMapOf<ScanTarget, Long>()
    private val usableInventory = linkedMapOf<ScanTarget, Long>()

    val isStarted: Boolean get() = started
    val isComplete: Boolean get() = started && targetIndex >= ScanTarget.workflow.size
    val isRunning: Boolean get() = started && !isComplete
    val currentTarget: ScanTarget? get() = if (isRunning) ScanTarget.workflow[targetIndex] else null
    val completedTargets: Set<ScanTarget> get() = completed.toSet()

    fun start(): ScanTarget? {
        started = true
        syncSessionStep()
        return currentTarget
    }

    /** Clears workflow progress only. It deliberately leaves the application-owned session alone. */
    fun reset() {
        started = false
        targetIndex = 0
        completed.clear()
        rawInventory.clear()
        usableInventory.clear()
    }

    fun next(): ScanTarget? {
        val target = requireNotNull(currentTarget) { "Skanowanie nie jest aktywne." }
        require(target in completed) { "Najpierw zatwierdź wynik bieżącego kroku." }
        targetIndex++
        syncSessionStep()
        return currentTarget
    }

    fun back(): ScanTarget? {
        if (!started) return null
        targetIndex = (targetIndex - 1).coerceAtLeast(0)
        syncSessionStep()
        return currentTarget
    }

    fun acceptNumber(target: ScanTarget, value: Long, approximate: Boolean = false, sourceText: String? = null) {
        requireCurrent(target)
        require(target.counters.size == 1) { "Zatwierdź zasób i koszt razem." }
        require(!target.isInventory) { "Listę należy zatwierdzić po zeskanowaniu wszystkich widoków." }
        sessionProvider().setValue(target.field, value, confirmed = true, approximate = approximate, sourceText = sourceText)
        completed += target
    }

    data class CounterReading(val value: Long, val approximate: Boolean = false, val source: String? = null)

    /** Validate both fields before changing the session; a failed cost must not leave half an update. */
    fun acceptPair(target: ScanTarget, readings: Map<ScanTarget, CounterReading>) {
        requireCurrent(target)
        require(target.counters.size == 2 && readings.keys == target.counters.toSet()) { "Wymagane są zasób i koszt." }
        for ((counter, reading) in readings) {
            require(reading.value in 0..ScanSession.MAX_VALUE && (!counter.field.isCost || reading.value > 0)) { "Nieprawidłowy zasób lub koszt." }
        }
        for ((counter, reading) in readings) {
            sessionProvider().setValue(counter.field, reading.value, approximate = reading.approximate, sourceText = reading.source)
            completed += counter
        }
    }

    /**
     * Call only after an explicit end-of-list confirmation. Base items are subtracted exactly once.
     * Pets and eggs represent the same inventory category, not two separate scan targets.
     */
    fun completeInventory(target: ScanTarget, totalRaw: Long, subtractBase: Boolean, approximate: Boolean = false) {
        requireCurrent(target)
        require(target.isInventory) { "Bieżący krok nie jest listą." }
        require(totalRaw in 0..ScanSession.MAX_VALUE) { "Liczba pozycji jest poza dozwolonym zakresem." }
        val deduction = if (subtractBase) target.baseCount.toLong() else 0L
        require(totalRaw >= deduction) { "Liczba pozycji jest mniejsza niż liczba bazowych pozycji do odjęcia." }
        val usable = totalRaw - deduction
        // Validation is complete before mutating either the workflow or the report values.
        if (target == ScanTarget.MOUNTS) {
            sessionProvider().setValue(ResourceField.MOUNTS, usable, approximate = approximate, sourceText = "Zatwierdzona lista wierzchowców")
        } else {
            sessionProvider().setValue(ResourceField.PETS, usable, approximate = approximate, sourceText = "Zatwierdzona lista zwierzaków lub jajek")
        }
        rawInventory[target] = totalRaw
        usableInventory[target] = usable
        completed += target
    }

    fun inventoryTotals(): Map<ScanTarget, Long> = usableInventory.toMap()
    fun inventoryRawTotals(): Map<ScanTarget, Long> = rawInventory.toMap()

    private fun requireCurrent(target: ScanTarget) {
        require(currentTarget == target) { "Wynik nie dotyczy bieżącego kroku." }
    }

    private fun syncSessionStep() {
        if (!started) return
        sessionProvider().goTo(currentTarget?.step ?: ScanStep.REVIEW)
    }
}
