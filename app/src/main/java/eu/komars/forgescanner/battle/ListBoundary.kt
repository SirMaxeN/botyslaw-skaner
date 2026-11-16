package eu.komars.forgescanner.battle

import android.graphics.Bitmap
import android.graphics.Rect

internal object ListBoundary {
    /** Require a whole row of empty space next to visible content. Inspect while the gesture is held. */
    fun emptyRow(image: Bitmap, area: Rect, rowHeight: Int, darkBackground: Boolean = false): Boolean {
        if (rowHeight < 8 || area.height() < rowHeight * 2 ||
            !Rect(0, 0, image.width, image.height).contains(area)) return false
        val left = area.left + area.width() / 12
        val right = area.right - area.width() / 12
        val boundary = area.bottom - rowHeight
        fun ink(y: Int): Int = (left until right step 4).count { x ->
            val pixel = image.getPixel(x, y)
            val darkest = minOf(pixel ushr 16 and 255, pixel ushr 8 and 255, pixel and 255)
            if (darkBackground) darkest > 155 else darkest < 212
        }
        return (boundary until area.bottom step 3).all { ink(it) <= 1 } &&
            (boundary - rowHeight until boundary step 3).count { ink(it) >= 3 } >= rowHeight / 10
    }
}
