package eu.komars.forgescanner.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClanTechCatalogTest {
    @Test fun usesCurrentBonusAndIgnoresTheNextLevelAndDonationProgress() {
        assertEquals(7,ClanTechCatalog.level(0,listOf("7/10"),"Kucie +28% (+4%)\n3/246"))
        assertNull(ClanTechCatalog.level(0,listOf("7/100"),"Kucie +28% (+4%)"))
        assertNull(ClanTechCatalog.level(0,listOf("7/10"),"Kucie +24% (+4%)"))
    }

    @Test fun corroboratesAnIsolatedLabelAcrossTwoContrastsWhenCurrentBonusIsAbsent() {
        assertEquals(2,ClanTechCatalog.level(17,listOf("2/20","2 / 20"),"Darowizny (+5%)"))
        assertEquals(20,ClanTechCatalog.level(16,listOf("Maks.","Maks."),"Mikstury (+5%)"))
        assertNull(ClanTechCatalog.level(17,listOf("2/20","7/20"),"Darowizny (+5%)"))
        assertNull(ClanTechCatalog.level(17,listOf("2/20"),"Darowizny (+5%)"))
    }

    @Test fun zeroInTwoPopupReadsSurvivesAnUnreadableTreeLabel() {
        val windows = listOf(
            "Drzewo technologü\nAkcje Wojenne Kończenia Ulepszeń Drzewka Technologii dają 0% więcej Punktów Wwojny. (+4%)",
            "Drzewo technologii\nAkcje Wojenne Kończenia Ulepszeń Drzewka Technologii dają 0% więcej Punktów Wojny. (+4%)"
        )
        assertEquals(0, ClanTechCatalog.levelFromPopupReads(3, listOf("", ""), windows))
        assertEquals(0, ClanTechCatalog.level(3, listOf("0/10"), windows.first().replace("0%", "O%")))
        assertNull(ClanTechCatalog.levelFromPopupReads(3, listOf("", ""), windows.take(1)))
        assertNull(ClanTechCatalog.levelFromPopupReads(1, listOf("", ""), windows))
        assertNull(ClanTechCatalog.levelFromPopupReads(3, listOf("1/10"), windows))
        assertNull(ClanTechCatalog.levelFromPopupReads(3, emptyList(), windows + "Dają 8% więcej."))
        assertNull(ClanTechCatalog.levelFromPopupReads(3, emptyList(), List(2) { "Punkty Wojny. (+4%)" }))
    }

    @Test fun dayPopupCanIdentifyTheDayFromItsDescription() {
        val window = "wofna Kanôw Dzieńs\nAkcje Wojenne w Dniu 3 Wojny Klanów dają +40% więcej Punktów Wojny."
        assertTrue(ClanTechCatalog.dayMatches(12, window))
        assertFalse(ClanTechCatalog.dayMatches(13, window))
        assertFalse(ClanTechCatalog.dayMatches(12, "Wojna klanów: dzień 4. Akcje wojenne w dniu 3."))
        assertEquals(10, ClanTechCatalog.levelFromPopupReads(12, listOf(""), listOf(window, window)))
    }

    @Test fun dayIconAndPopupMustAgree() {
        assertTrue(ClanTechCatalog.dayMatches(10,"Wojna klanów: dzień 1"))
        assertFalse(ClanTechCatalog.dayMatches(10,"Wojna klanów: dzień 2"))
    }
}
