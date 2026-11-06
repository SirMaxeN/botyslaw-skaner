package eu.komars.forgescanner.automation

import org.junit.Assert.*
import org.junit.Test

class CalibrationFlowTest {
    @Test fun fullSetupVisitsEveryReadAndNavigationStepInOrder() {
        val flow = CalibrationFlow()
        assertTrue(flow.isComplete)
        assertEquals(AutoScanStep.HAMMERS, flow.beginAll())
        val visited = mutableListOf<AutoScanStep>()
        while (flow.current != null) {
            visited += flow.current!!
            assertFalse(flow.isRepair)
            assertEquals(flow.current!!.ordinal, flow.index)
            flow.advance()
        }
        assertEquals(AutoScanStep.entries.toList(), visited)
        assertTrue(flow.isComplete)
        assertNull(flow.index)
        assertNull(flow.advance())
    }

    @Test fun repairingAFailedMiddleFieldEndsImmediatelyAfterItsSave() {
        val flow = CalibrationFlow()
        flow.beginRepair(AutoScanStep.MOUNT_COST)
        assertTrue(flow.isRepair)
        assertEquals(AutoScanStep.MOUNT_COST, flow.current)
        assertNull(flow.advance(AutoScanStep.MOUNT_COST))
        assertNull(flow.current)
        assertTrue(flow.isComplete)
        assertTrue(flow.isRepair)
        assertNull(flow.advance(AutoScanStep.MOUNT_COST))
    }

    @Test fun repairingLastNavigationAndStartingFullSetupAgainAreSeparateModes() {
        val flow = CalibrationFlow()
        flow.beginRepair(AutoScanStep.CLOSE_RESEARCH)
        assertNull(flow.advance())
        assertTrue(flow.isComplete)
        flow.beginAll()
        assertFalse(flow.isRepair)
        assertEquals(AutoScanStep.HAMMERS, flow.current)
        assertEquals(AutoScanStep.OPEN_MOUNTS, flow.advance())
    }

    @Test fun staleSaveCallbackCannotSkipAnotherField() {
        val flow = CalibrationFlow()
        flow.beginAll()
        flow.advance(AutoScanStep.HAMMERS)
        assertEquals(AutoScanStep.OPEN_MOUNTS, flow.advance(AutoScanStep.HAMMERS))
        assertEquals(AutoScanStep.OPEN_MOUNTS, flow.current)
        flow.beginRepair(AutoScanStep.PET_LIST)
        assertEquals(AutoScanStep.PET_LIST, flow.advance(AutoScanStep.OPEN_MOUNTS))
        assertFalse(flow.isComplete)
    }

    @Test fun cancellationClearsOnlyInFlightNavigationAndAllowsAnotherRepair() {
        val flow = CalibrationFlow()
        flow.beginAll(); flow.advance(); flow.cancel()
        assertNull(flow.current)
        assertNull(flow.index)
        assertFalse(flow.isRepair)
        assertNull(flow.advance(AutoScanStep.OPEN_MOUNTS))
        flow.beginRepair(AutoScanStep.SKILL_TICKETS)
        assertEquals(AutoScanStep.SKILL_TICKETS, flow.current)
        flow.clear()
        assertTrue(flow.isComplete)
        assertFalse(flow.isRepair)
    }
}
