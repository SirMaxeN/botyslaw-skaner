package eu.komars.forgescanner.vision

import eu.komars.forgescanner.model.ScanTarget
import org.junit.Assert.*
import org.junit.Test

class CounterStripLayoutTest {
    private fun layout() = CounterStripLayout(linkedMapOf(
        ScanTarget.MOUNT_KEYS to ScreenBounds(410, 623, 665, 705),
        ScanTarget.MOUNT_COST to ScreenBounds(383, 1611, 697, 1767)))

    @Test fun eachSlotMapsBackToItsOwnOriginalScreenPosition() {
        val layout = layout()
        for (slot in layout.slots) {
            val line = OcrLine("188", ScreenBounds(slot.packed.left + 70, slot.packed.top + 15,
                slot.packed.left + 140, slot.packed.top + 45))
            val mapped = layout.mapLine(line)!!
            assertEquals(slot.target, mapped.first)
            assertEquals(ScreenBounds(slot.source.left + 70, slot.source.top + 15,
                slot.source.left + 140, slot.source.top + 45), mapped.second.bounds)
            assertEquals("188", mapped.second.text)
        }
    }

    @Test fun separatorAndCrossSlotLinesAreRejectedInsteadOfMixingFields() {
        val layout = layout(); val first = layout.slots[0].packed; val second = layout.slots[1].packed
        assertNull(layout.mapLine(OcrLine("188", ScreenBounds(first.left, first.bottom + 2, first.left + 60, second.top - 2))))
        assertNull(layout.mapLine(OcrLine("7600 50", ScreenBounds(first.left, first.top + 10, first.left + 100, second.top + 40))))
        assertNull(layout.mapLine(OcrLine("9", ScreenBounds(0, first.top, first.left - 1, first.top + 20))))
    }

    @Test fun identicalNumbersStillBelongToTwoIndependentFields() {
        val layout = layout()
        val mapped = layout.slots.map { slot -> layout.mapLine(OcrLine("50",
            ScreenBounds(slot.packed.left + 70, slot.packed.top + 15, slot.packed.left + 120, slot.packed.top + 45)))!! }
        assertEquals(listOf(ScanTarget.MOUNT_KEYS, ScanTarget.MOUNT_COST), mapped.map { it.first })
        assertEquals(listOf("50", "50"), mapped.map { it.second.text })
    }

    @Test fun packingPreservesFullCalibratedWidthAndHeightWithoutScaling() {
        val layout = layout()
        assertTrue(layout.slots[1].packed.top > layout.slots[0].packed.bottom)
        for (slot in layout.slots) {
            assertEquals(slot.source.width, slot.packed.width)
            assertEquals(slot.source.height, slot.packed.height)
            assertTrue(slot.packed.right < layout.width)
            assertTrue(slot.packed.bottom < layout.height)
        }
    }
}
