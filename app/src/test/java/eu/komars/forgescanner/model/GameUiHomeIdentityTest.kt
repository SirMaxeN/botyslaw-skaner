package eu.komars.forgescanner.model

import eu.komars.forgescanner.automation.AutoScanStep
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.DataInputStream
import java.io.File

class GameUiHomeIdentityTest {
    private data class Profile(val width: Int, val height: Int, val pixels: IntArray) {
        fun detector() = GameUiGeometryDetector(width, height, pixels)
        fun paint(box: PixelRegion, color: Int) {
            for (y in box.top until box.bottom) for (x in box.left until box.right)
                pixels[y * width + x] = color
        }
    }

    private fun profile(name: String): Profile {
        val file = System.getenv("SCANNER_UI_PROFILES")?.let { File(it, "$name.gray") }
        assumeTrue(file?.isFile == true)
        return DataInputStream(file!!.inputStream().buffered()).use { input ->
            val width = input.readInt(); val height = input.readInt()
            Profile(width, height, IntArray(width * height) { input.readInt() })
        }
    }

    @Test fun actualHomeAfterClosingMountsIsAValidDestinationWithItsOwnChrome() {
        val detector = profile("after-close-1.2.8").detector()
        assertEquals(GameScreenKind.HOME, detector.screenKind())
        for (step in listOf(AutoScanStep.HAMMERS, AutoScanStep.OPEN_MOUNTS, AutoScanStep.OPEN_RESEARCH))
            assertNotNull(step.name, detector.detect(step))
        assertNull(detector.detect(AutoScanStep.CLOSE_MOUNTS))
        assertNull(detector.detect(AutoScanStep.CLOSE_RESEARCH))
    }

    @Test fun changedEquipmentAndBattleArtCannotHideHomeOrItsResearchButton() {
        val source = profile("after-close-1.2.8")
        val original = source.detector()
        val research = original.detect(AutoScanStep.OPEN_RESEARCH)!!.region
        val mount = original.detect(AutoScanStep.OPEN_MOUNTS)!!.region
        source.paint(PixelRegion(0, 0, source.width, (source.height * .54).toInt()), 0xff8758a9.toInt())
        source.paint(mount, 0xff08a8e0.toInt())
        val detector = source.detector()
        assertEquals(GameScreenKind.HOME, detector.screenKind())
        assertEquals(research, detector.detect(AutoScanStep.OPEN_RESEARCH)!!.region)
        assertNotNull(detector.detect(AutoScanStep.HAMMERS))
    }
}
