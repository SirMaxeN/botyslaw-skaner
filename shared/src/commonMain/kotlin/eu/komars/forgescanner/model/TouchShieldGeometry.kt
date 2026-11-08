package eu.komars.forgescanner.model

/** Non-overlapping touch-blocking rectangles covering display minus the union of its holes. */
object TouchShieldGeometry {
    /** Keep one bounded strip open while the same held pointer advances. */
    fun gestureCorridor(display: PixelRegion, panel: PixelRegion, current: PixelRegion?,
                        requested: PixelRegion): PixelRegion? {
        val clipped = PixelRegion(maxOf(display.left, requested.left), maxOf(display.top, requested.top),
            minOf(display.right, requested.right), minOf(display.bottom, requested.bottom))
        if (clipped.width <= 0 || clipped.height <= 0) return null
        // Only overlapping strips are accumulated. Unrelated taps never open a connecting area.
        val merged = if (current != null && current.left < clipped.right && current.right > clipped.left &&
            current.top <= clipped.bottom && current.bottom >= clipped.top)
            PixelRegion(minOf(current.left, clipped.left), minOf(current.top, clipped.top),
                maxOf(current.right, clipped.right), maxOf(current.bottom, clipped.bottom)) else clipped
        if (merged.left < panel.right && merged.right > panel.left && merged.top < panel.bottom && merged.bottom > panel.top) return null
        if (merged.width.toLong() * merged.height > display.width.toLong() * display.height / 4) return null
        return merged
    }

    fun partition(display: PixelRegion, holes: List<PixelRegion>): List<PixelRegion> {
        if (display.width <= 0 || display.height <= 0) return emptyList()
        val clipped = holes.mapNotNull {
            val rect = PixelRegion(maxOf(display.left, it.left), maxOf(display.top, it.top),
                minOf(display.right, it.right), minOf(display.bottom, it.bottom))
            rect.takeIf { part -> part.width > 0 && part.height > 0 }
        }
        val xs = (listOf(display.left, display.right) + clipped.flatMap { listOf(it.left, it.right) }).distinct().sorted()
        val ys = (listOf(display.top, display.bottom) + clipped.flatMap { listOf(it.top, it.bottom) }).distinct().sorted()
        val output = mutableListOf<PixelRegion>()
        for ((top, bottom) in ys.zipWithNext()) {
            var runLeft: Int? = null
            fun finishRun(right: Int) {
                val left = runLeft ?: return
                val above = output.indexOfFirst { it.left == left && it.right == right && it.bottom == top }
                if (above >= 0) output[above] = output[above].copy(bottom = bottom)
                else output += PixelRegion(left, top, right, bottom)
                runLeft = null
            }
            for ((left, right) in xs.zipWithNext()) {
                val hole = clipped.any { left >= it.left && right <= it.right && top >= it.top && bottom <= it.bottom }
                if (hole) finishRun(left) else if (runLeft == null) runLeft = left
            }
            finishRun(display.right)
        }
        return output
    }
}
