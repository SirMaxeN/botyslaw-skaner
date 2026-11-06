package eu.komars.forgescanner.automation

import eu.komars.forgescanner.model.GameScreenKind.*
import org.junit.Assert.*
import org.junit.Test

class NavigationReadinessGateTest {
    @Test fun oneObservationAndDuplicateTimeCannotProveReadiness() {
        val gate = NavigationReadinessGate(AutoScanStep.OPEN_RESEARCH, 0)
        assertEquals(NavigationReadinessGate.Status.WAIT, gate.observe(SKILLS, 240))
        assertEquals(NavigationReadinessGate.Status.WAIT, gate.observe(SKILLS, 240))
        assertEquals(NavigationReadinessGate.Status.WAIT, gate.observe(SKILLS, 359))
        assertEquals(NavigationReadinessGate.Status.READY, gate.observe(SKILLS, 360))
    }

    @Test fun closeNeedsItsLongerSettleEvenWhenHomeAppearsImmediately() {
        val gate = NavigationReadinessGate(AutoScanStep.CLOSE_MOUNTS, 0)
        assertEquals(NavigationReadinessGate.Status.WAIT, gate.observe(HOME, 160))
        assertEquals(NavigationReadinessGate.Status.WAIT, gate.observe(HOME, 280))
        assertEquals(NavigationReadinessGate.Status.READY, gate.observe(HOME, 450))
    }

    @Test fun switchingKindsOrUnknownResetsTheStableCandidate() {
        val gate = NavigationReadinessGate(AutoScanStep.OPEN_RESEARCH, 0)
        gate.observe(SKILLS, 240)
        gate.observe(UNKNOWN, 360)
        gate.observe(PETS, 480)
        assertEquals(UNKNOWN, gate.stableScreen(480))
        assertEquals(NavigationReadinessGate.Status.READY, gate.observe(PETS, 600))
    }

    @Test fun stableSourceRemainsAvailableForABoundedRecoveryDecision() {
        val gate = NavigationReadinessGate(AutoScanStep.OPEN_RESEARCH, 0)
        gate.observe(HOME, 240)
        assertEquals(NavigationReadinessGate.Status.WAIT, gate.observe(HOME, 360))
        assertEquals(HOME, gate.stableScreen(360))
        assertEquals(UNKNOWN, gate.stableScreen(961))
    }

    @Test fun targetArrivingAfterThreeSecondsStillHasSettlingGrace() {
        val gate = NavigationReadinessGate(AutoScanStep.CLOSE_MOUNTS, 0)
        assertEquals(NavigationReadinessGate.Status.WAIT, gate.observe(HOME, 3200))
        assertEquals(NavigationReadinessGate.Status.READY, gate.observe(HOME, 3320))
        assertEquals(NavigationReadinessGate.Status.TIMEOUT, gate.observe(HOME, 3750))
    }

    @Test fun backwardClockCannotAuthorizeReadiness() {
        val gate = NavigationReadinessGate(AutoScanStep.OPEN_MOUNTS, 10)
        gate.observe(MOUNTS, 300)
        assertEquals(NavigationReadinessGate.Status.TIMEOUT, gate.observe(MOUNTS, 299))
        assertEquals(UNKNOWN, gate.stableScreen(299))
    }
}
