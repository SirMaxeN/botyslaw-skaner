package eu.komars.forgescanner.automation

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import kotlin.math.abs
import kotlin.math.sqrt

/** Low-resolution local layout signature. It is not a retained screen capture or OCR result. */
data class ScreenCheckpoint(val aspect: Float, val samples: List<Int>) {
    val valid: Boolean get() = aspect.isFinite() && aspect > 0 && samples.size == WIDTH * HEIGHT && samples.all { it in -1..255 } &&
        samples.count { it >= 0 } >= 48 && sqrt(samples.filter { it >= 0 }.map { it.toDouble() }.let { values ->
            val mean = values.average(); values.sumOf { (it - mean) * (it - mean) } / values.size
        }) >= 12.0

    /** Sparse mostly-white screens must match their actual dark UI, not just their whitespace. */
    fun matches(other: ScreenCheckpoint): Boolean {
        if (!valid || !other.valid || abs(other.aspect / aspect - 1) > .025f) return false
        val informative = samples.indices.filter { samples[it] >= 0 && other.samples[it] >= 0 && (samples[it] < 220 || other.samples[it] < 220) }
        if (informative.size < 24) return false
        val differences = informative.map { abs(samples[it] - other.samples[it]) }
        return differences.average() <= 25.0 && differences.count { it > 65 }.toDouble() / differences.size <= .16
    }

    fun matches(bitmap: Bitmap): Boolean = matches(capture(bitmap))

    companion object {
        const val WIDTH = 32
        const val HEIGHT = 64
        fun capture(bitmap: Bitmap, excludedRegions: List<Rect> = emptyList()): ScreenCheckpoint {
            require(!bitmap.isRecycled && bitmap.width >= WIDTH && bitmap.height >= HEIGHT)
            val samples = ArrayList<Int>(WIDTH * HEIGHT)
            // Cell averages suppress antialiasing and one-pixel translation changes.
            for (y in 0 until HEIGHT) for (x in 0 until WIDTH) {
                val centerX = ((x + .5) * bitmap.width / WIDTH).toInt()
                val centerY = ((y + .5) * bitmap.height / HEIGHT).toInt()
                if (excludedRegions.any { it.contains(centerX, centerY) }) { samples += -1; continue }
                var gray = 0
                for (dy in 0..1) for (dx in 0..1) {
                    val px = ((x + .25 + dx * .5) * bitmap.width / WIDTH).toInt().coerceIn(0, bitmap.width - 1)
                    val py = ((y + .25 + dy * .5) * bitmap.height / HEIGHT).toInt().coerceIn(0, bitmap.height - 1)
                    val color = bitmap.getPixel(px, py)
                    gray += (Color.red(color) * 299 + Color.green(color) * 587 + Color.blue(color) * 114) / 1000
                }
                samples += gray / 4
            }
            return ScreenCheckpoint(bitmap.width.toFloat() / bitmap.height, samples)
        }
    }
}
