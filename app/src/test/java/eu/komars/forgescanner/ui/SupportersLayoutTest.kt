package eu.komars.forgescanner.ui

import org.junit.Assert.assertEquals
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
    }
}
