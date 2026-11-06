package eu.komars.forgescanner.model

import eu.komars.forgescanner.automation.AutoScanStep
import eu.komars.forgescanner.automation.ShortViewportInventoryDetector
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.DataInputStream
import java.io.File
import kotlin.math.roundToInt

class InventoryViewportProposalTest {
    private class Profile(val width: Int, val height: Int, val colors: IntArray) {
        fun detector() = GameUiGeometryDetector(width, height, colors)
        fun gray() = GrayScrollFrame(width, height, ByteArray(colors.size) { i ->
            val c = colors[i]; (((c ushr 16 and 255) * 299 + (c ushr 8 and 255) * 587 + (c and 255) * 114) / 1000).toByte()
        })
    }
    private fun load(name: String): Profile {
        val directory = System.getenv("SCANNER_UI_PROFILES")
        val file = directory?.let { File(it, "$name.gray") }; assumeTrue(file?.isFile == true)
        return DataInputStream(file!!.inputStream()).use { input ->
            val width = input.readInt(); val height = input.readInt()
            Profile(width, height, IntArray(width * height) { input.readInt() })
        }
    }
    @Test fun oneAndFiveMountsProposeTheFullEmptyScrollAreaAndFitTheirActualCounts() {
        var previous: PixelRegion? = null
        for ((name, count) in listOf("one-mount" to 1L, "changed-mounts-confirmed" to 5L)) {
            val p = load(name); val box = p.detector().detect(AutoScanStep.MOUNT_LIST)!!.region
            assertTrue(box.width >= p.width * .80); assertTrue(box.height >= p.height * .28)
            previous?.let { assertTrue(ApprovedInventoryViewport.matches(box, it, 4)) }; previous = box
            val grid = ApprovedInventoryViewport.fit(p.gray(), box, 5)!!
            assertEquals(box.bottom, grid.region.bottom)
            assertTrue(grid.region.height >= box.height * .90)
            val w = 96; val h = (grid.region.height * w.toDouble() / grid.region.width).roundToInt()
            val row = (grid.rowHeight * w / grid.region.width).roundToInt()
            val raw = p.gray()
            val sample = GrayScrollFrame(w, h, ByteArray(w * h) { i ->
                val x = grid.region.left + ((i % w + .5) * grid.region.width / w).toInt()
                val y = grid.region.top + ((i / w + .5) * grid.region.height / h).toInt()
                raw.pixels[y * p.width + x]
            })
            val detector = ShortViewportInventoryDetector()
            detector.observe(sample, row, 5, 0); detector.observe(sample, row, 5, 300); detector.observe(sample, row, 5, 600)
            assertEquals(count, detector.observe(sample, row, 5, 900))
        }
    }
    @Test fun petsViewportEndsBeforeEquipmentAndDoesNotDependOnItemArtwork() {
        val p = load("pets-top")
        val original = p.detector().detect(AutoScanStep.PET_LIST)!!.region
        assertTrue(original.top in (p.height * .10).toInt()..(p.height * .16).toInt())
        assertTrue(original.bottom in (p.height * .43).toInt()..(p.height * .53).toInt())
        val fitted = ApprovedInventoryViewport.fit(p.gray(), original, 5)!!
        assertTrue(fitted.icons >= 5); assertEquals(original.bottom, fitted.region.bottom)
        for (y in original.top until original.bottom) for (x in original.left until original.right) p.colors[y * p.width + x] = -1
        assertEquals(original, p.detector().detect(AutoScanStep.PET_LIST)!!.region)
    }
    @Test fun emptyOrUnrecognizedScreensCannotCreateAnInventoryCalibration() {
        val frame = GrayScrollFrame(100, 200, ByteArray(20000) { 255.toByte() })
        assertNull(ApprovedInventoryViewport.fit(frame, PixelRegion(0, 0, 100, 200), 5))
        assertFalse(ApprovedInventoryViewport.matches(PixelRegion(0, 0, 100, 150), PixelRegion(0, 0, 100, 200), 3))
    }
}
