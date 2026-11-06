package eu.komars.forgescanner.automation

import eu.komars.forgescanner.model.GameScreenKind.*
import org.junit.Assert.*
import org.junit.Test

class NavigationReadinessTest {
    @Test fun aSingleExpectedFrameCannotReleaseTheNextTap() {
        val gate = NavigationReadinessGate(AutoScanStep.OPEN_RESEARCH, 0)
        assertEquals(NavigationReadinessGate.Status.WAIT, gate.observe(SKILLS, 240))
        assertEquals(NavigationReadinessGate.Status.WAIT, gate.observe(SKILLS, 300))
        assertEquals(NavigationReadinessGate.Status.READY, gate.observe(SKILLS, 360))
    }
    @Test fun changingOrUnknownScreensResetTheStableEvidence() {
        val gate = NavigationReadinessGate(AutoScanStep.OPEN_PETS, 0)
        gate.observe(PETS, 160); gate.observe(UNKNOWN, 280)
        assertEquals(NavigationReadinessGate.Status.WAIT, gate.observe(PETS, 400))
        assertEquals(NavigationReadinessGate.Status.READY, gate.observe(PETS, 520))
    }
    @Test fun aClosingWindowMustSettleBeforeAnotherHomeButtonIsTapped() {
        val gate = NavigationReadinessGate(AutoScanStep.CLOSE_MOUNTS, 0)
        gate.observe(HOME, 160)
        assertEquals(NavigationReadinessGate.Status.WAIT, gate.observe(HOME, 400))
        assertEquals(NavigationReadinessGate.Status.READY, gate.observe(HOME, 520))
    }
    @Test fun expiredOrStaleEvidenceNeverAuthorizesRecovery() {
        val gate = NavigationReadinessGate(AutoScanStep.OPEN_RESEARCH, 0)
        gate.observe(HOME, 100); gate.observe(HOME, 240)
        assertEquals(UNKNOWN, gate.stableScreen(1800))
        assertEquals(NavigationReadinessGate.Status.TIMEOUT, gate.observe(SKILLS, NavigationReadinessGate.PHASE_TIMEOUT_MILLIS + NavigationReadinessGate.LATE_TARGET_GRACE_MILLIS))
    }
    @Test fun recoveryContainsOnlyKnownNavigationAndNeverRetriesItself() {
        for (step in AutoScanStep.entries.filter { it.isNavigation }) for (screen in eu.komars.forgescanner.model.GameScreenKind.entries) {
            val plan = NavigationRecoveryPlan.steps(step, screen, false)
            assertTrue(plan == null || plan.size <= 3 && plan.all { it.isNavigation })
            assertNull(NavigationRecoveryPlan.steps(step, screen, true))
        }
        assertNull(NavigationRecoveryPlan.steps(AutoScanStep.OPEN_PETS, UNKNOWN, false))
        assertEquals(listOf(AutoScanStep.CLOSE_MOUNTS, AutoScanStep.OPEN_RESEARCH, AutoScanStep.OPEN_PETS),
            NavigationRecoveryPlan.steps(AutoScanStep.OPEN_PETS, MOUNTS, false))
    }
}
