package eu.komars.forgescanner.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SupportersLayoutTest {
    @Test fun columnsGrowWithTheListUpToTheScreenWidth() {
        assertEquals(1, SupportersLayout.columns(5, 411))
        assertEquals(1, SupportersLayout.columns(7, 411))
        assertEquals(2, SupportersLayout.columns(8, 411))
        assertEquals(3, SupportersLayout.columns(60, 411))  // a phone fits three name columns
        assertEquals(4, SupportersLayout.columns(500, 800)) // tablet: never more than four
        assertEquals(1, SupportersLayout.columns(50, 120))  // very narrow: still one column
    }

    @Test fun scrollTimeIsShortAndCapped() {
        assertEquals(1200L, SupportersLayout.scrollMillis(40f))
        assertEquals(4000L, SupportersLayout.scrollMillis(440f))
        assertEquals(9000L, SupportersLayout.scrollMillis(20000f))
        assertEquals(12000L, SupportersLayout.scrollMillis(440f, full = true)) // first start / after an update: 3x
    }
}

class SupportersIntroKeyTest {
    private fun key(code: Int, vararg names: String) = eu.komars.forgescanner.sync.SupportersClient.introKey(code, names.toList())

    @Test fun fullIntroAgainAfterAnUpdateOrAChangeOfPeople() {
        assertEquals(key(65, "Ala", "Bob"), key(65, "Bob", "Ala"))            // order only: still seen
        assertNotEquals(key(65, "Ala", "Bob"), key(66, "Ala", "Bob"))         // app update
        assertNotEquals(key(65, "Ala", "Bob"), key(65, "Ala", "Bob", "Cez"))  // new supporter
        assertNotEquals(key(65, "Ala", "Bob"), key(65, "Ala"))                // removed
    }
}
