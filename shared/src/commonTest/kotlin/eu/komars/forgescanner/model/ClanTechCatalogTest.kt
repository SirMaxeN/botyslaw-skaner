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

    @Test fun dayIconAndPopupMustAgree() {
        assertTrue(ClanTechCatalog.dayMatches(10,"Wojna klanów: dzień 1"))
        assertFalse(ClanTechCatalog.dayMatches(10,"Wojna klanów: dzień 2"))
    }
}
