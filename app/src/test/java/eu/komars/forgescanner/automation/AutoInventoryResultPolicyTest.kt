package eu.komars.forgescanner.automation

import eu.komars.forgescanner.model.ScanTarget
import eu.komars.forgescanner.model.ScrollAnalysis
import org.junit.Assert.*
import org.junit.Test

class AutoInventoryResultPolicyTest {
    @Test fun theSameEstimatedCountAsManualModeIsKeptAndFlaggedForReview() {
        val recording = ScrollAnalysis(372, 1800, 2, "≈ ESTYMACJA", approximate = true,
            inferredRows = 1, lowConfidence = true)
        val reading = AutoInventoryResultPolicy.reading(ScanTarget.PETS, recording)
        assertEquals(369L, reading.value)
        assertTrue(reading.approximate)
        assertTrue(reading.source!!.contains("372"))
    }

    @Test fun uncertaintyCannotBecomeAnUnmarkedVerifiedNumber() {
        val recording = ScrollAnalysis(31, 300, 1, "Prześledzono listę")
        val reading = AutoInventoryResultPolicy.reading(ScanTarget.MOUNTS, recording)
        assertEquals(30L, reading.value)
        assertTrue(reading.approximate)
    }

    @Test fun aSingleBaseMountProducesZeroAvailableMounts() {
        val reading = AutoInventoryResultPolicy.reading(ScanTarget.MOUNTS,
            ScrollAnalysis(1, 20, 0, "Widoczny koniec listy"))
        assertEquals(0L, reading.value)
        assertFalse(reading.approximate)
    }

    @Test fun aCountSmallerThanTheBaseInventoryStillFails() {
        try {
            AutoInventoryResultPolicy.reading(ScanTarget.PETS, ScrollAnalysis(2, 20, 0, ""))
            fail("Invalid base count must not be exported")
        } catch (_: IllegalStateException) { }
    }
}