package eu.komars.forgescanner.vision

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import eu.komars.forgescanner.model.CellSignature
import eu.komars.forgescanner.model.InventoryFrame
import eu.komars.forgescanner.model.InventoryRow
import kotlin.math.floor

data class GridCalibration(
    val bounds: Rect,
    val columns: Int = 5,
    val rowHeightPx: Int = bounds.width() / columns,
    val minimumCellVariance: Double = 100.0
)

/** Prototype thumbnail segmentation. All results require user review; no game-specific template is assumed. */
class InventoryScanner {
    fun captureFrame(bitmap: Bitmap, calibration: GridCalibration): InventoryFrame {
        require(calibration.columns in 1..12 && calibration.rowHeightPx >= 8)
        val roi = Rect(calibration.bounds)
        require(roi.left >= 0 && roi.top >= 0 && roi.right <= bitmap.width && roi.bottom <= bitmap.height && roi.width() >= calibration.columns * 8 && roi.height() >= 8)
        val rowCount = roi.height() / calibration.rowHeightPx
        val warnings = mutableListOf("Automatyczne zliczanie siatki wymaga sprawdzenia. Tekst, blokady lub ozdobne tło mogą zostać uznane za pozycję.")
        if (roi.height() % calibration.rowHeightPx != 0) warnings += "Częściowy dolny rząd pominięty. Dodaj go ręcznie na końcu."
        val rows = (0 until rowCount).map { row ->
            InventoryRow((0 until calibration.columns).map { col ->
                val left = roi.left + col * roi.width() / calibration.columns
                val right = roi.left + (col + 1) * roi.width() / calibration.columns
                signature(bitmap, Rect(left, roi.top + row * calibration.rowHeightPx, right, roi.top + (row + 1) * calibration.rowHeightPx), calibration.minimumCellVariance)
            })
        }
        return InventoryFrame(rows, calibration.columns, warnings)
    }

    private fun signature(bitmap: Bitmap, cell: Rect, minimumVariance: Double): CellSignature {
        val gray = DoubleArray(64)
        var red = 0L; var green = 0L; var blue = 0L
        for (y in 0 until 8) for (x in 0 until 8) {
            // Exclude card edges, labels and selection outlines as far as a generic grid can.
            val px = (cell.left + cell.width() * (0.18 + 0.64 * (x + 0.5) / 8)).toInt().coerceIn(0, bitmap.width - 1)
            val py = (cell.top + cell.height() * (0.14 + 0.64 * (y + 0.5) / 8)).toInt().coerceIn(0, bitmap.height - 1)
            val color = bitmap.getPixel(px, py)
            val r = Color.red(color); val g = Color.green(color); val b = Color.blue(color)
            red += r; green += g; blue += b
            gray[y * 8 + x] = r * 0.299 + g * 0.587 + b * 0.114
        }
        val mean = gray.average()
        val variance = gray.sumOf { (it - mean) * (it - mean) } / gray.size
        var hash = 0L
        gray.forEachIndexed { index, value -> if (value >= mean) hash = hash or (1L shl index) }
        val colorBucket = floor(red / 64.0 / 64).toInt() * 16 + floor(green / 64.0 / 64).toInt() * 4 + floor(blue / 64.0 / 64).toInt()
        return CellSignature(variance >= minimumVariance, hash, colorBucket)
    }
}
