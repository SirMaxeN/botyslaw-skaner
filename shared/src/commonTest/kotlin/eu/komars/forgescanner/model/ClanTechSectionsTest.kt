package eu.komars.forgescanner.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClanTechSectionHeadersTest {
    @Test fun headersAreRecognisedAndLevelLabelsAreNot() {
        assertEquals(ClanTechSections.Section.WAR, ClanTechSections.header("Wojna klanów"))
        assertEquals(ClanTechSections.Section.POTIONS, ClanTechSections.header("Klanowe mikstury techniczne"))
        assertEquals(ClanTechSections.Section.OTHER, ClanTechSections.header("Moc"))
        assertEquals(ClanTechSections.Section.OTHER, ClanTechSections.header("Zegary jaj"))
        assertNull(ClanTechSections.header("0/10"))
        assertNull(ClanTechSections.header("Maks."))
        assertNull(ClanTechSections.header("1.69%"))
    }

    @Test fun nodesBelongToTheNearestHeaderAboveOrTheCarriedSection() {
        val headers = listOf(ClanTechSections.Header(100, ClanTechSections.Section.OTHER), ClanTechSections.Header(900, ClanTechSections.Section.WAR))
        assertEquals(ClanTechSections.Section.OTHER, ClanTechSections.sectionOf(500, headers, null))
        assertEquals(ClanTechSections.Section.WAR, ClanTechSections.sectionOf(1000, headers, null))
        assertEquals(ClanTechSections.Section.POTIONS, ClanTechSections.sectionOf(50, headers, ClanTechSections.Section.POTIONS))
        assertEquals(ClanTechSections.Section.WAR, ClanTechSections.last(headers, null))
        assertTrue(ClanTechSections.allowed(ClanTechSections.Section.WAR, 3))
        assertFalse(ClanTechSections.allowed(ClanTechSections.Section.WAR, 16))
        assertTrue(ClanTechSections.allowed(ClanTechSections.Section.POTIONS, 17))
        assertFalse(ClanTechSections.allowed(ClanTechSections.Section.OTHER, 3))
        assertFalse(ClanTechSections.allowed(null, 3))
    }
}
