package eu.komars.forgescanner.automation

import eu.komars.forgescanner.model.ScanTarget

/** Fixed, user-configured route. It contains no purchase, merge or summon action. */
enum class AutoScanStep(val title: String, val instruction: String, val target: ScanTarget? = null) {
    HAMMERS("Młotki", "Zaznacz cały dolny licznik kowadła z ikoną młotka i zapasem szerokości na większą liczbę.", ScanTarget.HAMMERS),
    OPEN_MOUNTS("Otwórz wierzchowce", "Zaznacz przycisk otwierający ekran wierzchowców."),
    MOUNT_KEYS("Klucze wierzchowców", "Zaznacz cały pasek kluczy z ikoną i zapasem miejsca na cyfry.", ScanTarget.MOUNT_KEYS),
    MOUNT_COST("Koszt wierzchowca", "Zaznacz koszt przyzwania jednego wierzchowca. Nie naciskaj przyzywania.", ScanTarget.MOUNT_COST),
    MOUNT_LIST("Lista wierzchowców", "Ustaw listę na górze i zaznacz jej obszar przewijania.", ScanTarget.MOUNTS),
    CLOSE_MOUNTS("Zamknij wierzchowce", "Zaznacz przycisk zamknięcia ekranu wierzchowców."),
    OPEN_RESEARCH("Umiejętności/jajka/badania", "Zaznacz przycisk otwierający ekran umiejętności/jajek/badań."),
    OPEN_SKILLS("Otwórz umiejętności", "Zaznacz zakładkę umiejętności."),
    SKILL_TICKETS("Bilety umiejętności", "Zaznacz cały pasek biletów z ikoną i zapasem miejsca na cyfry.", ScanTarget.TICKETS),
    SKILL_COST("Koszt umiejętności ×5", "Zaznacz koszt pakietu pięciu przyzwań. Nie naciskaj przyzywania.", ScanTarget.SKILL_COST),
    OPEN_PETS("Otwórz zwierzaki", "Zaznacz zakładkę zwierzaków lub jajek."),
    PET_LIST("Lista zwierzaków i jajek", "Ustaw listę na górze i zaznacz obszar samych ikon, bez paska wyposażenia.", ScanTarget.PETS),
    CLOSE_RESEARCH("Wróć do młotków", "Zaznacz przycisk zamknięcia ekranu umiejętności/jajek/badań, aby wrócić do ekranu młotków.");

    val isNavigation: Boolean get() = target == null
    val isInventory: Boolean get() = target?.isInventory == true
    val timeoutMillis: Long get() = if (isInventory) 120_000L else if (isNavigation) 15_000L else 40_000L
}

/** Values are provisional until the complete fixed route returns to the starting screen. */
data class AutoReading(val value: Long, val approximate: Boolean = false, val source: String? = null)
