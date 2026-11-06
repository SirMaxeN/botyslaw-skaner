package eu.komars.forgescanner.battle

import org.junit.Assert.*
import org.junit.Test

class CaptureAnalysisGateTest {
    @Test fun analysisFinishingInsideOpenPopupCannotAdvanceNavigation() {
        val g=CaptureAnalysisGate();g.beginCapture();g.submitAnalysis();g.finishAnalysis()
        assertFalse(g.ready);g.finishCapture();assertTrue(g.ready)
    }
    @Test fun closingAllPopupsStillWaitsForEveryQueuedRead() {
        val g=CaptureAnalysisGate();g.beginCapture();repeat(3){g.submitAnalysis()};g.finishCapture()
        repeat(2){g.finishAnalysis();assertFalse(g.ready)}
        g.finishAnalysis();assertTrue(g.ready)
    }
    @Test fun targetedRetryBlocksProgressUntilBothImageAndAnalysisAreFinished() {
        val g=CaptureAnalysisGate();g.beginCapture();g.submitAnalysis();g.finishCapture();g.finishAnalysis()
        assertTrue(g.ready);g.beginCapture();g.submitAnalysis();assertFalse(g.ready)
        g.finishCapture();assertFalse(g.ready);g.finishAnalysis();assertTrue(g.ready)
    }
    @Test fun stopPreventsLateRecognitionFromReleasingAnotherStage() {
        val g=CaptureAnalysisGate();g.beginCapture();g.submitAnalysis();g.finishCapture();g.cancel();g.finishAnalysis()
        assertFalse(g.ready)
    }
}
