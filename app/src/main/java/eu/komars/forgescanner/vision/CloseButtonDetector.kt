package eu.komars.forgescanner.vision

import android.graphics.Bitmap
import android.graphics.Rect
import eu.komars.forgescanner.model.CloseButtonPixelDetector
import eu.komars.forgescanner.model.PixelRegion
import kotlin.math.roundToInt

/** Proposes a unique red circular white-X control; does not tap or grant navigation permission. */
class CloseButtonDetector {
    fun detect(bitmap: Bitmap, expected: Rect? = null): Rect? {
        if (bitmap.width < 16 || bitmap.height < 16 || bitmap.isRecycled) return null
        val scale = minOf(1.0, 480.0 / bitmap.width, 1200.0 / bitmap.height)
        val width = (bitmap.width * scale).roundToInt().coerceAtLeast(16)
        val height = (bitmap.height * scale).roundToInt().coerceAtLeast(16)
        val reduced = Bitmap.createScaledBitmap(bitmap, width, height, true)
        try {
            val pixels = IntArray(width * height)
            reduced.getPixels(pixels, 0, width, 0, 0, width, height)
            val nearby = expected?.let {
                PixelRegion((it.left * scale).roundToInt(), (it.top * scale).roundToInt(),
                    (it.right * scale).roundToInt(), (it.bottom * scale).roundToInt())
            }
            return CloseButtonPixelDetector().detect(width, height, pixels, nearby)?.run {
                Rect((left / scale).roundToInt().coerceAtLeast(0), (top / scale).roundToInt().coerceAtLeast(0),
                    (right / scale).roundToInt().coerceAtMost(bitmap.width), (bottom / scale).roundToInt().coerceAtMost(bitmap.height))
            }
        } finally { if (reduced !== bitmap) reduced.recycle() }
    }
}
