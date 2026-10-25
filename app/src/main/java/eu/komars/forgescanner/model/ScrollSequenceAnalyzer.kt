package eu.komars.forgescanner.model

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class GrayScrollFrame(val width: Int, val height: Int, val pixels: ByteArray, val timestampNanos: Long = 0) {
    init { require(width >= 16 && height >= 16 && pixels.size == width * height) }
    fun pixel(x: Int, y: Int) = pixels[y * width + x].toInt() and 255
}
data class ScrollAnalysis(val count: Long, val frames: Int, val uncertainFrames: Int, val message: String, val failureFrame: Int? = null, val failureReason: String? = null, val captureDurationMillis: Long = 0, val largestGapMillis: Long = 0, val approximate: Boolean = false, val inferredRows: Int = 0, val lowConfidence: Boolean = false)

/** Tracks consecutive motion, including repeated rows, in a shared document coordinate system. */
class ScrollSequenceAnalyzer(private val trace: ((Int, Double, Double) -> Unit)? = null) {
    internal data class Motion(val pixels: Double, val repeatedRows: Boolean)
    fun analyze(frames: List<GrayScrollFrame>, columns: Int, rowHeight: Int, allowEstimation: Boolean = false): ScrollAnalysis {
        require(frames.isNotEmpty() && columns in 1..12 && rowHeight >= 8)
        val first = frames.first()
        require(first.width >= columns * 4)
        require(frames.all { it.width == first.width && it.height == first.height })
        var offset = 0.0
        var anchor = first
        var anchorOffset = 0.0
        var uncertain = 0
        var failureFrame: Int? = null
        var failureReason: String? = null
        var velocity = 0.0
        var previousMotionFrame = 0
        var fastAmbiguity = false
        val rows = InventoryRowAccumulator(columns, rowHeight)
        fun collect(frame: GrayScrollFrame) {
            rows.observe(frame, offset)
        }
        collect(first)
        for (i in 1 until frames.size) {
            val frame = frames[i]
            val gap = frame.timestampNanos > 0 && frames[i - 1].timestampNanos > 0 &&
                frame.timestampNanos - frames[i - 1].timestampNanos > 150_000_000L
            // Preserve velocity through an occasional duplicate display buffer. After a sustained
            // 50 ms hold, discard it: extrapolating the old fling across a pause adds identical rows.
            val previousMotion = frames[previousMotionFrame]
            if (!gap && repeatedCapture(frame, previousMotion)) {
                if (frame.timestampNanos - previousMotion.timestampNanos > 50_000_000L) {
                    velocity = 0.0; previousMotionFrame = i
                }
                continue
            }
            val deltaSeconds = if (frame.timestampNanos > previousMotion.timestampNanos && previousMotion.timestampNanos > 0)
                (frame.timestampNanos - previousMotion.timestampNanos) / 1_000_000_000.0 else 1.0
            var shift = translation(anchor, frame, rowHeight, offset - anchorOffset + velocity * deltaSeconds)
            if (shift?.repeatedRows == true && velocity * deltaSeconds > rowHeight * .20 &&
                shift.pixels - (offset - anchorOffset) in -rowHeight * .15..-.03) {
                // A tiny apparent reversal during a fast forward fling can be a whole-row wrap.
                // Prefer continuity, but expose the decision as an estimate rather than certainty.
                shift = translation(anchor, frame, rowHeight,
                    offset - anchorOffset + max(velocity * deltaSeconds, rowHeight * .65)) ?: shift
                fastAmbiguity = true
            }
            if (shift?.repeatedRows == true && abs(shift.pixels - (offset - anchorOffset + velocity * deltaSeconds)) > rowHeight * .30) {
                // At a new fling, preceding velocity can point to the wrong identical row. Look
                // ahead to the next distinct image before selecting the phase-wrap candidate.
                val future = (i + 1..minOf(i + 6, frames.lastIndex)).map { frames[it] }
                    .firstOrNull { !repeatedCapture(frame, it) }
                if (future != null) {
                    val nextSeconds = if (future.timestampNanos > frame.timestampNanos && frame.timestampNanos > 0)
                        (future.timestampNanos - frame.timestampNanos) / 1_000_000_000.0 else 1.0
                    val following = translation(frame, future, rowHeight, velocity * nextSeconds)
                    if (following != null) {
                        val predicted = offset - anchorOffset + (velocity + following.pixels / nextSeconds) * .5 * deltaSeconds
                        shift = translation(anchor, frame, rowHeight, predicted) ?: shift
                        fastAmbiguity = true
                    }
                }
            }
            // If an anchor approaches the half-row ambiguity, rebase on the immediately preceding frame.
            if (shift == null && !gap && anchor !== previousMotion) {
                anchor = previousMotion; anchorOffset = offset
                shift = translation(anchor, frame, rowHeight, velocity * deltaSeconds)
            }
            if (shift == null || anchorOffset + shift.pixels < -1.0 || gap) {
                uncertain++
                if (failureFrame == null) failureFrame = i
                if (failureReason == null) failureReason = when {
                    gap -> "capture gap ${(frame.timestampNanos - frames[i - 1].timestampNanos) / 1_000_000} ms"
                    shift == null -> "ambiguous image displacement"
                    else -> "negative document offset"
                }
                if (!allowEstimation) break
                // Use motion on both sides of a damaged capture, then reacquire from the next frame.
                // Never silently turn a bridged section into a verified result.
                val following = frames.getOrNull(i + 1)
                val afterSeconds = if (following != null && following.timestampNanos > frame.timestampNanos && frame.timestampNanos > 0)
                    (following.timestampNanos - frame.timestampNanos) / 1_000_000_000.0 else 1.0
                val after = following?.let { translation(frame, it, rowHeight, velocity * afterSeconds) }
                val prediction = if (after != null) (velocity + after.pixels / afterSeconds) * .5 * deltaSeconds
                    else velocity * deltaSeconds
                val recovered = ScrollMotionRecovery().estimate(previousMotion, frame, columns, rowHeight, prediction)
                offset = max(0.0, offset + recovered.displacement)
                if (recovered.imageMatched) collect(frame)
                anchor = frame; anchorOffset = offset
                // A prediction is not a fresh velocity measurement; retain the last real velocity.
                if (recovered.imageMatched) velocity = recovered.displacement / deltaSeconds
                previousMotionFrame = i
                trace?.invoke(i, offset, velocity)
                continue
            }
            val nextOffset = max(0.0, anchorOffset + shift.pixels)
            val change = nextOffset - offset
            if (shift.repeatedRows && abs(change) >= rowHeight * .30) fastAmbiguity = true
            velocity = if (abs(change) < 0.03) 0.0 else velocity * 0.5 + change / deltaSeconds * 0.5
            offset = nextOffset
            previousMotionFrame = i
            trace?.invoke(i, offset, velocity)
            collect(frame)
            // Measure a row against the same reference instead of summing a biased fractional
            // fit on every capture. Expected motion still resolves periodic row candidates.
            if (abs(shift.pixels) >= rowHeight * .40) {
                anchor = frame; anchorOffset = offset
            }
        }
        // Inventories are packed row by row. Only infer holes between observed occupied rows,
        // never a tail after the last visible item. This assumption is exposed as an estimate.
        val inferred = if (allowEstimation) rows.missingRowsBeforeLastOccupied() else 0
        val count = rows.count + inferred.toLong() * columns
        val approximate = allowEstimation && (uncertain > 0 || inferred > 0 || fastAmbiguity)
        val largestGap = frames.zipWithNext { a, b -> if (a.timestampNanos > 0) ((b.timestampNanos - a.timestampNanos) / 1_000_000).coerceAtLeast(0) else 0 }.maxOrNull() ?: 0
        val lowConfidence = approximate && ((fastAmbiguity && largestGap > 28) || uncertain > max(3, frames.size / 20))
        val message = if (lowConfidence) "≈ ESTYMACJA — niska pewność. Przy tym tempie mogło umknąć wiele rzędów. Sprawdź lub popraw liczbę; dokładniejszy odczyt wymaga spokojniejszego przewinięcia."
        else if (approximate) "≈ ESTYMACJA: odtworzono niepewne fragmenty z ruchu i pełnych rzędów. Sprawdź liczbę przed zatwierdzeniem."
        else if (uncertain > 0) "Zgubiono fragment listy. Wróć na górę i zeskanuj ponownie, przewijając trochę wolniej."
        else "Prześledzono ruch listy (${frames.size} klatek). Sprawdź liczbę i ostatni rząd."
        val duration = if (frames.first().timestampNanos > 0) ((frames.last().timestampNanos - frames.first().timestampNanos) / 1_000_000).coerceAtLeast(0) else 0
        return ScrollAnalysis(count, frames.size, uncertain, message, failureFrame, failureReason, duration, largestGap, approximate, inferred, lowConfidence)
    }

    /** Video compression and UI compositing can change a few levels in an otherwise held frame. */
    private fun repeatedCapture(a: GrayScrollFrame, b: GrayScrollFrame): Boolean {
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

    internal fun translation(old: GrayScrollFrame, next: GrayScrollFrame, rowHeight: Int, expected: Double): Motion? {
        // Equal images can also mean one complete repeated row passed the reference.
        // Stationary consecutive captures are handled before matching the older keyframe.
        if (abs(expected) < rowHeight * .5 && old.pixels.contentEquals(next.pixels)) return Motion(0.0, false)
        val limit = old.height / 2
        val scores = DoubleArray(limit * 2 + 1)
        for (shift in -limit..limit) {
            val start = max(0, -shift)
            val end = min(old.height, old.height - shift)
            var sum = 0L; var count = 0
            for (y in start until end step 2) for (x in 2 until old.width - 2 step 3) {
                sum += abs(old.pixel(x, y + shift) - next.pixel(x, y))
                count++
            }
            scores[shift + limit] = if (count > 0) sum.toDouble() / count else Double.POSITIVE_INFINITY
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
        // Fit the minimum between pixel centers; integer rounding otherwise accumulates every frame.
        val correction = if (scores[index] > 0.001 && index > 0 && index < scores.lastIndex) {
            val a = scores[index - 1]; val b = scores[index]; val c = scores[index + 1]
            val denominator = a - 2 * b + c
            if (denominator > 0.001) (0.5 * (a - c) / denominator).coerceIn(-0.5, 0.5) else 0.0
        } else 0.0
        return Motion(shift + correction, competing)
    }

}
