package eu.komars.forgescanner.model

import org.junit.Assert.*
import org.junit.Test

class InventoryTrackerTest {
    private fun row(value: Int, occupied: Int = 5): InventoryRow = InventoryRow((0 until 5).map {
        CellSignature(it < occupied, java.lang.Long.rotateLeft(-1L, value), value)
    })
    private fun frame(vararg rows: InventoryRow) = InventoryFrame(rows.toList(), 5)

    @Test fun alignsConsecutiveRowsWithoutGloballyDeduplicatingPets() {
        val tracker = InventoryTracker()
        tracker.append(frame(row(1), row(2), row(3)))
        val result = tracker.append(frame(row(3), row(4), row(5, 3)))
        assertEquals(23L, result.totalObservedCount)
        assertEquals(8, result.addedCount)
        assertEquals(1, result.overlapRows)
        assertTrue(result.needsReview)
    }

    @Test fun identicalRowsDoNotInventScrollDistance() {
        val tracker = InventoryTracker()
        val same = frame(row(1), row(1), row(1))
        tracker.append(same)
        val result = tracker.append(same)
        assertEquals(15L, result.totalObservedCount)
        assertEquals(0, result.addedCount)
        assertTrue(result.message.contains("ręcznie"))
        tracker.addManually(5, same)
        assertEquals(20L, tracker.count)
    }

    @Test fun noOverlapDoesNotCountWholeFrameAgain() {
        val tracker = InventoryTracker()
        tracker.append(frame(row(1), row(2)))
        val result = tracker.append(frame(row(3), row(4)))
        assertEquals(10L, result.totalObservedCount)
        assertNull(result.overlapRows)
        tracker.reset()
        assertEquals(0L, tracker.count)
    }

    @Test fun invalidManualFrameLeavesStateUnchanged() {
        val tracker = InventoryTracker()
        tracker.append(frame(row(1)))
        try {
            tracker.addManually(5, InventoryFrame(listOf(InventoryRow(emptyList())), 5))
            fail("Expected invalid frame to be rejected")
        } catch (_: IllegalArgumentException) { }
        assertEquals(5L, tracker.count)
    }
}
