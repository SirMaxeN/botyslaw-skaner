package eu.komars.forgescanner.vision

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import eu.komars.forgescanner.model.GrayScrollFrame
import eu.komars.forgescanner.model.GridGeometryDetector
import eu.komars.forgescanner.model.PixelRegion
import eu.komars.forgescanner.model.SavedCalibration
import kotlin.math.roundToInt

/** Automatic geometry fit; callers own the screenshot and only supply an approximate viewport. */
class InventoryGridCalibrator {
    fun refine(bitmap: Bitmap, rough: Rect, columns: Int = 5): SavedCalibration {
        val width = minOf(360, bitmap.width)
        val factor = width.toDouble() / bitmap.width
        val height = (bitmap.height * factor).roundToInt()
        val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
        try {
            val colors = IntArray(width * height)
            scaled.getPixels(colors, 0, width, 0, 0, width, height)
            val gray = ByteArray(colors.size) { index ->
                val color = colors[index]
                ((Color.red(color) * 299 + Color.green(color) * 587 + Color.blue(color) * 114) / 1000).toByte()
            }
            val geometry = GridGeometryDetector().detect(GrayScrollFrame(width, height, gray),
                PixelRegion((rough.left * factor).roundToInt(), (rough.top * factor).roundToInt(),
                    (rough.right * factor).roundToInt(), (rough.bottom * factor).roundToInt()), columns)
                ?: throw IllegalStateException("Nie widać ikon listy. Zaznacz z grubsza większy obszar z ikonami.")
            val rect = geometry.region.run { Rect((left / factor).roundToInt(), (top / factor).roundToInt(),
                (right / factor).roundToInt(), (bottom / factor).roundToInt()) }
            return SavedCalibration(rect, columns, (geometry.rowHeight / factor).roundToInt())
        } finally { if (scaled !== bitmap) scaled.recycle() }
    }
}
