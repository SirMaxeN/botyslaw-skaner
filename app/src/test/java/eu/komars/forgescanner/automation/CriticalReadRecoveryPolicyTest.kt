package eu.komars.forgescanner.automation

import eu.komars.forgescanner.model.ScanTarget
import org.junit.Assert.*
import org.junit.Test

class CriticalReadRecoveryPolicyTest {
    @Test fun repeatedCompleteEmptyReadsMayInspectTheScreenOnce() {
        assertTrue(CriticalReadRecoveryPolicy.eligible(2, 0, 3, false, true, true))
        assertTrue(CriticalReadRecoveryPolicy.eligible(4, 0, 3, false, true, true))
        assertFalse(CriticalReadRecoveryPolicy.eligible(4, 0, 3, true, true, true))
    }

    @Test fun incompleteOrFirstBurstsKeepTheSameCropInsteadOfNavigating() {
        assertFalse(CriticalReadRecoveryPolicy.eligible(0, 0, 3, false, true, true))
        assertFalse(CriticalReadRecoveryPolicy.eligible(1, 0, 3, false, true, true))
        for (frames in 0..2)
            assertFalse(CriticalReadRecoveryPolicy.eligible(2, 0, frames, false, true, true))
    }

    @Test fun aReadableZeroOrAnOcrConflictNeedsNoScreenRecovery() {
        // A valid numeric zero is still one readable candidate, not an empty burst.
        assertFalse(CriticalReadRecoveryPolicy.eligible(2, 1, 3, false, true, true))
        assertFalse(CriticalReadRecoveryPolicy.eligible(2, 2, 3, false, true, true))
    }

    @Test fun losingCaptureOrGameVisibilityNeverAuthorizesRecoveryGestures() {
        assertFalse(CriticalReadRecoveryPolicy.eligible(2, 0, 3, false, false, true))
        assertFalse(CriticalReadRecoveryPolicy.eligible(2, 0, 3, false, true, false))
        assertFalse(CriticalReadRecoveryPolicy.eligible(2, 0, 3, false, false, false))
    }

    @Test fun eachCounterReturnsToItsOwnScreenWithoutRestartingInventories() {
        assertEquals(AutoScanStep.CLOSE_RESEARCH, CriticalReadRecoveryPolicy.expectedNavigation(ScanTarget.HAMMERS))
        assertEquals(AutoScanStep.OPEN_MOUNTS, CriticalReadRecoveryPolicy.expectedNavigation(ScanTarget.MOUNT_KEYS))
        assertEquals(AutoScanStep.OPEN_MOUNTS, CriticalReadRecoveryPolicy.expectedNavigation(ScanTarget.MOUNT_COST))
        assertEquals(AutoScanStep.OPEN_SKILLS, CriticalReadRecoveryPolicy.expectedNavigation(ScanTarget.TICKETS))
        assertEquals(AutoScanStep.OPEN_SKILLS, CriticalReadRecoveryPolicy.expectedNavigation(ScanTarget.SKILL_COST))
        assertNull(CriticalReadRecoveryPolicy.expectedNavigation(ScanTarget.MOUNTS))
        assertNull(CriticalReadRecoveryPolicy.expectedNavigation(ScanTarget.PETS))
    }
}
