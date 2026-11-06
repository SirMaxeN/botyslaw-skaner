package eu.komars.forgescanner.model

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class CloseButtonPixelDetectorTest {
    private val width = 320
    private val height = 560
    private fun image() = IntArray(width * height) { 0xfff5f5f5.toInt() }
    private fun paint(pixels: IntArray, cx: Int, cy: Int, radius: Int = 27, symbol: String = "x") {
        for (y in cy - radius..cy + radius) for (x in cx - radius..cx + radius) {
            val dx = x - cx; val dy = y - cy
            if (dx * dx + dy * dy <= radius * radius) pixels[y * width + x] = 0xffed335b.toInt()
            val inSymbol = maxOf(abs(dx), abs(dy)) <= radius * .42
            val white = when (symbol) {
                "x" -> inSymbol && abs(abs(dx) - abs(dy)) <= 2
                "+" -> inSymbol && (abs(dx) <= 2 || abs(dy) <= 2)
                "red-cross" -> false
                else -> false
            }
            if (white) pixels[y * width + x] = -1
            if (symbol == "red-cross") pixels[y * width + x] =
                if (abs(dx) <= 3 || abs(dy) <= 3) 0xffed335b.toInt() else -1
        }
    }

    @Test fun proposesUniqueRedCircleWithActualDiagonalWhiteX() {
        val pixels = image(); paint(pixels, 160, 450)
        val found = CloseButtonPixelDetector().detect(width, height, pixels)
        assertNotNull(found)
        assertTrue(found!!.left < 160 && found.right > 160 && found.top < 450 && found.bottom > 450)
    }

    @Test fun refusesPlusSolidCircleAndRedCrossBadge() {
        for (symbol in listOf("+", "none", "red-cross")) {
            val pixels = image(); paint(pixels, 160, 450, symbol = symbol)
            assertNull(symbol, CloseButtonPixelDetector().detect(width, height, pixels))
        }
    }

    @Test fun refusesAmbiguousTwoXButtonsUnlessExpectedRegionIsNearby() {
        val pixels = image(); paint(pixels, 60, 100); paint(pixels, 220, 450)
        val detector = CloseButtonPixelDetector()
        assertNull(detector.detect(width, height, pixels))
        val found = detector.detect(width, height, pixels, PixelRegion(190, 420, 250, 480))
        assertNotNull(found)
        assertTrue(found!!.top > 400)
        assertNull(detector.detect(width, height, pixels, PixelRegion(120, 240, 180, 300)))
    }

    @Test fun supportsDifferentButtonPositionsAndSizes() {
        for ((cx, cy, radius) in listOf(Triple(260, 80, 15), Triple(90, 330, 28), Triple(200, 490, 34))) {
            val pixels = image(); paint(pixels, cx, cy, radius)
            assertNotNull("radius=$radius", CloseButtonPixelDetector().detect(width, height, pixels))
        }
    }

    @Test fun rejectsRedSquareEvenIfItContainsAnX() {
        val pixels = image(); paint(pixels, 160, 450)
        for (y in 423..477) for (x in 133..187) {
            if (pixels[y * width + x] == 0xfff5f5f5.toInt()) pixels[y * width + x] = 0xffed335b.toInt()
        }
        assertNull(CloseButtonPixelDetector().detect(width, height, pixels))
    }

    @Test fun optionalPrivateGameReference() {
        val path = System.getenv("SCANNER_CLOSE_REFERENCE")
        org.junit.Assume.assumeTrue(path != null)
        java.io.DataInputStream(java.io.File(path!!).inputStream().buffered()).use { input ->
            val outputWidth = input.readInt(); val outputHeight = input.readInt()
            val pixels = IntArray(outputWidth * outputHeight) { input.readInt() }
            assertNotNull(CloseButtonPixelDetector().detect(outputWidth, outputHeight, pixels))
        }
    }
}
