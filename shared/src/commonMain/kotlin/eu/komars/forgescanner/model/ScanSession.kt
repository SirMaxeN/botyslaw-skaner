package eu.komars.forgescanner.model

import eu.komars.forgescanner.platform.nowMillis

enum class ResourceField(val label: String) {
    MOUNT_KEYS("Klucze wierzchowców"),
    MOUNT_COST("Koszt przyzwania 1 wierzchowca"),
    MOUNTS("Wierzchowce, bez 1 bazowego"),
    TICKETS("Bilety umiejętności"),
    SKILL_COST("Koszt przyzwania umiejętności x5"),
    PETS("Zwierzaki lub jajka, bez 3 bazowych"),
    HAMMERS("Młotki"),
    // Extras (app 1.5.1): shown with the clan resources on Discord, never scored; optional everywhere.
    COINS("Monety"),
    GEMS("Diamenty"),
    SHELLS("Skorupki"),
    RED_VIALS("Czerwone fiolki"),
    GREEN_VIALS("Zielone fiolki");

    val isCost: Boolean get() = this == MOUNT_COST || this == SKILL_COST
    val optional: Boolean get() = this in EXTRAS

    companion object {
        val EXTRAS: List<ResourceField> get() = listOf(COINS, GEMS, SHELLS, RED_VIALS, GREEN_VIALS)
        val required: List<ResourceField> get() = entries.filter { !it.optional }
    }
}

enum class ScanStep(val title: String, val instruction: String, val fields: List<ResourceField>) {
    MOUNT_CURRENCY(
        "2/4 · Klucze i koszt",
        "Otwórz przyzywanie wierzchowców. Pokaż liczbę kluczy i koszt jednego przyzwania, następnie naciśnij Odczytaj.",
        listOf(ResourceField.MOUNT_KEYS, ResourceField.MOUNT_COST)
    ),
    MOUNT_INVENTORY(
        "2/4 · Wierzchowce",
        "Otwórz wierzchowce. Zacznij od góry listy. Dodawaj pełne rzędy po 5, przewijając z widocznym wspólnym rzędem. Na końcu sprawdź wynik bez 1 bazowego.",
        listOf(ResourceField.MOUNTS)
    ),
    SKILL_CURRENCY(
        "3/4 · Bilety i koszt x5",
        "Otwórz przyzywanie umiejętności. Pokaż bilety i cenę pakietu x5. Upewnij się, że cena dotyczy pięciu przyzwań.",
        listOf(ResourceField.TICKETS, ResourceField.SKILL_COST)
    ),
    PET_INVENTORY(
        "4/4 · Zwierzaki lub jajka",
        "Otwórz zwierzaki lub jajka. Zacznij od góry, przewijaj z jednym wspólnym rzędem. Zliczaj tylko pozycje przeznaczone do łączenia; pomiń 3 bazowe zwierzaki.",
        listOf(ResourceField.PETS)
    ),
    HAMMERS(
        "1/4 · Młotki",
        "Pokaż licznik młotków. Sprawdź odczyt i przejdź do podsumowania.",
        listOf(ResourceField.HAMMERS)
    ),
    REVIEW("Podsumowanie", "Sprawdź wszystkie wartości. Dopiero zatwierdzony raport skopiuj do schowka.", ResourceField.entries.toList()),
    // After REVIEW so the manual step order (next() stops at REVIEW) is unchanged.
    EXTRAS("Dodatkowe zasoby", "Monety i diamenty (ekran główny), skorupki (zwierzaki), czerwone i zielone fiolki (drzewo technologii). Nie wpływają na punkty.", ResourceField.EXTRAS);
}

data class ResourceObservation(
    val value: Long,
    val confirmed: Boolean,
    val approximate: Boolean = false,
    val sourceText: String? = null
)

data class ScanSnapshot(
    val step: ScanStep,
    val values: Map<ResourceField, ResourceObservation>,
    val startedAtMillis: Long,
    val updatedAtMillis: Long
) {
    fun value(field: ResourceField): Long? = values[field]?.value
    val missingFields: List<ResourceField> get() = ResourceField.required.filter { !values.containsKey(it) }
    val unconfirmedFields: List<ResourceField> get() = ResourceField.entries.filter { values[it]?.confirmed == false }
    val readyToExport: Boolean get() = missingFields.isEmpty() && unconfirmedFields.isEmpty()
}

/** A scan is temporary local state. Missing OCR results never overwrite an earlier value. */
class ScanSession(private val clock: () -> Long = ::nowMillis) {
    private var startedAt = clock()
    private var updatedAt = startedAt
    private var currentStep = ScanStep.MOUNT_CURRENCY
    private val observations = linkedMapOf<ResourceField, ResourceObservation>()

    var onChange: ((ScanSnapshot)->Unit)? = null
    val step: ScanStep get() = currentStep

    fun setValue(field: ResourceField, value: Long, confirmed: Boolean = true, approximate: Boolean = false, sourceText: String? = null) {
        require(value in 0..MAX_VALUE) { "Wartość musi być pomiędzy 0 a $MAX_VALUE." }
        require(!field.isCost || value > 0) { "Koszt musi być większy od zera." }
        observations[field] = ResourceObservation(value, confirmed, approximate, sourceText)
        // A value confirmed by the user teaches the game-font reader the digits of that counter's last crop.
        if (confirmed) runCatching { CounterConfirmations.listener?.invoke(field.name, value) }
        updatedAt = clock()
        onChange?.invoke(snapshot())
    }

    fun removeValue(field: ResourceField) {
        observations.remove(field)
        updatedAt = clock()
        onChange?.invoke(snapshot())
    }

    fun confirm(field: ResourceField) {
        observations[field]?.let { observations[field] = it.copy(confirmed = true) }
        updatedAt = clock()
        onChange?.invoke(snapshot())
    }

    fun next(): ScanStep {
        currentStep = ScanStep.entries[(currentStep.ordinal + 1).coerceAtMost(ScanStep.REVIEW.ordinal)]
        onChange?.invoke(snapshot())
        return currentStep
    }

    fun back(): ScanStep {
        currentStep = ScanStep.entries[(currentStep.ordinal - 1).coerceAtLeast(0)]
        onChange?.invoke(snapshot())
        return currentStep
    }

    fun goTo(step: ScanStep) { currentStep = step;onChange?.invoke(snapshot()) }

    fun snapshot(): ScanSnapshot = ScanSnapshot(currentStep, observations.toMap(), startedAt, updatedAt)

    companion object {
        const val MAX_VALUE: Long = 9_000_000_000_000L
        fun restore(saved:ScanSnapshot)=ScanSession().apply {
            saved.values.forEach {(field,v)->setValue(field,v.value,v.confirmed,v.approximate,v.sourceText)}
            startedAt=saved.startedAtMillis;updatedAt=saved.updatedAtMillis;currentStep=saved.step
        }
    }
}
