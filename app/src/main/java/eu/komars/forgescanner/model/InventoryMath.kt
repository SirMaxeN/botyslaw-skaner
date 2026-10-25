package eu.komars.forgescanner.model

object InventoryMath {
    fun count(fullRows: Long, remainder: Long, columns: Int = 5, excludedBase: Int = 0): Long {
        require(columns in 1..12 && fullRows >= 0 && remainder in 0 until columns.toLong() && excludedBase >= 0)
        val total = Math.addExact(Math.multiplyExact(fullRows, columns.toLong()), remainder)
        require(total >= excludedBase) { "Nie można odjąć większej liczby bazowych pozycji niż widoczna suma." }
        return (total - excludedBase).also { require(it <= ScanSession.MAX_VALUE) }
    }
}
