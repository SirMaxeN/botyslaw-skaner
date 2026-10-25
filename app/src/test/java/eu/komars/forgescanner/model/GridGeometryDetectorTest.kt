package eu.komars.forgescanner.model

import org.junit.Assert.*
import org.junit.Test

class GridGeometryDetectorTest {
    private fun screen(single: Boolean = false): GrayScrollFrame {
        val pixels = ByteArray(200 * 300) { 255.toByte() }
        for (row in 0 until if (single) 1 else 4) for (col in 0 until if (single) 1 else 5) {
            for (y in 42 + row * 36 until 68 + row * 36) for (x in 12 + col * 36 until 36 + col * 36) pixels[y * 200 + x] = 40
        }
        for (y in 198 until 228) for (x in 4 until 194) pixels[y * 200 + x] = 40
        return GrayScrollFrame(200, 300, pixels)
    }
    @Test fun looseSelectionFitsSpacingAndStopsBeforeTheEquippedPanel() {
        val result = GridGeometryDetector().detect(screen(), PixelRegion(20, 48, 170, 185))!!
        assertEquals(36.0, result.rowHeight, .1)
        assertEquals(6, result.region.left)
        assertEquals(186, result.region.right)
        assertEquals(196, result.region.bottom)
        assertEquals(20, result.icons)
    }
    @Test fun oneMountDoesNotRequireMultipleRowsToCalibrate() {
        val result = GridGeometryDetector().detect(screen(true), PixelRegion(5, 32, 190, 185))!!
        assertEquals(1, result.icons)
        assertTrue(result.region.bottom < 198)
    }
    @Test fun emptySelectionDoesNotInventAGrid() {
        val blank = GrayScrollFrame(200, 300, ByteArray(60000) { 255.toByte() })
        assertNull(GridGeometryDetector().detect(blank, PixelRegion(0, 30, 200, 180)))
    }
    @Test fun optionalRealSingleMountExcludesTitleKeysAndModalBorder() {
        val path = System.getenv("SCANNER_MOUNT_SCREEN")
        org.junit.Assume.assumeTrue(path != null)
        java.io.DataInputStream(java.io.File(path!!).inputStream()).use { input ->
            val w = input.readInt(); val h = input.readInt()
            val frame = GrayScrollFrame(w, h, ByteArray(w * h).also { input.readFully(it) })
            // The actual old phone calibration included the heading and key counter.
            val fitted = GridGeometryDetector().detect(frame, PixelRegion(33, 196, 276, 524))!!
            println("MOUNT_GEOMETRY: $fitted")
            assertEquals(1, fitted.icons)
            assertTrue(fitted.region.top >= 233)
            assertTrue(fitted.region.left >= 15)
            val rect = fitted.region
            val cropped = GrayScrollFrame(rect.width, rect.height, ByteArray(rect.width * rect.height) { i ->
                frame.pixels[(rect.top + i / rect.width) * w + rect.left + i % rect.width]
            })
            assertEquals(1L, ScrollSequenceAnalyzer().analyze(List(30) { cropped }, 5, fitted.rowHeight.toInt()).count)
            // Reuse the real mount card as a long packed inventory, not a one-mount special case.
            val pitch = fitted.rowHeight.toInt()
            val document = ByteArray(rect.width * pitch * 16) { 255.toByte() }
            for (row in 0 until 16) for (col in 0 until if (row == 15) 1 else 5) {
                for (y in 0 until pitch) for (x in 0 until pitch) {
                    document[(row * pitch + y) * rect.width + col * pitch + x] = cropped.pixels[y * cropped.width + x]
                }
            }
            val viewport = pitch * 4
            val sequence = (0..pitch * 12 step 6).map { offset ->
                GrayScrollFrame(rect.width, viewport, document.copyOfRange(offset * rect.width, (offset + viewport) * rect.width))
            }
            assertEquals(76L, ScrollSequenceAnalyzer().analyze(sequence, 5, pitch).count)
        }
    }
    @Test fun optionalRealScreenFitsWithoutPixelPerfectSelection() {
        val path = System.getenv("SCANNER_GRID_FIXTURE")
        org.junit.Assume.assumeTrue(path != null)
        java.io.DataInputStream(java.io.File(path!!).inputStream()).use { input ->
            val w = input.readInt(); val h = input.readInt()
            val frame = GrayScrollFrame(w, h, ByteArray(w * h).also { input.readFully(it) })
            val result = GridGeometryDetector().detect(frame, PixelRegion(14, 96, 342, 359))!!
            println("GRID_GEOMETRY: $result")
            assertTrue(result.icons >= 15)
            assertEquals(200.0, result.rowHeight * 3, 4.0)
            assertTrue(result.region.bottom * 3 <= 1120)
            val loose = GridGeometryDetector().detect(frame, PixelRegion(0, 28, 359, 371))!!
            assertTrue("Header currency icons must not establish the grid", loose.region.top >= 88)
            assertEquals(result.rowHeight, loose.rowHeight, 1.0)
        }
    }
}
