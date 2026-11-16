package eu.komars.forgescanner.model

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The automatic list scroll holds the finger still before every check, so the game list rests where the finger
 * left it: content moves ~1:1 with the finger, minus a small "slop" each time the finger is pressed again.
 * Image matching knows the fractional row phase exactly but can slip by whole rows on identical eggs; the finger
 * knows the whole rows. [fuse] keeps the image where it was unambiguous and takes the row count from the finger
 * where it was not.
 */
object FingerOdometer {
    /** One stretch between two rest moments, in sampled pixels. */
    data class Interval(val fingerPx: Double, val presses: Int, val imagePx: Double, val ambiguous: Boolean, val blocked: Boolean,
        /** Grid phase (where the row gaps fall, 0..row) on the still frames at both ends, when measurable. */
        val phaseFrom: Double? = null, val phaseTo: Double? = null)

    data class Fusion(
        /** Whole rows to add to the image count (negative: the image counted rows that never passed). */
        val rowCorrection: Int,
        val ratio: Double,
        val slopPx: Double,
        val intervals: Int,
        val corrected: Int,
        val unsure: Int,
        val fingerRows: Double,
        val imageRows: Double,
        /** Every stretch as finger/image rows; "~" ambiguous, "|" pushed against the end, "+n" presses. */
        val stretches: String = "",
        /** Fast mode: finger for whole rows + grid phase of still frames for the fraction; null without phases. */
        val phaseCorrection: Int? = null,
        val phaseRows: Double? = null,
        /** Fast mode: exact pixels to add to the image offset at the last rest (unrounded [phaseCorrection]). */
        val phaseErrorPx: Double? = null
    ) {
        /** The correction used for the count: still-frame grid phase when measured, else finger alone. */
        val correction: Int get() = phaseCorrection ?: rowCorrection
        fun describe() = "palec ${fmt(fingerRows)} rz., obraz ${fmt(imageRows)} rz., korekta ${if (rowCorrection > 0) "+" else ""}$rowCorrection rz., " +
            "postoje ${phaseRows?.let { fmt(it) } ?: "-"} rz. -> ${phaseCorrection ?: "-"} " +
            "(odcinki $intervals, poprawione $corrected, niepewne $unsure, przelicznik ${fmt(ratio)}, luz ${fmt(slopPx)} px) [$stretches]"
        private fun fmt(v: Double) = ((v * 100).roundToInt() / 100.0).toString()
    }

    fun fuse(intervals: List<Interval>, rowHeight: Double): Fusion {
        require(rowHeight > 0)
        // A clear image that saw (almost) no motion while the finger travelled: the list was already at its end
        // and the end was not detected. The finger is no evidence there (diagnostics: mounts 336 vs real ~91).
        fun stuck(it: Interval) = !it.ambiguous && it.fingerPx > rowHeight * .5 && it.imagePx < it.fingerPx * .2
        val usable = intervals.filter { !it.blocked && it.fingerPx > rowHeight * .2 && !stuck(it) }
        val clean = usable.filter { !it.ambiguous }
        val withoutPress = clean.filter { it.presses == 0 }
        val ratio = median(withoutPress.map { it.imagePx / it.fingerPx })?.takeIf { it in .8..1.25 } ?: 1.0
        val slop = median(clean.filter { it.presses > 0 }.map { (ratio * it.fingerPx - it.imagePx) / (ratio * it.presses) })
            ?.coerceIn(0.0, rowHeight * .5) ?: 0.0
        var corrected = 0; var unsure = 0
        var fingerTotal = 0.0; var imageTotal = 0.0; var ambiguousError = 0.0
        for (interval in usable) {
            val expected = ratio * (interval.fingerPx - slop * interval.presses)
            fingerTotal += expected; imageTotal += interval.imagePx
            if (!interval.ambiguous) continue
            val error = expected - interval.imagePx
            // Summed before rounding: a lost image phase spreads fractions over several stretches.
            ambiguousError += error
            val rows = (error / rowHeight).roundToInt()
            if (rows != 0) corrected++
            if (abs(error - rows * rowHeight) > rowHeight * .3) unsure++
        }
        val correction = (ambiguousError / rowHeight).roundToInt()
        fun r(v: Double) = ((v / rowHeight) * 100).roundToInt() / 100.0
        val stretches = intervals.joinToString(" ") { "${r(it.fingerPx)}/${r(it.imagePx)}${if (it.ambiguous) "~" else ""}${if (it.blocked) "|" else ""}${if (it.presses > 0) "+${it.presses}" else ""}" }
        // Fast mode: the finger is accurate to well under half a row per stretch, the still frame's grid phase is exact
        // modulo a row, so together they give each stretch's travel without following the motion frame by frame.
        val phased = usable.filter { it.phaseFrom != null && it.phaseTo != null }
        // The press slop seen by the still frames themselves: finger travel minus the grid shift, wrapped to
        // +-half a row, per press. Independent of the image tracker, which may be wrong on every stretch.
        fun wrap(v: Double) = v - rowHeight * kotlin.math.round(v / rowHeight)
        // Two presses at ~0.3 row each already exceed the half-row a single stretch can resolve, so one slop is
        // fitted to all pressed stretches at once: the value whose predicted grid shift agrees with most still frames.
        val pressed = phased.filter { it.presses > 0 }
        val phaseSlop = if (pressed.isEmpty()) slop else {
            var best = slop; var bestScore = Double.NEGATIVE_INFINITY
            var candidate = 0.0
            while (candidate <= rowHeight * .45) {
                val score = pressed.sumOf { kotlin.math.cos(2 * kotlin.math.PI *
                    (ratio * (it.fingerPx - candidate * it.presses) - (it.phaseFrom!! - it.phaseTo!!)) / rowHeight) }
                if (score > bestScore + 1e-9) { bestScore = score; best = candidate }
                candidate += rowHeight / 128
            }
            best
        }
        var phaseTotal = 0.0; var phaseError = 0.0
        for (interval in phased) {
            val expected = ratio * (interval.fingerPx - phaseSlop * interval.presses)
            val shift = interval.phaseFrom!! - interval.phaseTo!!
            // A clear image keeps its whole rows (the list may have hit the end mid-stroke); only its fraction snaps
            // to the grid. Identical rows take the whole rows from the finger.
            val measured = if (!interval.ambiguous) interval.imagePx + wrap(shift - interval.imagePx)
                else expected + wrap(shift - expected)
            phaseTotal += measured; phaseError += measured - interval.imagePx
        }
        val phaseValid = phased.size >= 2 && phased.size * 2 >= usable.size
        // Stretches without a usable finger (pushed against the end, or barely moved): the image travel is
        // still snapped to the still-frame grid phase, which fixes its fraction and any error under half a row.
        if (phaseValid) for (interval in intervals) {
            if (interval in usable || interval.phaseFrom == null || interval.phaseTo == null) continue
            phaseError += wrap((interval.phaseFrom - interval.phaseTo) - interval.imagePx)
        }
        return Fusion(correction, ratio, slop, usable.size, corrected, unsure, fingerTotal / rowHeight, imageTotal / rowHeight, stretches,
            if (phaseValid) (phaseError / rowHeight).roundToInt() else null, if (phaseValid) phaseTotal / rowHeight else null,
            if (phaseValid) phaseError else null)
    }

    /**
     * Where the gaps between rows fall in a still frame (0 until [rowHeight]): the brightest comb of horizontal lines
     * one row apart. Null when the frame has no clear row structure.
     */
    fun gridPhase(frame: GrayScrollFrame, rowHeight: Int): Double? {
        if (frame.height < rowHeight * 2 || rowHeight < 8) return null
        val from = frame.width / 10; val until = frame.width - from
        val profile = DoubleArray(frame.height) { y ->
            var sum = 0; var n = 0
            var x = from; while (x < until) { sum += frame.pixel(x, y); n++; x += 2 }
            sum.toDouble() / n
        }
        val score = DoubleArray(rowHeight) { p ->
            var sum = 0.0; var n = 0; var y = p
            while (y < frame.height) { sum += profile[y]; n++; y += rowHeight }
            sum / n
        }
        val high = score.maxOrNull() ?: return null; val low = score.minOrNull() ?: return null
        if (high - low < 3.0) return null
        // Circular centre of the bright band: a wide white gap has a plateau, whose middle is stable frame to frame.
        val threshold = low + (high - low) * .6
        var sx = 0.0; var sy = 0.0
        for (p in score.indices) {
            val weight = score[p] - threshold
            if (weight <= 0) continue
            val angle = 2 * kotlin.math.PI * p / rowHeight
            sx += weight * kotlin.math.cos(angle); sy += weight * kotlin.math.sin(angle)
        }
        if (sx == 0.0 && sy == 0.0) return null
        val angle = kotlin.math.atan2(sy, sx)
        return ((angle / (2 * kotlin.math.PI) * rowHeight) % rowHeight + rowHeight) % rowHeight
    }

    private fun median(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        return if (sorted.size % 2 == 1) sorted[sorted.size / 2] else (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2
    }
}
