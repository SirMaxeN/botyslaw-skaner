package eu.komars.forgescanner.model

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min

/** Counts occupied cells in document coordinates, without rounding a partial scroll to a row. */
class InventoryRowAccumulator(private val columns: Int, private val rowHeight: Int) {
    private data class Observation(val cells: BooleanArray, val margin: Double)
    private val rows = mutableMapOf<Int, Observation>()

    var count: Long = 0
        private set

    init { require(columns in 1..12 && rowHeight >= 8) }

    val lastOccupiedRow: Int
        get() = rows.filterValues { observation -> observation.cells.any { it } }.keys.maxOrNull() ?: -1

    fun missingRowsBeforeLastOccupied(): Int = (0 until lastOccupiedRow).count { it !in rows }

    fun observe(frame: GrayScrollFrame, offset: Double) {
        require(offset.isFinite() && offset >= 0 && frame.width >= columns * 4)
        // The icon ends at 80% of its row. Its bottom label/margin may safely be clipped.
        val from = ceil((offset - .75) / rowHeight).toInt().coerceAtLeast(0)
        val until = floor((offset + frame.height + rowHeight * .20) / rowHeight).toInt()
        for (row in from until until) {
            val localY = (row * rowHeight - offset).toInt().coerceAtLeast(0)
            if (localY + ceil(rowHeight * .80).toInt() > frame.height) continue
            val margin = min(localY, frame.height - localY - rowHeight).toDouble()
            val old = rows[row]
            if (old != null && margin < old.margin) continue
            val cells = BooleanArray(columns) { column ->
                occupied(frame, column * frame.width / columns,
                    (column + 1) * frame.width / columns, localY)
            }
            count += cells.count { it }.toLong() - (old?.cells?.count { it } ?: 0)
            rows[row] = Observation(cells, margin)
        }
    }

    private fun occupied(frame: GrayScrollFrame, left: Int, right: Int, top: Int): Boolean {
        var sum = 0.0
        var squares = 0.0
        var samples = 0
        for (y in 0 until 12) for (x in 0 until 12) {
            val px = (left + (right - left) * (.18 + .64 * (x + .5) / 12))
                .toInt().coerceIn(0, frame.width - 1)
            val py = (top + rowHeight * (.14 + .64 * (y + .5) / 12))
                .toInt().coerceIn(0, frame.height - 1)
            val value = frame.pixel(px, py).toDouble()
            sum += value
            squares += value * value
            samples++
        }
        return squares / samples - (sum / samples) * (sum / samples) >= 100.0
    }
}
