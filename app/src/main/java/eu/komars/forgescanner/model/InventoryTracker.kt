package eu.komars.forgescanner.model

data class CellSignature(val occupied: Boolean, val perceptualHash: Long, val colorBucket: Int = 0)
data class InventoryRow(val cells: List<CellSignature>) {
    val occupiedCount: Int get() = cells.count { it.occupied }
}
data class InventoryFrame(val rows: List<InventoryRow>, val columns: Int, val warnings: List<String> = emptyList())
data class InventoryCountResult(
    val totalObservedCount: Long,
    val visibleCount: Int,
    val addedCount: Int,
    val overlapRows: Int?,
    val needsReview: Boolean,
    val message: String
)

/** Sequence overlap, not global image deduplication: identical creatures are separate slots. */
class InventoryTracker(private val columns: Int = 5) {
    private var previous: InventoryFrame? = null
    var count: Long = 0
        private set

    init { require(columns in 1..12) }

    fun reset() { previous = null; count = 0 }

    fun append(frame: InventoryFrame): InventoryCountResult {
        require(frame.columns == columns && frame.rows.all { it.cells.size == columns })
        val visible = frame.rows.sumOf { it.occupiedCount }
        val old = previous
        if (frame.rows.isEmpty()) return result(visible, 0, null, "Brak pełnych rzędów. Popraw zaznaczenie siatki.")
        if (old == null) {
            previous = frame
            count = visible.toLong()
            return result(visible, visible, 0, "Pierwszy widok: $visible. To wstępne zliczenie. Przewiń w dół, zachowując wspólny rząd.")
        }
        val possible = (1..minOf(old.rows.size, frame.rows.size)).filter { overlap ->
            old.rows.takeLast(overlap).zip(frame.rows.take(overlap)).all { (a, b) -> rowsMatch(a, b) }
        }
        val allSame = old.rows.size == frame.rows.size && old.rows.zip(frame.rows).all { (a, b) -> rowsMatch(a, b) }
        if (allSame) {
            // A stationary view and a scrolled view of identical rows are indistinguishable.
            return result(visible, 0, frame.rows.size, "Widok wygląda tak samo. Nie dodano pozycji. Przy identycznych zwierzakach policz nowe rzędy ręcznie.")
        }
        if (possible.size != 1) {
            return result(visible, 0, null, if (possible.isEmpty()) "Nie znaleziono wspólnego rzędu. Cofnij trochę przewijanie albo dodaj rzędy ręcznie." else "Kilka rzędów wygląda identycznie. Nie zgaduję, ile nowych pozycji doszło; dodaj je ręcznie.")
        }
        val overlap = possible.single()
        val added = frame.rows.drop(overlap).sumOf { it.occupiedCount }
        count = Math.addExact(count, added.toLong())
        previous = frame
        return result(visible, added, overlap, "Dodano $added; wstępnie razem $count. Sprawdź puste i częściowe rzędy.")
    }

    /** Call only after the user explicitly counts the newly revealed rows/items. */
    fun addManually(items: Int, currentFrame: InventoryFrame? = null): Long {
        require(items >= 0)
        if (currentFrame != null) {
            require(currentFrame.columns == columns && currentFrame.rows.all { it.cells.size == columns })
        }
        val nextCount = Math.addExact(count, items.toLong())
        require(nextCount <= ScanSession.MAX_VALUE)
        count = nextCount
        if (currentFrame != null) {
            previous = currentFrame
        }
        return count
    }

    private fun result(visible: Int, added: Int, overlap: Int?, message: String) =
        InventoryCountResult(count, visible, added, overlap, true, message)

    private fun rowsMatch(a: InventoryRow, b: InventoryRow): Boolean = a.cells.zip(b.cells).all { (x, y) ->
        x.occupied == y.occupied && (!x.occupied || (java.lang.Long.bitCount(x.perceptualHash xor y.perceptualHash) <= 7 && x.colorBucket == y.colorBucket))
    }
}
