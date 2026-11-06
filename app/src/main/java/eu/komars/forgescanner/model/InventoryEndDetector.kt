package eu.komars.forgescanner.model

import kotlin.math.abs

/** Evidence of a visible list end, not proof that scanning started at the top. */
class InventoryEndDetector {
    enum class Status { NOT_VISIBLE, CHECKING, VISIBLE_END }
    private var previous: GrayScrollFrame? = null
    private var stableSince = -1L
    private var samples = 0

    fun observe(frame: GrayScrollFrame, rowHeight: Int, nowMillis: Long, columns: Int = 5): Status {
        require(rowHeight >= 8 && columns in 1..12)
        val cellWidth = frame.width / columns
        // Rough manual selections can contain the modal's vertical border. Inspect
        // cell interiors, with a wider exclusion at the two outside screen edges.
        val lefts = IntArray(columns) { col -> maxOf(col * frame.width / columns + cellWidth * 12 / 100,
            if (col == 0) frame.width * 6 / 100 else 0) }
        val rights = IntArray(columns) { col -> minOf((col + 1) * frame.width / columns - cellWidth * 12 / 100,
            if (col == columns - 1) frame.width * 94 / 100 else frame.width) }
        var lastContent = -1
        for (y in 0 until frame.height) {
            var dark = 0
            for (col in 0 until columns) for (x in lefts[col] until rights[col]) {
                if (frame.pixel(x, y) < 215) dark++
            }
            if (dark >= maxOf(2, frame.width / 70)) {
                lastContent = y
            }
        }
        val footer = frame.height - lastContent - 1
        val bandTop = lastContent - (rowHeight * .75).toInt()
        val bottomCells = BooleanArray(columns) { col ->
            var dark = 0
            if (bandTop >= 0) for (y in bandTop..lastContent) {
                for (x in lefts[col] until rights[col]) if (frame.pixel(x, y) < 215) dark++
            }
            dark >= maxOf(6, rowHeight * (rights[col] - lefts[col]) / 50)
        }
        val prefix = bottomCells.takeWhile { it }.size
        var visibleIconRows = 0
        if (bandTop >= 0 && prefix in 1 until columns) for (y in bandTop..lastContent) {
            var dark = 0
            for (col in 0 until prefix) for (x in lefts[col] until rights[col]) {
                if (frame.pixel(x, y) < 215) dark++
            }
            if (dark >= 2) visibleIconRows++
        }
        // A partial packed final row is visible even when the selected ROI ends
        // just above the equipped bar (only 2-3 sampled white pixels remain).
        // A clipped next-row tip or a border reaching the bottom is insufficient.
        val partialLastRow = bandTop >= 0 && prefix in 1 until columns &&
            bottomCells.drop(prefix).none { it } && footer >= maxOf(2.0, rowHeight * .06) &&
            visibleIconRows >= rowHeight * .35

        // Short artwork can leave a large ordinary gap before the next full row.
        // Growth or stability of that gap is not proof of an end: pausing the
        // gesture would manufacture the stability being tested. A full row needs
        // one completely empty row pitch below it. Shorter full-row tails are
        // resolved by the driver's repeated fresh blocked gestures, or Finish.
        val longFooter = footer >= rowHeight
        val candidate = lastContent >= 0 && (longFooter || partialLastRow)
        val old = previous
        previous = frame
        if (!candidate) { stableSince = -1; samples = 0; return Status.NOT_VISIBLE }
        var difference = 0L; var pixels = 0
        if (old != null && old.width == frame.width && old.height == frame.height) {
            for (y in 0 until frame.height step 2) for (col in 0 until columns) {
                for (x in lefts[col] until rights[col] step 2) {
                    difference += abs(old.pixel(x, y) - frame.pixel(x, y)); pixels++
                }
            }
        }
        val stable = pixels > 0 && difference.toDouble() / pixels <= 2.0
        if (!stable || stableSince < 0 || nowMillis < stableSince) { stableSince = nowMillis; samples = 1 }
        else samples++
        return if (samples >= 4 && nowMillis - stableSince >= 900) Status.VISIBLE_END else Status.CHECKING
    }
}
