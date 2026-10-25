package eu.komars.forgescanner.model

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Recovery only: compare independent column strips, tolerating an animated/occluded strip.
 * Every use remains explicitly approximate, even when the image match is strong.
 */
class ScrollMotionRecovery {
    data class Estimate(val displacement: Double, val imageMatched: Boolean)

    fun estimate(old: GrayScrollFrame, next: GrayScrollFrame, columns: Int, rowHeight: Int,
                 predicted: Double): Estimate {
        if (old.pixels.contentEquals(next.pixels)) return Estimate(0.0, true)
        val limit = (old.height * .8).toInt()
        val scores = DoubleArray(limit * 2 + 1) { Double.POSITIVE_INFINITY }
        for (shift in -limit..limit) {
            val bands = DoubleArray(columns) { col ->
                var sum = 0L; var count = 0
                val left = col * old.width / columns + 2
                val right = (col + 1) * old.width / columns - 2
                for (y in max(0, -shift) until min(old.height, old.height - shift) step 2) {
                    for (x in left until right step 3) {
                        sum += abs(old.pixel(x, y + shift) - next.pixel(x, y)); count++
                    }
                }
                if (count > 0) sum.toDouble() / count else 255.0
            }.sorted()
            // Require a majority of columns to agree; a single blank column is not evidence.
            scores[shift + limit] = bands.take(max(1, (columns + 1) / 2)).average()
        }
        val best = scores.minOrNull() ?: 255.0
        if (best > 32.0) return Estimate(predicted.coerceIn(-old.height * 3.0, old.height * 3.0), false)
        val peaks = scores.indices.filter { i -> scores[i] <= best + 4.0 &&
            (i == 0 || scores[i] <= scores[i - 1]) &&
            (i == scores.lastIndex || scores[i] <= scores[i + 1]) }
        val index = peaks.minByOrNull { abs(it - limit - predicted) } ?: scores.indices.minBy { scores[it] }
        // Do not infer an entire unseen row from a repetitive peak without marking uncertainty.
        val shift = index - limit
        val correction = if (index > 0 && index < scores.lastIndex && scores[index] > .001) {
            val a = scores[index - 1]; val b = scores[index]; val c = scores[index + 1]
            val denominator = a - 2 * b + c
            if (denominator > .001) (.5 * (a - c) / denominator).coerceIn(-.5, .5) else 0.0
        } else 0.0
        return Estimate(shift + correction, abs(shift - predicted) < rowHeight * 1.5)
    }
}
