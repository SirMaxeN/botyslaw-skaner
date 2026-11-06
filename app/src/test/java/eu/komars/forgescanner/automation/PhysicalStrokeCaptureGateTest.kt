package eu.komars.forgescanner.automation

import org.junit.Assert.assertEquals
import org.junit.Test

class PhysicalStrokeCaptureGateTest {
    @Test fun initialStrokeCanBootstrapButLaterStrokesRequireARealNewCallback() {
        val gate = PhysicalStrokeCaptureGate()
        assertEquals(PhysicalStrokeCaptureGate.Status.READY, gate.check(0, 0, allowInitialBootstrap = true))
        assertEquals(PhysicalStrokeCaptureGate.Status.WAIT, gate.check(100, 0))
        gate.observe(1_000_000_000L, 110)
        assertEquals(PhysicalStrokeCaptureGate.Status.READY, gate.check(110, 0))
        assertEquals(PhysicalStrokeCaptureGate.Status.WAIT, gate.check(111, 1_000_000_000L))
    }

    @Test fun recentDistinctNativeFramesContinueWithoutDelay() {
        val gate = PhysicalStrokeCaptureGate()
        var source = 1_000_000_000L
        repeat(50) { index ->
            val start = index * 350L
            gate.observe(source, start)
            val baseline = source
            source += 333_333_340L
            gate.observe(source, start + 333)
            assertEquals(PhysicalStrokeCaptureGate.Status.READY, gate.check(start + 350, baseline))
        }
    }

    @Test fun anOldButNewerThanPrepassFrameCannotAuthorizeTheNextStroke() {
        val gate = PhysicalStrokeCaptureGate()
        gate.observe(1_000_000_000L, 0)
        val baseline = gate.latestTimestampNanos
        gate.observe(1_016_000_000L, 16)
        assertEquals(PhysicalStrokeCaptureGate.Status.WAIT, gate.check(350, baseline))
        assertEquals(PhysicalStrokeCaptureGate.Status.WAIT, gate.check(1099, baseline))
        assertEquals(PhysicalStrokeCaptureGate.Status.STALLED, gate.check(1100, baseline))
    }

    @Test fun cachedCallbacksCannotRefreshArrivalOrExtendTheWaitDeadline() {
        val gate = PhysicalStrokeCaptureGate()
        gate.observe(1_000_000_000L, 0)
        assertEquals(PhysicalStrokeCaptureGate.Status.WAIT, gate.check(200, 1_000_000_000L))
        for (now in 250L..900L step 50) {
            gate.observe(1_000_000_000L, now)
            assertEquals(PhysicalStrokeCaptureGate.Status.WAIT, gate.check(now, 1_000_000_000L))
        }
        assertEquals(PhysicalStrokeCaptureGate.Status.STALLED, gate.check(950, 1_000_000_000L))
    }

    @Test fun genuineFreshCallbackResumesAndLaterWaitGetsItsOwnDeadline() {
        val gate = PhysicalStrokeCaptureGate()
        assertEquals(PhysicalStrokeCaptureGate.Status.WAIT, gate.check(0, 0))
        gate.observe(100, 700)
        assertEquals(PhysicalStrokeCaptureGate.Status.READY, gate.check(700, 0))
        assertEquals(PhysicalStrokeCaptureGate.Status.WAIT, gate.check(701, 100))
        assertEquals(PhysicalStrokeCaptureGate.Status.WAIT, gate.check(1450, 100))
        assertEquals(PhysicalStrokeCaptureGate.Status.STALLED, gate.check(1451, 100))
    }

    @Test fun endCheckPauseResetsOnlyWaitingAndCannotCreateFreshEvidence() {
        val gate = PhysicalStrokeCaptureGate()
        gate.observe(100, 0)
        assertEquals(PhysicalStrokeCaptureGate.Status.WAIT, gate.check(200, 100))
        gate.resetWait()
        assertEquals(PhysicalStrokeCaptureGate.Status.WAIT, gate.check(5000, 100))
        assertEquals(PhysicalStrokeCaptureGate.Status.STALLED, gate.check(5750, 100))
    }
}
