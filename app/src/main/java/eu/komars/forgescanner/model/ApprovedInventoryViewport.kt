package eu.komars.forgescanner.model

import kotlin.math.abs
import kotlin.math.roundToInt

/** A confirmed structural viewport keeps its empty space even when only one icon exists. */
object ApprovedInventoryViewport {
    fun matches(selection: PixelRegion, proposal: PixelRegion, tolerance: Int): Boolean =
        abs(selection.left - proposal.left) <= tolerance && abs(selection.top - proposal.top) <= tolerance &&
            abs(selection.right - proposal.right) <= tolerance && abs(selection.bottom - proposal.bottom) <= tolerance

    fun fit(frame: GrayScrollFrame, viewport: PixelRegion, columns: Int): GridGeometryDetector.Geometry? {
        require(viewport.left >= 0 && viewport.top >= 0 && viewport.right <= frame.width && viewport.bottom <= frame.height)
        if (viewport.width < 16 || viewport.height < 16) return null
        val crop = GrayScrollFrame(viewport.width, viewport.height, ByteArray(viewport.width * viewport.height) { index ->
            frame.pixels[(viewport.top + index / viewport.width) * frame.width + viewport.left + index % viewport.width]
        })
        val fitted = GridGeometryDetector().detect(crop, PixelRegion(0, 0, crop.width, crop.height), columns) ?: return null
        // Do not discard the first row because artwork has a large transparent upper margin.
        val top = fitted.region.top.coerceIn(0, (fitted.rowHeight * .25).roundToInt())
        return GridGeometryDetector.Geometry(PixelRegion(viewport.left + fitted.region.left, viewport.top + top,
            viewport.left + fitted.region.right, viewport.bottom), columns, fitted.rowHeight, fitted.icons)
    }
}
