package eu.komars.forgescanner.model

import eu.komars.forgescanner.battle.GameDigitFont
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

class GameDigitPlausibilityTest {
    private fun glyph(pattern: (Int, Int) -> Boolean) = GameDigitFont.Glyph(.6,
        (0 until 14).joinToString("") { y -> (0 until 10).joinToString("") { x -> if (pattern(x, y)) "1" else "0" } })
    private val zero = glyph { x, y -> (x in 1..8 && (y == 1 || y == 12)) || (y in 1..12 && (x == 1 || x == 8)) }
    private val one = glyph { x, _ -> x in 4..5 }
    private val k = glyph { x, y -> x in 1..2 || (x - 2 == kotlin.math.abs(y - 7) && x in 3..8) }

    @Test fun aLetterLabelledAsADigitIsRejectedButARealDigitVariantIsKept() {
        val font = GameDigitFont
        val reference = listOf(GameDigitFont.Sample(0, zero), GameDigitFont.Sample(1, one))
        assertFalse(font.plausible(GameDigitFont.Sample(0, k), reference), "k stored as 0 must be rejected")
        assertTrue(font.plausible(GameDigitFont.Sample(0, zero), reference))
        assertTrue(font.plausible(GameDigitFont.Sample(1, one), reference))
    }
}
