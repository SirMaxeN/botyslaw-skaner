package eu.komars.forgescanner.model

import org.junit.Assert.*
import org.junit.Test

class InventoryEndDetectorTest {
    private fun frame(rows: Int, partialLast: Boolean = false): GrayScrollFrame {
        val data = ByteArray(80 * 120) { 255.toByte() }
        for (row in 0 until rows) for (y in row * 40 + 8 until row * 40 + 30) {
            val columns = if (rows == 1 || (partialLast && row == rows - 1)) 1 else 5
            for (col in 0 until columns) for (x in col * 16 + 4 until col * 16 + 12) {
                data[y * 80 + x] = if ((x + y) % 2 == 0) 30 else 170.toByte()
            }
        }
        return GrayScrollFrame(80, 120, data)
    }
    @Test fun oneMountWithAnEmptyFooterFinishesWithoutScrolling() {
        val detector = InventoryEndDetector(); val frame = frame(1)
        assertEquals(InventoryEndDetector.Status.CHECKING, detector.observe(frame, 40, 0))
        for (time in 200L..800L step 200) assertEquals(InventoryEndDetector.Status.CHECKING, detector.observe(frame, 40, time))
        assertEquals(InventoryEndDetector.Status.VISIBLE_END, detector.observe(frame, 40, 1000))
        assertEquals(1L, ScrollSequenceAnalyzer().analyze(listOf(frame), 5, 40).count)
    }
    @Test fun pausingOnAFullViewportDoesNotMeanTheEnd() {
        val detector = InventoryEndDetector()
        for (time in 0L..2000L step 200) assertEquals(InventoryEndDetector.Status.NOT_VISIBLE, detector.observe(frame(3), 40, time))
    }
    @Test fun emptyOrBadlySelectedWhiteRegionIsNotAcceptedAsACompleteInventory() {
        val detector = InventoryEndDetector()
        for (time in 0L..2000L step 200) assertEquals(InventoryEndDetector.Status.NOT_VISIBLE, detector.observe(frame(0), 40, time))
    }
    @Test fun partialFinalRowCanConfirmTheEndWithoutALargeEmptyFooter() {
        val detector = InventoryEndDetector(); val last = frame(3, partialLast = true)
        for (time in 0L..800L step 200) assertEquals(InventoryEndDetector.Status.CHECKING, detector.observe(last, 40, time))
        assertEquals(InventoryEndDetector.Status.VISIBLE_END, detector.observe(last, 40, 1000))
        assertEquals(11L, ScrollSequenceAnalyzer().analyze(listOf(last), 5, 40).count)
    }
    @Test fun aFewPixelsOfTheNextRowDoNotConfirmAnEnd() {
        val pixels = ByteArray(80 * 120) { 255.toByte() }
        for (y in 117 until 120) for (x in 4 until 12) pixels[y * 80 + x] = 30
        val clipped = GrayScrollFrame(80, 120, pixels)
        val detector = InventoryEndDetector()
        for (time in 0L..1200L step 200) assertEquals(InventoryEndDetector.Status.NOT_VISIBLE, detector.observe(clipped, 40, time))
    }
    @Test fun returningToScrollingClearsTheCandidateEnd() {
        val detector = InventoryEndDetector()
        detector.observe(frame(1), 40, 0); detector.observe(frame(1), 40, 500)
        assertEquals(InventoryEndDetector.Status.NOT_VISIBLE, detector.observe(frame(3), 40, 800))
        assertEquals(InventoryEndDetector.Status.CHECKING, detector.observe(frame(1), 40, 1000))
    }
}
