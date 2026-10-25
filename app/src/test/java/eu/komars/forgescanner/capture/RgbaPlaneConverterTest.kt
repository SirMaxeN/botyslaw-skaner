package eu.komars.forgescanner.capture

import java.nio.ByteBuffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class RgbaPlaneConverterTest {
    @Test
    fun skipsRowPaddingWithoutRequiringPaddingAfterLastRow() {
        val bytes = ByteArray(20) { 99 }
        pixel(bytes, 0, 255, 0, 0)
        pixel(bytes, 4, 0, 255, 0)
        pixel(bytes, 12, 0, 0, 255)
        pixel(bytes, 16, 255, 255, 255)
        assertArrayEquals(
            intArrayOf(0xffff0000.toInt(), 0xff00ff00.toInt(), 0xff0000ff.toInt(), 0xffffffff.toInt()),
            RgbaPlaneConverter.readArgb(ByteBuffer.wrap(bytes), 2, 2, 12, 4),
        )
    }

    @Test
    fun honorsPixelStrideCropAndInitialBufferPosition() {
        val bytes = ByteArray(39) { 99 }
        pixel(bytes, 3, 1, 2, 3)
        pixel(bytes, 11, 4, 5, 6)
        pixel(bytes, 27, 7, 8, 9)
        pixel(bytes, 35, 10, 11, 12)
        val buffer = ByteBuffer.wrap(bytes).apply { position(3) }
        assertArrayEquals(intArrayOf(0xff0a0b0c.toInt()), RgbaPlaneConverter.readArgb(buffer, 2, 2, 24, 8, 1, 1, 1, 1))
        assertEquals(3, buffer.position())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTruncatedPixels() {
        RgbaPlaneConverter.readArgb(ByteBuffer.wrap(ByteArray(7)), 2, 1, 8, 4)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOutOfBoundsCrop() {
        RgbaPlaneConverter.readArgb(ByteBuffer.wrap(ByteArray(16)), 2, 2, 8, 4, 1, 1, 2, 1)
    }

    @Test fun grayRegionRespectsCropPaddingAndBufferPosition() {
        val rowStride = 32 * 4 + 8
        val bytes = ByteArray(3 + rowStride * 31 + 32 * 4) { 99 }
        for (y in 0 until 32) for (x in 0 until 32) {
            val gray = if (x >= 8 && y >= 8) 200 else 20
            pixel(bytes, 3 + y * rowStride + x * 4, gray, gray, gray)
        }
        val buffer = ByteBuffer.wrap(bytes).apply { position(3) }
        val gray = RgbaPlaneConverter.readGrayRegion(buffer, 32, 32, rowStride, 4, 8, 8, 24, 24, 16, 16)
        assertEquals(256, gray.size)
        assertEquals(3, buffer.position())
        assertEquals(true, gray.all { (it.toInt() and 255) == 200 })
    }
    @Test(expected = IllegalArgumentException::class) fun grayRegionRejectsTruncatedCapture() {
        RgbaPlaneConverter.readGrayRegion(ByteBuffer.wrap(ByteArray(32 * 32 * 4 - 1)),
            32, 32, 128, 4, 0, 0, 32, 32, 16, 16)
    }

    private fun pixel(bytes: ByteArray, offset: Int, red: Int, green: Int, blue: Int) {
        bytes[offset] = red.toByte()
        bytes[offset + 1] = green.toByte()
        bytes[offset + 2] = blue.toByte()
        bytes[offset + 3] = 255.toByte()
    }
}
