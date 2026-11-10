package eu.komars.forgescanner.automation

import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GuidedRecordingFlowTest {
    @Test fun ignoresUnrecognizedScreens() {
        val flow = GuidedRecordingFlow()
        assertTrue(flow.regions(360, 480, IntArray(360 * 480)).isEmpty())
        repeat(3) { assertTrue(flow.observe(mapOf("hammers" to "123")).isEmpty()) }
        assertTrue(flow.instruction().contains("młotków"))
    }

    @Test fun confirmsCountersFromTwoConsecutiveFramesOnTheGameScreen() {
        val file = File("../.local/clan-tech/home-wallet-94.png")
        if (!file.isFile) return
        val image = ImageIO.read(file)
        val pixels = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
        val flow = GuidedRecordingFlow()
        fun frame(vararg values: Pair<String, String>): List<RecordingValue> {
            flow.regions(image.width, image.height, pixels)
            return flow.observe(mapOf(*values))
        }
        assertTrue(frame("coins" to "2.12m", "gems" to "7 8").isEmpty())
        assertTrue(frame("coins" to "2.13m").isEmpty())
        assertTrue(frame("coins" to "2.12m", "gems" to "7").isEmpty())
        assertEquals(listOf(RecordingValue("coins", 2_120_000, true), RecordingValue("gems", 7, false)),
            frame("coins" to "2.12m", "gems" to "7"))
        assertTrue(frame("coins" to "2.12m", "gems" to "7").isEmpty())
    }
}
