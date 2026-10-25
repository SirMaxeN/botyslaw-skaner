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
        var lastContent = -1
        for (y in 0 until frame.height) {
            var dark = 0
            for (x in 2 until frame.width - 2) if (frame.pixel(x, y) < 215) dark++
            if (dark >= maxOf(2, frame.width / 70)) lastContent = y
        }
        // Ordinary inter-row whitespace is not enough. Require a long empty footer in the grid ROI.
        val footer = frame.height - lastContent - 1
        val bandTop = lastContent - (rowHeight * 0.75).toInt()
        // A left-packed, incomplete final row is another boundary signal even with a short footer.
        // Require bottom whitespace or a substantial visible icon body; a few clipped pixels are insufficient.
        val bottomCells = BooleanArray(columns) { col ->
            var dark = 0
            if (bandTop >= 0) for (y in bandTop..lastContent) {
                for (x in col * frame.width / columns until (col + 1) * frame.width / columns) {
                    if (frame.pixel(x, y) < 215) dark++
                }
            }
            dark >= maxOf(6, rowHeight * (frame.width / columns) / 50)
        }
        val prefix = bottomCells.takeWhile { it }.size
        var visibleIconRows = 0
        if (bandTop >= 0 && prefix in 1 until columns) for (y in bandTop..lastContent) {
            var dark = 0
            for (x in 0 until prefix * frame.width / columns) if (frame.pixel(x, y) < 215) dark++
            if (dark >= 2) visibleIconRows++
        }
        val partialLastRow = bandTop >= 0 && prefix in 1 until columns && bottomCells.drop(prefix).none { it } &&
            (footer >= maxOf(3.0, rowHeight * 0.08) || visibleIconRows >= rowHeight * 0.55)
        val candidate = lastContent >= 0 && (footer >= rowHeight * 0.70 || partialLastRow)
        val old = previous
        previous = frame
        if (!candidate) { stableSince = -1; samples = 0; return Status.NOT_VISIBLE }
        var difference = 0L; var pixels = 0
        if (old != null && old.width == frame.width && old.height == frame.height) {
            for (y in 0 until frame.height step 2) for (x in 0 until frame.width step 2) {
                difference += abs(old.pixel(x, y) - frame.pixel(x, y)); pixels++
            }
        }
        val stable = pixels > 0 && difference.toDouble() / pixels <= 2.0
        if (!stable || stableSince < 0 || nowMillis < stableSince) { stableSince = nowMillis; samples = 1 }
        else samples++
        return if (samples >= 4 && nowMillis - stableSince >= 900) Status.VISIBLE_END else Status.CHECKING
    }
}
