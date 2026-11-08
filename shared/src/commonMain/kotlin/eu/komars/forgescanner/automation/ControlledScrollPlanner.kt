package eu.komars.forgescanner.automation

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.round

/** Plans logical passes only. InventoryRecording remains the sole source of counts. */
class ControlledScrollPlanner(
    private val rowHeightPx: Double,
    viewportHeightPx: Double = rowHeightPx * 4.2,
    maximumGesturePx: Double = rowHeightPx * 3.8
) {
    enum class Kind { DRAG, WAIT, END_VISIBLE, CHECK_END, FAILED }
    /** Distance and duration cover a whole logical pass, possibly multiple physical strokes. */
    data class Decision(val kind: Kind, val distancePx: Double = 0.0, val durationMillis: Long = 0L, val message: String = "")
    private var sourceTimestamp = 0L
    private var initial = true
    private var blockedGestures = 0
    private var completedPasses = 0
    private var lastCompletedGestureId = 0L
    private val motionIntervals = ArrayDeque<Long>()
    private var lastNativeTimestamp = 0L
    private var lastMotionTimestamp = 0L
    private var motionActive = false

    /** The driver splits logical passes to this ROI/screen limit, and to <=1000ms per stroke. */
    val maximumSegmentDistancePx: Double

    init {
        require(rowHeightPx.isFinite() && rowHeightPx >= 8)
        require(viewportHeightPx.isFinite() && viewportHeightPx >= rowHeightPx)
        require(maximumGesturePx.isFinite() && maximumGesturePx >= rowHeightPx * .15)
        maximumSegmentDistancePx = min(rowHeightPx * 3.65, min(viewportHeightPx - rowHeightPx * .45, maximumGesturePx))
    }

    /** Bracket each dispatched physical stroke, excluding release/reposition/idle time. */
    fun beginMotion() {
        motionActive = true
        lastMotionTimestamp = 0L
    }

    fun endMotion() {
        motionActive = false
        lastMotionTimestamp = 0L
    }

    /** Called only for distinct native source frames; cached polls supply no cadence evidence. */
    fun observeSourceFrame(timestampNanos: Long) {
        if (timestampNanos <= lastNativeTimestamp) return
        lastNativeTimestamp = timestampNanos
        if (!motionActive) return
        val previous = lastMotionTimestamp
        lastMotionTimestamp = timestampNanos
        if (previous <= 0) return
        val interval = timestampNanos - previous
        motionIntervals.addLast(interval)
        while (motionIntervals.size > 24) motionIntervals.removeFirst()
    }

    /**
     * Cached capture polls cannot authorize another pass or prove a blocked
     * bottom. Wall-clock footer checks and manual Finish remain independent.
     * The displacement argument is a small signed image phase/movement signal,
     * never an assumed stroke distance or a source of inventory counts.
     */
    fun next(sourceTimestampNanos: Long, displacementPx: Double?, gestureCompleted: Boolean = true,
             endVisible: Boolean = false, completedGestureId: Long = 0): Decision {
        if (sourceTimestampNanos <= 0 || sourceTimestampNanos < sourceTimestamp || completedGestureId < lastCompletedGestureId) {
            return Decision(Kind.FAILED, message = "Nieprawidłowa kolejność klatek lub gestów. Zatrzymano przewijanie.")
        }
        if (endVisible) return Decision(Kind.END_VISIBLE, message = "Widoczny koniec listy.")
        if (sourceTimestampNanos == sourceTimestamp) return Decision(Kind.WAIT, message = "Czekam na nową klatkę listy.")
        sourceTimestamp = sourceTimestampNanos
        val newlyCompleted = completedGestureId > lastCompletedGestureId
        if (!initial) {
            if (!gestureCompleted || motionActive) return Decision(Kind.WAIT, message = "Przewijam i obserwuję listę.")
            if (displacementPx != null && (!displacementPx.isFinite() ||
                    abs(displacementPx) > rowHeightPx * .75 || displacementPx < -rowHeightPx * .04)) {
                return Decision(Kind.FAILED, message = "Nieprawidłowe przesunięcie listy. Zatrzymano skanowanie.")
            }
            if (newlyCompleted) blockedGestures = if (displacementPx != null && abs(displacementPx) < rowHeightPx * .015) blockedGestures + 1 else 0
            if (blockedGestures >= 3) return Decision(Kind.CHECK_END,
                message = "Trzy próby nie przesunęły listy. Podsumowuję ostatni rząd.")
        }
        initial = false
        lastCompletedGestureId = completedGestureId
        if (++completedPasses > 1500) return Decision(Kind.FAILED, message = "Osiągnięto limit przewijania. Sprawdź ustawienia.")
        // Keep long travel fixed. Slower genuine motion capture increases duration,
        // never causes endless tiny strokes. Idle gaps are absent from this sample.
        val longGaps = motionIntervals.filter { it > LONG_MOTION_INTERVAL_NANOS }.sorted()
        // One delayed callback must not poison every later pass. Repeated long
        // in-motion gaps do slow the whole pass; release/idle gaps never enter here.
        val eligible = if (longGaps.size < 2) motionIntervals.filter { it <= LONG_MOTION_INTERVAL_NANOS }
            else motionIntervals.toList()
        val sorted = eligible.sorted()
        val percentile = if (sorted.isEmpty()) DEFAULT_INTERVAL_NANOS
            else sorted[(ceil(sorted.size * .8).toInt() - 1).coerceAtLeast(0)]
        val recent = eligible.takeLast(3).sorted()
        val recentMedian = recent.getOrNull(recent.size / 2) ?: DEFAULT_INTERVAL_NANOS
        val repeatedGap = if (longGaps.size >= 2) longGaps[(longGaps.size - 1) / 2] else 0L
        val measured = maxOf(percentile, recentMedian, repeatedGap)
        val interval = if (eligible.size < 6) maxOf(DEFAULT_INTERVAL_NANOS, measured) else measured
        var normalRows = min(7.15, 2 * maximumSegmentDistancePx / rowHeightPx - .05)
        // Keep two long physical strokes on shorter viewports too, with a visible
        // fractional-row phase for the blocked-bottom check.
        if (abs(normalRows - round(normalRows)) < .15) normalRows = round(normalRows) - .17
        val rows = if (blockedGestures > 0) min(1.15, normalRows) else normalRows
        val minimumDuration = if (blockedGestures > 0) 250L else 600L
        val duration = maxOf(minimumDuration, ceil(rows * interval / (ROWS_PER_FRAME * 1_000_000.0)).toLong())
        if (duration > MAXIMUM_PASS_MILLIS) return Decision(Kind.FAILED,
            message = "Przechwytywanie nie nadąża za przewijaniem. Wróć na górę i ponów odczyt.")
        return Decision(Kind.DRAG, rowHeightPx * rows, duration, "Przewijam i liczę listę.")
    }

    companion object {
        private const val ROWS_PER_FRAME = .28
        private const val DEFAULT_INTERVAL_NANOS = 33_333_334L
        private const val LONG_MOTION_INTERVAL_NANOS = 100_000_000L
        private const val MAXIMUM_PASS_MILLIS = 4000L
    }
}

/** A cached image mid-list must not be mistaken for three blocked end gestures. */
class InventoryCaptureFreshnessGate(private val maximumWaitMillis: Long = 750) {
    enum class Status { FRESH, WAIT, STALLED }
    private var lastTimestamp = 0L
    private var waitingSince = -1L
    private var lastClock = -1L

    init { require(maximumWaitMillis > 0) }

    fun observe(timestampNanos: Long, nowMillis: Long): Status {
        if (nowMillis < 0 || nowMillis < lastClock || timestampNanos <= 0 || timestampNanos < lastTimestamp) return Status.STALLED
        lastClock = nowMillis
        if (timestampNanos > lastTimestamp) {
            lastTimestamp = timestampNanos
            waitingSince = -1
            return Status.FRESH
        }
        if (waitingSince < 0) waitingSince = nowMillis
        return if (nowMillis - waitingSince >= maximumWaitMillis) Status.STALLED else Status.WAIT
    }
}
