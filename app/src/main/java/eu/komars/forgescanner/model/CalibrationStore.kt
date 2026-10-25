package eu.komars.forgescanner.model

import android.content.Context
import android.graphics.Rect
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
data class SavedCalibration(val region: Rect, val columns: Int, val rowHeightPx: Int)

/** Device-local settings only. Screen captures and scanned quantities never enter preferences. */
class CalibrationStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("scan_calibrations_v1", Context.MODE_PRIVATE)

    fun save(target: ScanTarget, region: Rect, frameWidth: Int, frameHeight: Int, columns: Int = 5,
             rowHeightPx: Int = (region.width() / columns.coerceAtLeast(1)).coerceAtLeast(2)) {
        val profile = CalibrationProfile.fromPixels(
            PixelRegion(region.left, region.top, region.right, region.bottom), frameWidth, frameHeight, columns, rowHeightPx
        )
        val key = target.name
        preferences.edit()
            .putFloat("$key.left", profile.left).putFloat("$key.top", profile.top)
            .putFloat("$key.right", profile.right).putFloat("$key.bottom", profile.bottom)
            .putInt("$key.width", frameWidth).putInt("$key.height", frameHeight)
            .putInt("$key.columns", columns).putFloat("$key.row", profile.rowHeightFraction)
            .apply()
    }

    fun get(target: ScanTarget, frameWidth: Int, frameHeight: Int): SavedCalibration? {
        val projected = profile(target)?.project(frameWidth, frameHeight) ?: return null
        return SavedCalibration(
            projected.region.run { Rect(left, top, right, bottom) }, projected.columns, projected.rowHeightPx
        )
    }

    fun has(target: ScanTarget): Boolean = profile(target)?.valid == true

    fun clear(target: ScanTarget) {
        val edit = preferences.edit()
        for (suffix in listOf("left", "top", "right", "bottom", "width", "height", "columns", "row")) {
            edit.remove("${target.name}.$suffix")
        }
        edit.apply()
    }

    fun clear() = preferences.edit().clear().apply()
    fun clearAll() = clear()

    private fun profile(target: ScanTarget): CalibrationProfile? {
        val key = target.name
        if (!preferences.contains("$key.left")) return null
        return try {
            CalibrationProfile(
                preferences.getFloat("$key.left", Float.NaN), preferences.getFloat("$key.top", Float.NaN),
                preferences.getFloat("$key.right", Float.NaN), preferences.getFloat("$key.bottom", Float.NaN),
                preferences.getInt("$key.width", 0), preferences.getInt("$key.height", 0),
                preferences.getInt("$key.columns", 5), preferences.getFloat("$key.row", Float.NaN)
            ).takeIf { it.valid }
        } catch (_: ClassCastException) {
            null // Treat stale/malformed preferences as unset rather than crashing the overlay.
        }
    }
}
