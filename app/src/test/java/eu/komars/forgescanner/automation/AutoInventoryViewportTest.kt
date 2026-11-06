package eu.komars.forgescanner.automation

import eu.komars.forgescanner.model.GrayScrollFrame
import org.junit.Assert.*
import org.junit.Test

class AutoInventoryViewportTest {
    @Test fun scanDirectionAlwaysMovesFingerUpward() {
        assertEquals(1450f, AutoInventoryScrollPolicy.upwardDestination(1500f, 50f), .001f)
        assertTrue(AutoInventoryScrollPolicy.upwardDestination(800f, 20f) < 800f)
        try { AutoInventoryScrollPolicy.upwardDestination(100f, -10f); fail("Reverse motion must not be accepted") }
        catch (_: IllegalArgumentException) { }
    }

    @Test fun oneMountWithModalSideBordersAndWhiteFooterCompletesWithoutASwipe() {
        val detector = ShortViewportInventoryDetector()
        val frame = viewport(rows = 1, itemsInFinalRow = 1, sideBorder = true)
        assertNull(detector.observe(frame, 24, 5, 0))
        assertFalse(detector.fullViewportVisible)
        assertNull(detector.observe(frame, 24, 5, 300))
        assertNull(detector.observe(frame, 24, 5, 600))
        assertEquals(1L, detector.observe(frame, 24, 5, 900))
    }

    @Test fun aStableViewportFullOfIdenticalEggsIsNotACompleteList() {
        val detector = ShortViewportInventoryDetector()
        val frame = viewport(rows = 5, itemsInFinalRow = 5)
        for (time in 0L..1800L step 150) assertNull(detector.observe(frame, 24, 5, time))
        assertTrue("A clearly full first viewport can begin scrolling immediately", detector.fullViewportVisible)
    }

    @Test fun anEmptyWhiteScreenCannotProduceAZeroInventory() {
        val detector = ShortViewportInventoryDetector()
        val frame = GrayScrollFrame(100, 120, ByteArray(12000) { 255.toByte() })
        for (time in 0L..1800L step 150) assertNull(detector.observe(frame, 24, 5, time))
    }

    @Test fun aChangedCountRestartsTheStableInterval() {
        val detector = ShortViewportInventoryDetector()
        val one = viewport(rows = 1, itemsInFinalRow = 1)
        val two = viewport(rows = 1, itemsInFinalRow = 2)
        detector.observe(one, 24, 5, 0)
        detector.observe(one, 24, 5, 600)
        assertNull(detector.observe(two, 24, 5, 900))
        detector.observe(two, 24, 5, 1200)
        detector.observe(two, 24, 5, 1500)
        assertEquals(2L, detector.observe(two, 24, 5, 1800))
    }

    @Test fun optionalRealOneMountScreenshotHasOneVisibleRawItem() {
        val fixture = System.getenv("SCANNER_AUTO_ONE_MOUNT_GRAY")
        org.junit.Assume.assumeTrue(fixture != null)
        val input = java.io.DataInputStream(java.io.File(fixture!!).inputStream())
        val width = input.readInt(); val height = input.readInt(); val row = input.readInt()
        val pixels = ByteArray(width * height).also { input.readFully(it) }
        input.close()
        val frame = GrayScrollFrame(width, height, pixels)
        val detector = ShortViewportInventoryDetector()
        for (time in listOf(0L, 300L, 600L)) assertNull(detector.observe(frame, row, 5, time))
        assertEquals(1L, detector.observe(frame, row, 5, 900L))
    }

    @Test fun anyShortPackedMountListCanCompleteWithoutAssumingOneItem() {
        for (count in listOf(2, 5, 8, 15)) {
            val frame = viewport(rows = (count + 4) / 5, itemsInFinalRow = (count - 1) % 5 + 1, sideBorder = true)
            val detector = ShortViewportInventoryDetector()
            for (time in listOf(0L, 300L, 600L)) assertNull(detector.observe(frame, 24, 5, time))
            assertEquals(count.toLong(), detector.observe(frame, 24, 5, 900))
        }
    }

    @Test fun optionalActualChangedInventoryHasFiveMountsWithItsOwnFittedGrid() {
        val directory = System.getenv("SCANNER_UI_PROFILES")
        val fixture = directory?.let { java.io.File(it, "changed-mounts-confirmed.gray") }
        org.junit.Assume.assumeTrue(fixture?.isFile == true)
        val input = java.io.DataInputStream(fixture!!.inputStream())
        val width = input.readInt(); val height = input.readInt()
        val pixels = ByteArray(width * height) {
            val color = input.readInt()
            (((color ushr 16 and 255) * 299 + (color ushr 8 and 255) * 587 + (color and 255) * 114) / 1000).toByte()
        }
        input.close()
        val source = GrayScrollFrame(width, height, pixels)
        val geometry = eu.komars.forgescanner.model.GridGeometryDetector().detect(source,
            eu.komars.forgescanner.model.PixelRegion(width * 4 / 100, height * 30 / 100,
                width * 96 / 100, height * 68 / 100), 5)
        assertNotNull(geometry)
        val grid = geometry!!
        val sampleWidth = 96
        val sampleHeight = kotlin.math.round(grid.region.height * sampleWidth.toDouble() / grid.region.width).toInt()
        val row = kotlin.math.round(grid.rowHeight * sampleWidth / grid.region.width).toInt()
        val sample = ByteArray(sampleWidth * sampleHeight) { index ->
            val x = grid.region.left + ((index % sampleWidth + .5) * grid.region.width / sampleWidth).toInt()
            val y = grid.region.top + ((index / sampleWidth + .5) * grid.region.height / sampleHeight).toInt()
            pixels[y * width + x]
        }
        val frame = GrayScrollFrame(sampleWidth, sampleHeight, sample)
        val detector = ShortViewportInventoryDetector()
        for (time in listOf(0L, 300L, 600L)) assertNull(detector.observe(frame, row, 5, time))
        assertEquals(5L, detector.observe(frame, row, 5, 900))
    }

    private fun viewport(rows: Int, itemsInFinalRow: Int, sideBorder: Boolean = false): GrayScrollFrame {
        val width = 100; val height = 120; val rowHeight = 24
        val pixels = ByteArray(width * height) { 255.toByte() }
        for (row in 0 until rows) for (col in 0 until if (row == rows - 1) itemsInFinalRow else 5) {
            for (y in row * rowHeight + 3 until row * rowHeight + 21) {
                for (x in col * 20 + 3 until col * 20 + 17) pixels[y * width + x] = if ((x + y) % 3 == 0) 30.toByte() else 180.toByte()
            }
        }
        if (sideBorder) for (y in 0 until height) for (x in listOf(0, 1, 98, 99)) pixels[y * width + x] = 0
        return GrayScrollFrame(width, height, pixels)
    }
}
