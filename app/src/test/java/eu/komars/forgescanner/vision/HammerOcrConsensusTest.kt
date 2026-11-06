package eu.komars.forgescanner.vision

import org.junit.Assert.*
import org.junit.Test

class HammerOcrConsensusTest {
    private fun frame(vararg values: Long) = OcrAnalysis(values.map { value ->
        OcrCandidate(null, value, value.toString(), ScreenBounds(10, 10, 100, 50), .8f, false, "test")
    }, emptyList())

    @Test fun threeConsistentSourceFramesAllowAutomaticAcceptance() {
        val result = HammerOcrConsensus.combine(List(3) { frame(3888) })
        assertFalse(result.requiresConfirmation)
        assertEquals(3888L, result.candidates.single().value)
    }

    @Test fun eightVersusThreeNeverSelectsTheLargerNumberAutomatically() {
        val result = HammerOcrConsensus.combine(listOf(frame(3888), frame(8888), frame(3888)))
        assertTrue(result.requiresConfirmation)
        assertEquals(listOf(3888L, 8888L), result.candidates.map { it.value })
    }

    @Test fun repeatedContrastPassesAreOnlyOneVotePerFrame() {
        val result = HammerOcrConsensus.combine(listOf(frame(8888, 8888, 8888), frame(3888), frame(3888)))
        assertTrue(result.requiresConfirmation)
        assertEquals(3888L, result.candidates.first().value)
    }

    @Test fun singleOrMissingFramesCannotCertifyAReading() {
        assertTrue(HammerOcrConsensus.combine(listOf(frame(3888))).requiresConfirmation)
        assertTrue(HammerOcrConsensus.combine(listOf(frame(3888), frame(3888), OcrAnalysis(emptyList(), emptyList(), "capture stopped"))).requiresConfirmation)
    }

    @Test fun consistentAmbiguityStillRequiresThePlayerToChoose() {
        assertTrue(HammerOcrConsensus.combine(List(3) { frame(3888, 8888) }).requiresConfirmation)
    }

    @Test fun noReadingsReturnAnErrorInsteadOfZero() {
        val result = HammerOcrConsensus.combine(List(3) { frame() })
        assertTrue(result.requiresConfirmation)
        assertTrue(result.candidates.isEmpty())
        assertNotNull(result.error)
    }
}
