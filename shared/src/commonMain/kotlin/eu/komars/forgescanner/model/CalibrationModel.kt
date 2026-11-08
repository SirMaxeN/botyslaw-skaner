package eu.komars.forgescanner.model

import kotlin.math.abs
import kotlin.math.roundToInt

data class PixelRegion(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/** Pure geometry; no captured image or account/resource data is persisted. */
data class CalibrationProfile(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val columns: Int,
    val rowHeightFraction: Float
) {
    val valid: Boolean get() = listOf(left, top, right, bottom, rowHeightFraction).all { it.isFinite() } &&
        sourceWidth > 0 && sourceHeight > 0 && left >= 0f && top >= 0f && right <= 1f && bottom <= 1f &&
        right > left && bottom > top && columns in 1..12 && rowHeightFraction > 0f && rowHeightFraction <= 1f

    fun project(frameWidth: Int, frameHeight: Int): ProjectedCalibration? {
        if (!valid || frameWidth <= 0 || frameHeight <= 0) return null
        val sourceAspect = sourceWidth.toDouble() / sourceHeight
        val frameAspect = frameWidth.toDouble() / frameHeight
        if (abs(frameAspect / sourceAspect - 1.0) > MAX_ASPECT_CHANGE) return null
        val region = PixelRegion(
            (left * frameWidth).roundToInt().coerceIn(0, frameWidth),
            (top * frameHeight).roundToInt().coerceIn(0, frameHeight),
            (right * frameWidth).roundToInt().coerceIn(0, frameWidth),
            (bottom * frameHeight).roundToInt().coerceIn(0, frameHeight)
        )
        if (region.width < 2 || region.height < 2) return null
        val rowHeight = (rowHeightFraction * frameHeight).roundToInt()
        if (rowHeight < 2 || rowHeight > frameHeight) return null
        return ProjectedCalibration(region, columns, rowHeight)
    }

    companion object {
        const val MAX_ASPECT_CHANGE = 0.08

        fun fromPixels(region: PixelRegion, frameWidth: Int, frameHeight: Int, columns: Int = 5,
                       rowHeightPx: Int = (region.width / columns.coerceAtLeast(1)).coerceAtLeast(2)): CalibrationProfile {
            require(frameWidth > 0 && frameHeight > 0)
            require(region.left >= 0 && region.top >= 0 && region.right <= frameWidth && region.bottom <= frameHeight)
            require(region.width >= 2 && region.height >= 2)
            require(columns in 1..12 && rowHeightPx in 2..frameHeight)
            return CalibrationProfile(
                region.left.toFloat() / frameWidth, region.top.toFloat() / frameHeight,
                region.right.toFloat() / frameWidth, region.bottom.toFloat() / frameHeight,
                frameWidth, frameHeight, columns, rowHeightPx.toFloat() / frameHeight
            )
        }
    }
}

data class ProjectedCalibration(val region: PixelRegion, val columns: Int, val rowHeightPx: Int)
