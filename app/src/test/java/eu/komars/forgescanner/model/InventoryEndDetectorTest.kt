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

    @Test fun partialTailWithAThreePixelFooterSurvivesModalSideBorders() {
        val data = frame(3, partialLast = true).pixels.copyOf()
        // Move the last visible row down to leave only three white pixels below it.
        for (y in 80 until 120) for (x in 0 until 80) data[y * 80 + x] = 255.toByte()
        for (y in 95 until 117) for (col in 0..1) for (x in col * 16 + 4 until col * 16 + 12) {
            data[y * 80 + x] = if ((x + y) % 2 == 0) 30 else 170.toByte()
        }
        for (y in 0 until 120) for (x in listOf(0, 1, 2, 3, 76, 77, 78, 79)) data[y * 80 + x] = 0
        val tail = GrayScrollFrame(80, 120, data)
        val detector = InventoryEndDetector()
        for (time in 0L..800L step 200) assertEquals(InventoryEndDetector.Status.CHECKING, detector.observe(tail, 40, time))
        assertEquals(InventoryEndDetector.Status.VISIBLE_END, detector.observe(tail, 40, 1000))
    }

    @Test fun ordinaryInterRowGapsDoNotPauseControlledScrolling() {
        val detector = InventoryEndDetector()
        // Phase the same full rows so the white gap enters the bottom of the ROI.
        for (offset in 0..39) {
            val data = ByteArray(80 * 120) { index ->
                val x = index % 80; val localY = (index / 80 + offset) % 40
                if (localY in 8..29 && x % 16 in 4..11) 30 else 255.toByte()
            }
            assertEquals("An ordinary gap is not a list end at phase $offset", InventoryEndDetector.Status.NOT_VISIBLE,
                detector.observe(GrayScrollFrame(80, 120, data), 40, offset * 100L))
        }
    }

    @Test fun verticalModalBordersAloneAreNotItems() {
        val data = ByteArray(80 * 120) { 255.toByte() }
        for (y in 0 until 120) for (x in listOf(0, 1, 2, 3, 76, 77, 78, 79)) data[y * 80 + x] = 0
        val detector = InventoryEndDetector()
        for (time in 0L..2000L step 200) assertEquals(InventoryEndDetector.Status.NOT_VISIBLE,
            detector.observe(GrayScrollFrame(80, 120, data), 40, time))
    }

    @Test fun optionalRealTwoEggTailConfirmsWithOnlyAShortWhiteFooter() {
        val directory = System.getenv("SCANNER_UI_PROFILES")
        val fixture = directory?.let { java.io.File(it, "user-pets-bottom-end.gray") }
        org.junit.Assume.assumeTrue(fixture?.isFile == true)
        val input = java.io.DataInputStream(fixture!!.inputStream())
        val width = input.readInt(); val height = input.readInt(); val row = input.readInt()
        val pixels = ByteArray(width * height).also { input.readFully(it) }
        input.close()
        val last = GrayScrollFrame(width, height, pixels)
        val detector = InventoryEndDetector()
        for (time in 0L..800L step 200) assertEquals(InventoryEndDetector.Status.CHECKING, detector.observe(last, row, time))
        assertEquals(InventoryEndDetector.Status.VISIBLE_END, detector.observe(last, row, 1000))
    }

    @Test fun shorterArtworkDoesNotTurnAGrowingOrdinaryGapIntoAnEnd() {
        val document = ByteArray(80 * 240) { 255.toByte() }
        for (row in 0 until 6) {
            val artBottom = if (row == 2) 20 else 29
            for (y in row * 40 + 8..row * 40 + artBottom) for (col in 0 until 5) {
                for (x in col * 16 + 4 until col * 16 + 12) document[y * 80 + x] = 30
            }
        }
        fun at(offset: Int) = GrayScrollFrame(80, 120, document.copyOfRange(offset * 80, (offset + 120) * 80))
        val detector = InventoryEndDetector()
        // Footer grows19->27px while the next complete row remains just outside
        // the viewport. Even holding there must not confirm a truncated list.
        assertEquals(InventoryEndDetector.Status.NOT_VISIBLE, detector.observe(at(0), 40, 0))
        for (time in 100L..2000L step 100) assertEquals(InventoryEndDetector.Status.NOT_VISIBLE,
            detector.observe(at(8), 40, time))
    }

    @Test fun quarterRowArtworkNeverMakesAnOrdinaryGapAWholeEmptyRow() {
        val detector = InventoryEndDetector()
        for (offset in 0..39) {
            val data = ByteArray(80 * 120) { index ->
                val x = index % 80; val localY = (index / 80 + offset) % 40
                if (localY in 8..17 && x % 16 in 4..11) 30 else 255.toByte()
            }
            val frame = GrayScrollFrame(80, 120, data)
            for (hold in 0..5) assertEquals("Short art gap at phase $offset", InventoryEndDetector.Status.NOT_VISIBLE,
                detector.observe(frame, 40, offset * 2000L + hold * 200L))
        }
    }
}
