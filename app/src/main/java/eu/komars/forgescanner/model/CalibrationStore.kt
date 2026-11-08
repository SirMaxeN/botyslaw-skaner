package eu.komars.forgescanner.model

import android.content.Context
import android.graphics.Rect

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
