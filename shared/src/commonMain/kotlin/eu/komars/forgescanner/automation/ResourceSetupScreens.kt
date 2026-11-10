package eu.komars.forgescanner.automation

import eu.komars.forgescanner.model.GameScreenKind

/** Visible controls share one screenshot; route actions still run in their original order. */
object ResourceSetupScreens {
    data class Screen(val title:String,val fields:List<AutoScanStep>,val instruction:String)
    private val home=Screen("Ekran główny",listOf(AutoScanStep.HAMMERS,AutoScanStep.COINS,AutoScanStep.GEMS,AutoScanStep.OPEN_MOUNTS,AutoScanStep.OPEN_RESEARCH),
        "Zaznacz młotki, monety i diamenty (górny pasek — panel Botysława przesunie się na czas odczytu), wejście do wierzchowców i wejście do badań na jednym obrazie.")
    private val mounts=Screen("Wierzchowce",listOf(AutoScanStep.MOUNT_KEYS,AutoScanStep.MOUNT_COST,AutoScanStep.MOUNT_LIST,AutoScanStep.CLOSE_MOUNTS),
        "Ustaw klucze, koszt, przewijaną listę i przycisk zamknięcia. Lista powinna być na górze.")
    private val tabs=Screen("Zakładki badań",listOf(AutoScanStep.OPEN_SKILLS,AutoScanStep.OPEN_PETS,AutoScanStep.OPEN_TECH),
        "Ustaw zakładki umiejętności, zwierzaków i drzewa technologii. Następnie otworzę umiejętności.")
    private val skills=Screen("Umiejętności",listOf(AutoScanStep.SKILL_TICKETS,AutoScanStep.SKILL_COST),
        "Ustaw bilety i koszt pakietu ×5 na otwartej zakładce umiejętności. Powrót używa dolnego przycisku zapisanego na ekranie głównym.")
    private val pets=Screen("Zwierzaki i jajka",listOf(AutoScanStep.SHELLS,AutoScanStep.PET_LIST),
        "Ustaw licznik skorupek (lewy górny róg) i przewijaną listę ikon na otwartej zakładce zwierzaków. Lista powinna być na górze. Powrót używa zapisanego dolnego przycisku.")
    private val tech=Screen("Drzewo technologii",listOf(AutoScanStep.RED_VIALS,AutoScanStep.GREEN_VIALS),
        "Ustaw liczniki czerwonych i zielonych fiolek w lewym górnym rogu drzewa technologii.")

    fun forStep(step:AutoScanStep,visible:GameScreenKind=GameScreenKind.UNKNOWN):Screen = when(step) {
        AutoScanStep.HAMMERS,AutoScanStep.COINS,AutoScanStep.GEMS,AutoScanStep.OPEN_MOUNTS,AutoScanStep.OPEN_RESEARCH->home
        AutoScanStep.MOUNT_KEYS,AutoScanStep.MOUNT_COST,AutoScanStep.MOUNT_LIST,AutoScanStep.CLOSE_MOUNTS->mounts
        AutoScanStep.SKILL_TICKETS,AutoScanStep.SKILL_COST->skills
        AutoScanStep.SHELLS,AutoScanStep.PET_LIST->pets
        AutoScanStep.RED_VIALS,AutoScanStep.GREEN_VIALS->tech
        AutoScanStep.OPEN_SKILLS,AutoScanStep.OPEN_PETS,AutoScanStep.OPEN_TECH,AutoScanStep.CLOSE_RESEARCH->tabs
    }
}
