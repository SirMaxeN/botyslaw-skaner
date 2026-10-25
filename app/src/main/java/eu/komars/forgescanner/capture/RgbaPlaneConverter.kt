package eu.komars.forgescanner.capture

import java.nio.ByteBuffer

/** Converts only visible RGBA pixels, without assuming contiguous hardware rows. */
internal object RgbaPlaneConverter {
    fun readArgb(
        buffer: ByteBuffer,
        width: Int,
        height: Int,
        rowStride: Int,
        pixelStride: Int,
        left: Int = 0,
        top: Int = 0,
        cropWidth: Int = width,
        cropHeight: Int = height,
    ): IntArray {
        require(width > 0 && height > 0 && pixelStride >= 4)
        require(rowStride.toLong() >= pixelStride.toLong() * width)
        require(left >= 0 && top >= 0 && cropWidth > 0 && cropHeight > 0)
        require(left.toLong() + cropWidth <= width && top.toLong() + cropHeight <= height)
        val count = cropWidth.toLong() * cropHeight
        require(count <= Int.MAX_VALUE)
        val lastOffset = buffer.position().toLong() + (top + cropHeight - 1L) * rowStride + (left + cropWidth - 1L) * pixelStride
        require(lastOffset + 3 < buffer.limit()) { "Incomplete RGBA frame" }
        val pixels = IntArray(count.toInt())
        var output = 0
        for (y in top until top + cropHeight) {
            var offset = buffer.position() + y * rowStride + left * pixelStride
            for (x in 0 until cropWidth) {
                val r = buffer.get(offset).toInt() and 255
                val g = buffer.get(offset + 1).toInt() and 255
                val b = buffer.get(offset + 2).toInt() and 255
                val a = buffer.get(offset + 3).toInt() and 255
                pixels[output++] = (a shl 24) or (r shl 16) or (g shl 8) or b
                offset += pixelStride
            }
        }
        return pixels
    }
    /** Small grayscale ROI directly from the capture plane; no full-screen Bitmap allocation. */
    fun readGrayRegion(buffer: ByteBuffer, width: Int, height: Int, rowStride: Int, pixelStride: Int,
        left: Int, top: Int, cropWidth: Int, cropHeight: Int, outputWidth: Int, outputHeight: Int): ByteArray {
        require(width > 0 && height > 0 && pixelStride >= 4 && rowStride.toLong() >= width.toLong() * pixelStride)
        require(left >= 0 && top >= 0 && cropWidth > 0 && cropHeight > 0)
        require(left.toLong() + cropWidth <= width && top.toLong() + cropHeight <= height)
        require(outputWidth in 16..320 && outputHeight in 16..1200)
        val base = buffer.position()
        val last = base.toLong() + (top + cropHeight - 1L) * rowStride + (left + cropWidth - 1L) * pixelStride
        require(last + 3 < buffer.limit()) { "Incomplete RGBA frame" }
        val result = ByteArray(outputWidth * outputHeight)
        // Four taps reduce aliasing on narrow outlines and white inter-row gaps.
        for (y in 0 until outputHeight) for (x in 0 until outputWidth) {
            var gray = 0
            for (dy in 0..1) for (dx in 0..1) {
                val sx = left + ((x + 0.25 + dx * 0.5) * cropWidth / outputWidth).toInt().coerceIn(0, cropWidth - 1)
                val sy = top + ((y + 0.25 + dy * 0.5) * cropHeight / outputHeight).toInt().coerceIn(0, cropHeight - 1)
                val index = base + sy * rowStride + sx * pixelStride
                gray += ((buffer.get(index).toInt() and 255) * 299 +
                    (buffer.get(index + 1).toInt() and 255) * 587 +
                    (buffer.get(index + 2).toInt() and 255) * 114) / 1000
            }
            result[y * outputWidth + x] = (gray / 4).toByte()
        }
        return result
    }

}
