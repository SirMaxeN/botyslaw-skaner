package eu.komars.forgescanner.model

/** Physical pixel geometry; WindowManager's vertical origin excludes the top inset. */
object OverlayPlacement {
    fun find(screenHeight: Int, listTop: Int, listBottom: Int, panelHeight: Int,
             topInset: Int, bottomReserve: Int, gap: Int): Int? {
        val above = listTop-panelHeight-gap
        if (above >= topInset) return above-topInset
        val below = listBottom+gap
        if (below+panelHeight <= screenHeight-bottomReserve) return below-topInset
        return null
    }
}
