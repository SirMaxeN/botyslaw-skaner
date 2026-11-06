package eu.komars.forgescanner.vision

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CounterDigitMaskTest {
    @Test fun preservesWhiteAndRedUnaffordableCounters() {
        listOf(Triple(255, 255, 255), Triple(220, 220, 220),
            Triple(255, 60, 70), Triple(210, 65, 85), Triple(155, 60, 65))
            .forEach { (r, g, b) -> assertTrue(CounterDigitMask.isInk(r, g, b)) }
    }

    @Test fun excludesDarkOutlinesAndCommonButtonBackgrounds() {
        listOf(Triple(0, 0, 0), Triple(80, 80, 80), Triple(50, 160, 210),
            Triple(40, 190, 100), Triple(180, 150, 30), Triple(130, 90, 100))
            .forEach { (r, g, b) -> assertFalse(CounterDigitMask.isInk(r, g, b)) }
    }
}
