package eu.komars.forgescanner.model

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class GrayScrollFrame(val width: Int, val height: Int, val pixels: ByteArray, val timestampNanos: Long = 0,
    val sourceFrameId: Long = 0) {
    init { require(width >= 16 && height >= 16 && pixels.size == width * height) }
    fun pixel(x: Int, y: Int) = pixels[y * width + x].toInt() and 255
}
data class ScrollAnalysis(val count: Long, val frames: Int, val uncertainFrames: Int, val message: String, val failureFrame: Int? = null, val failureReason: String? = null, val captureDurationMillis: Long = 0, val largestGapMillis: Long = 0, val approximate: Boolean = false, val inferredRows: Int = 0, val lowConfidence: Boolean = false)

/** Tracks consecutive motion, including repeated rows, in a shared document coordinate system. */
class ScrollSequenceAnalyzer(private val trace: ((Int, Double, Double) -> Unit)? = null) {
    data class Motion(val pixels: Double, val repeatedRows: Boolean)
    /** Deterministic work metric for matching regressions; no image data is retained. */
    var lastTranslationComparedPixels = 0L
        private set
    fun analyze(frames: List<GrayScrollFrame>, columns: Int, rowHeight: Int, allowEstimation: Boolean = false): ScrollAnalysis {
        require(frames.isNotEmpty())
        val session = ScrollAnalysisSession(columns, rowHeight, allowEstimation, trace)
        frames.forEach(session::offer)
        return session.finish()
    }

    /** Video compression and UI compositing can change a few levels in an otherwise held frame. */
    fun repeatedCapture(a: GrayScrollFrame, b: GrayScrollFrame): Boolean {
        var difference = 0L; var count = 0
        for (i in a.pixels.indices step 3) {
            difference += abs((a.pixels[i].toInt() and 255) - (b.pixels[i].toInt() and 255)); count++
        }
        return difference.toDouble() / count < .6
    }

    /** Cheap live advice from adjacent captures. Final validation still uses the whole sequence. */
    fun guidance(previous: GrayScrollFrame, current: GrayScrollFrame, rowHeight: Int): ScrollGuidance {
        require(rowHeight >= 8 && previous.width == current.width && previous.height == current.height)
        val elapsed = current.timestampNanos - previous.timestampNanos
        if (previous.timestampNanos > 0 && elapsed > 80_000_000L) return ScrollGuidance.PAUSE
        val motion = translation(previous, current, rowHeight, 0.0) ?: return ScrollGuidance.PAUSE
        return when {
            abs(motion.pixels) >= rowHeight * 0.36 -> ScrollGuidance.PAUSE
            abs(motion.pixels) >= rowHeight * 0.18 -> ScrollGuidance.SLOW
            else -> ScrollGuidance.SCROLL
        }
    }

    fun translation(old: GrayScrollFrame, next: GrayScrollFrame, rowHeight: Int, expected: Double): Motion? {
        lastTranslationComparedPixels = 0
        // Equal images can also mean one complete repeated row passed the reference.
        // Stationary consecutive captures are handled before matching the older keyframe.
        if (abs(expected) < rowHeight * .5 && old.pixels.contentEquals(next.pixels)) return Motion(0.0, false)
        val limit = old.height / 2
        val scores = DoubleArray(limit * 2 + 1) { Double.POSITIVE_INFINITY }
        fun score(shift: Int, ceiling: Double): Double {
            val start = max(0, -shift)
            val end = min(old.height, old.height - shift)
            val horizontalSamples = (old.width - 2) / 3
            val total = ((end - start + 1) / 2) * horizontalSamples
            if (total <= 0) return Double.POSITIVE_INFINITY
            var sum = 0L
            for (y in start until end step 2) {
                for (x in 2 until old.width - 2 step 3) {
                    sum += abs(old.pixel(x, y + shift) - next.pixel(x, y))
                }
                lastTranslationComparedPixels += horizontalSamples
                // Remaining absolute errors cannot decrease this lower bound.
                // A score above best + 6 can neither win nor be an eligible peak.
                if (sum.toDouble() / total > ceiling) return Double.POSITIVE_INFINITY
            }
            return sum.toDouble() / total
        }
        var currentBest = Double.POSITIVE_INFINITY
        fun evaluate(shift: Int) {
            val value = score(shift, currentBest + 6.0)
            scores[shift + limit] = value
            currentBest = min(currentBest, value)
        }
        // Start near predicted motion to establish a useful bound immediately.
        // Final selection still uses the original low-to-high array/tie order.
        val center = if (expected.isFinite()) expected.roundToInt().coerceIn(-limit, limit) else 0
        for (distance in 0..limit * 2) {
            if (center - distance >= -limit) evaluate(center - distance)
            if (distance > 0 && center + distance <= limit) evaluate(center + distance)
        }
        val bestIndex = scores.indices.minByOrNull { scores[it] } ?: return null
        val bestScore = scores[bestIndex]
        if (bestScore > 45.0) return null
        // Identical eggs create several equally good peaks one row apart. With dense capture,
        // the nearest peak is the actual motion, rather than a jump to a different identical egg.
        val peaks = scores.indices.filter { index ->
            scores[index] <= bestScore + 6.0 &&
                (index == 0 || scores[index] <= scores[index - 1]) &&
                (index == scores.lastIndex || scores[index] <= scores[index + 1])
        }
        val competing = peaks.any { abs(it - bestIndex) > max(4, rowHeight / 3) }
        // Motion continuity resolves repeated eggs, while a soft image-error tie break retains
        // the stronger match when acceleration puts two adjacent rows almost equally near.
        val index = if (competing) peaks.minByOrNull {
            abs(it - limit - expected) / rowHeight + .01 * (scores[it] - bestScore)
        } ?: bestIndex else bestIndex
        val shift = index - limit
        if (competing && abs(shift - expected) >= rowHeight * 0.60) return null
        // Pruned neighbors cannot be eligible peaks, but their exact errors still
        // participate in the original subpixel fit around the selected minimum.
        if (index > 0 && !scores[index - 1].isFinite()) scores[index - 1] = score(index - 1 - limit, Double.POSITIVE_INFINITY)
        if (index < scores.lastIndex && !scores[index + 1].isFinite()) scores[index + 1] = score(index + 1 - limit, Double.POSITIVE_INFINITY)
        // Fit the minimum between pixel centers; integer rounding otherwise accumulates every frame.
        val correction = if (scores[index] > 0.001 && index > 0 && index < scores.lastIndex) {
            val a = scores[index - 1]; val b = scores[index]; val c = scores[index + 1]
            val denominator = a - 2 * b + c
            if (denominator > 0.001) (0.5 * (a - c) / denominator).coerceIn(-0.5, 0.5) else 0.0
        } else 0.0
        return Motion(shift + correction, competing)
    }

}
