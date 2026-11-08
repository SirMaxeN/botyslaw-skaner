package eu.komars.forgescanner.model

import kotlin.math.abs
import kotlin.math.max

/** One authoritative stream for progress and final results, with six-frame lookahead. */
class ScrollAnalysisSession(private val columns: Int, private val rowHeight: Int,
    private val allowEstimation: Boolean = false,
    private val trace: ((Int, Double, Double) -> Unit)? = null) {
    private val matcher = ScrollSequenceAnalyzer()
    private val rows = InventoryRowAccumulator(columns, rowHeight)
    private val pending = ArrayDeque<GrayScrollFrame>()
    private var firstInput: GrayScrollFrame? = null
    private var lastInput: GrayScrollFrame? = null
    private var previousInput: GrayScrollFrame? = null
    private var motionFrame: GrayScrollFrame? = null
    private var anchor: GrayScrollFrame? = null
    private var offset = 0.0
    private var anchorOffset = 0.0
    private var uncertain = 0
    private var failureFrame: Int? = null
    private var failureReason: String? = null
    private var velocity = 0.0
    private var fastAmbiguity = false
    private var sourceFrames = 0
    private var processedIndex = 0
    private var largestGap = 0L
    private var halted = false
    private var finished = false
    var guidance: ScrollGuidance = ScrollGuidance.SCROLL
        private set
    val count: Long get() = rows.count + if (allowEstimation) rows.missingRowsBeforeLastOccupied().toLong() * columns else 0L

    init { require(columns in 1..12 && rowHeight >= 8) }

    fun offer(frame: GrayScrollFrame) {
        check(!finished)
        require(frame.width >= columns * 4)
        val first = firstInput
        if (first != null) require(frame.width == first.width && frame.height == first.height)
        lastInput?.let { old -> if (old.timestampNanos > 0) largestGap = maxOf(largestGap,
            ((frame.timestampNanos - old.timestampNanos) / 1_000_000).coerceAtLeast(0)) }
        lastInput = frame; sourceFrames++
        if (first == null) {
            firstInput = frame; previousInput = frame; motionFrame = frame; anchor = frame
            collect(frame)
            return
        }
        if (halted) return
        pending.addLast(frame)
        if (pending.size > 6) processNext()
    }

    private fun collect(frame: GrayScrollFrame) { rows.observe(frame, offset) }

    private fun processNext() {
        val frame = pending.removeFirst()
        val i = ++processedIndex
        val prior = requireNotNull(previousInput)
        previousInput = frame
            val gap = frame.timestampNanos > 0 && prior.timestampNanos > 0 &&
                frame.timestampNanos - prior.timestampNanos > 150_000_000L
            // Preserve velocity through an occasional duplicate display buffer. After a sustained
            // 50 ms hold, discard it: extrapolating the old fling across a pause adds identical rows.
            val previousMotion = requireNotNull(motionFrame)
            if (!gap && matcher.repeatedCapture(frame, previousMotion)) {
                if (frame.timestampNanos - previousMotion.timestampNanos > 50_000_000L) {
                    velocity = 0.0; motionFrame = frame
                }
                return
            }
            val deltaSeconds = if (frame.timestampNanos > previousMotion.timestampNanos && previousMotion.timestampNanos > 0)
                (frame.timestampNanos - previousMotion.timestampNanos) / 1_000_000_000.0 else 1.0
            var shift = matcher.translation(requireNotNull(anchor), frame, rowHeight, offset - anchorOffset + velocity * deltaSeconds)
            if (shift?.repeatedRows == true && velocity * deltaSeconds > rowHeight * .20 &&
                shift.pixels - (offset - anchorOffset) in -rowHeight * .15..-.03) {
                // A tiny apparent reversal during a fast forward fling can be a whole-row wrap.
                // Prefer continuity, but expose the decision as an estimate rather than certainty.
                shift = matcher.translation(requireNotNull(anchor), frame, rowHeight,
                    offset - anchorOffset + max(velocity * deltaSeconds, rowHeight * .65)) ?: shift
                fastAmbiguity = true
            }
            if (shift?.repeatedRows == true && abs(shift.pixels - (offset - anchorOffset + velocity * deltaSeconds)) > rowHeight * .30) {
                // At a new fling, preceding velocity can point to the wrong identical row. Look
                // ahead to the next distinct image before selecting the phase-wrap candidate.
                val future = pending.take(6)
                    .firstOrNull { !matcher.repeatedCapture(frame, it) }
                if (future != null) {
                    val nextSeconds = if (future.timestampNanos > frame.timestampNanos && frame.timestampNanos > 0)
                        (future.timestampNanos - frame.timestampNanos) / 1_000_000_000.0 else 1.0
                    val following = matcher.translation(frame, future, rowHeight, velocity * nextSeconds)
                    if (following != null) {
                        val predicted = offset - anchorOffset + (velocity + following.pixels / nextSeconds) * .5 * deltaSeconds
                        shift = matcher.translation(requireNotNull(anchor), frame, rowHeight, predicted) ?: shift
                        fastAmbiguity = true
                    }
                }
            }
            // If an anchor approaches the half-row ambiguity, rebase on the immediately preceding frame.
            if (shift == null && !gap && anchor !== previousMotion) {
                anchor = previousMotion; anchorOffset = offset
                shift = matcher.translation(requireNotNull(anchor), frame, rowHeight, velocity * deltaSeconds)
            }
            if (shift == null || anchorOffset + shift.pixels < -1.0 || gap) {
                uncertain++
                if (failureFrame == null) failureFrame = i
                if (failureReason == null) failureReason = when {
                    gap -> "capture gap ${(frame.timestampNanos - prior.timestampNanos) / 1_000_000} ms"
                    shift == null -> "ambiguous image displacement"
                    else -> "negative document offset"
                }
                if (!allowEstimation) { halted = true; pending.clear(); return }
                // Use motion on both sides of a damaged capture, then reacquire from the next frame.
                // Never silently turn a bridged section into a verified result.
                val following = pending.firstOrNull()
                val afterSeconds = if (following != null && following.timestampNanos > frame.timestampNanos && frame.timestampNanos > 0)
                    (following.timestampNanos - frame.timestampNanos) / 1_000_000_000.0 else 1.0
                val after = following?.let { matcher.translation(frame, it, rowHeight, velocity * afterSeconds) }
                val prediction = if (after != null) (velocity + after.pixels / afterSeconds) * .5 * deltaSeconds
                    else velocity * deltaSeconds
                val recovered = ScrollMotionRecovery().estimate(previousMotion, frame, columns, rowHeight, prediction)
                offset = max(0.0, offset + recovered.displacement)
                if (recovered.imageMatched) collect(frame)
                anchor = frame; anchorOffset = offset
                // A prediction is not a fresh velocity measurement; retain the last real velocity.
                if (recovered.imageMatched) velocity = recovered.displacement / deltaSeconds
                motionFrame = frame
                trace?.invoke(i, offset, velocity)
                return
            }
            val nextOffset = max(0.0, anchorOffset + shift.pixels)
            val change = nextOffset - offset
            guidance = when {
                gap || abs(change) >= rowHeight * .36 -> ScrollGuidance.PAUSE
                abs(change) >= rowHeight * .18 -> ScrollGuidance.SLOW
                else -> ScrollGuidance.SCROLL
            }
            if (shift.repeatedRows && abs(change) >= rowHeight * .30) fastAmbiguity = true
            velocity = if (abs(change) < 0.03) 0.0 else velocity * 0.5 + change / deltaSeconds * 0.5
            offset = nextOffset
            motionFrame = frame
            trace?.invoke(i, offset, velocity)
            collect(frame)
            // Measure a row against the same reference instead of summing a biased fractional
            // fit on every capture. Expected motion still resolves periodic row candidates.
            if (abs(shift.pixels) >= rowHeight * .40) {
                anchor = frame; anchorOffset = offset
            }
    }

    fun finish(): ScrollAnalysis {
        check(firstInput != null)
        if (!finished) {
            while (pending.isNotEmpty() && !halted) processNext()
            finished = true
        }
        return result()
    }

    private fun result(): ScrollAnalysis {
        // Inventories are packed row by row. Only infer holes between observed occupied rows,
        // never a tail after the last visible item. This assumption is exposed as an estimate.
        val inferred = if (allowEstimation) rows.missingRowsBeforeLastOccupied() else 0
        val count = rows.count + inferred.toLong() * columns
        val approximate = allowEstimation && (uncertain > 0 || inferred > 0 || fastAmbiguity)
        val lowConfidence = approximate && ((fastAmbiguity && largestGap > 28) || uncertain > max(3, sourceFrames / 20))
        val message = if (lowConfidence) "≈ ESTYMACJA — niska pewność. Przy tym tempie mogło umknąć wiele rzędów. Sprawdź lub popraw liczbę; dokładniejszy odczyt wymaga spokojniejszego przewinięcia."
        else if (approximate) "≈ ESTYMACJA: odtworzono niepewne fragmenty z ruchu i pełnych rzędów. Sprawdź liczbę przed zatwierdzeniem."
        else if (uncertain > 0) "Zgubiono fragment listy. Wróć na górę i zeskanuj ponownie, przewijając trochę wolniej."
        else "Prześledzono ruch listy (${sourceFrames} klatek). Sprawdź liczbę i ostatni rząd."
        val duration = if (requireNotNull(firstInput).timestampNanos > 0) ((requireNotNull(lastInput).timestampNanos - requireNotNull(firstInput).timestampNanos) / 1_000_000).coerceAtLeast(0) else 0
        return ScrollAnalysis(count, sourceFrames, uncertain, message, failureFrame, failureReason, duration, largestGap, approximate, inferred, lowConfidence)
    }
}
