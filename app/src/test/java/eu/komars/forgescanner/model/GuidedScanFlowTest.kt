package eu.komars.forgescanner.model

import org.junit.Assert.*
import org.junit.Test

class GuidedScanFlowTest {
    private val session = ScanSession { 123L }
    private val flow = GuidedScanFlow { session }
    private fun pair(target: ScanTarget, amount: Long, cost: Long) = flow.acceptPair(target,
        mapOf(target to GuidedScanFlow.CounterReading(amount), target.counters[1] to GuidedScanFlow.CounterReading(cost)))

    @Test fun startsWithHammersAndRequiresConfirmationBeforeAdvancing() {
        assertNull(flow.currentTarget)
        assertEquals(ScanTarget.HAMMERS, flow.start())
        assertThrows(IllegalArgumentException::class.java) { flow.next() }
        flow.acceptNumber(ScanTarget.HAMMERS, 11026)
        assertEquals(ScanTarget.MOUNT_KEYS, flow.next())
    }
    @Test fun pairedCostsAreValidatedBeforeEitherValueChanges() {
        advanceToMountCurrency()
        assertThrows(IllegalArgumentException::class.java) { pair(ScanTarget.MOUNT_KEYS, 2840, 0) }
        assertNull(session.snapshot().value(ResourceField.MOUNT_KEYS))
        assertNull(session.snapshot().value(ResourceField.MOUNT_COST))
        assertThrows(IllegalArgumentException::class.java) { flow.acceptNumber(ScanTarget.MOUNT_KEYS, 2840) }
        pair(ScanTarget.MOUNT_KEYS, 2840, 50)
        assertEquals(2840L, session.snapshot().value(ResourceField.MOUNT_KEYS))
        assertEquals(50L, session.snapshot().value(ResourceField.MOUNT_COST))
        assertEquals(ScanTarget.MOUNTS, flow.next())
    }
    @Test fun inventoryIsUnpublishedUntilConfirmedAndBasesAreSubtractedOnce() {
        advanceToMounts()
        assertNull(session.snapshot().value(ResourceField.MOUNTS))
        flow.completeInventory(ScanTarget.MOUNTS, 21, true)
        flow.completeInventory(ScanTarget.MOUNTS, 21, true)
        assertEquals(20L, session.snapshot().value(ResourceField.MOUNTS))
        assertEquals(21L, flow.inventoryRawTotals()[ScanTarget.MOUNTS])
    }
    @Test fun petsAndEggsAreOneFinalTarget() {
        advanceToPets()
        flow.completeInventory(ScanTarget.PETS, 336, true)
        assertEquals(333L, session.snapshot().value(ResourceField.PETS))
        assertNull(flow.next())
    }
    @Test fun estimateRemainsMarkedAfterConfirmationAndBaseSubtraction() {
        advanceToPets()
        flow.completeInventory(ScanTarget.PETS, 336, true, approximate = true)
        assertEquals(333L, session.snapshot().value(ResourceField.PETS))
        assertTrue(session.snapshot().values.getValue(ResourceField.PETS).approximate)
    }
    @Test fun emptyListCanBeConfirmedWithoutSubtractingAbsentBases() {
        advanceToPets(); flow.completeInventory(ScanTarget.PETS, 0, false)
        assertEquals(0L, session.snapshot().value(ResourceField.PETS))
    }
    @Test fun invalidInventoryDoesNotOverwriteConfirmedValues() {
        advanceToPets(); flow.completeInventory(ScanTarget.PETS, 336, true)
        assertThrows(IllegalArgumentException::class.java) { flow.completeInventory(ScanTarget.PETS, 2, true) }
        assertEquals(333L, session.snapshot().value(ResourceField.PETS))
    }
    @Test fun fiveStepsProduceAllSevenValuesAndCanReturnToPetsForCorrection() {
        advanceToPets(); flow.completeInventory(ScanTarget.PETS, 3, true); flow.next()
        assertTrue(flow.isComplete)
        assertTrue(session.snapshot().readyToExport)
        assertEquals(7, session.snapshot().values.size)
        assertEquals(ScanStep.REVIEW, session.step)
        assertEquals(ScanTarget.PETS, flow.back())
    }
    @Test fun resetClearsWorkflowOnlyAndResumeDoesNotAdvance() {
        flow.start(); flow.acceptNumber(ScanTarget.HAMMERS, 11026); flow.next()
        assertEquals(ScanTarget.MOUNT_KEYS, flow.start())
        flow.reset()
        assertFalse(flow.isStarted)
        assertEquals(11026L, session.snapshot().value(ResourceField.HAMMERS))
        assertEquals(ScanTarget.HAMMERS, flow.start())
    }
    private fun advanceToMountCurrency() {
        flow.start(); flow.acceptNumber(ScanTarget.HAMMERS, 11026); flow.next()
    }
    private fun advanceToMounts() {
        advanceToMountCurrency(); pair(ScanTarget.MOUNT_KEYS, 2840, 50); flow.next()
    }
    private fun advanceToPets() {
        advanceToMounts(); flow.completeInventory(ScanTarget.MOUNTS, 1, true); flow.next()
        pair(ScanTarget.TICKETS, 9880, 188); flow.next()
    }
}
