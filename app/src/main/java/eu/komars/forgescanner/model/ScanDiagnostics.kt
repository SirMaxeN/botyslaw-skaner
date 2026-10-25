package eu.komars.forgescanner.model

import android.content.Context

/** Local numeric capture diagnostics only. No image, pairing code or credential is stored here. */
class ScanDiagnostics(context: Context) {
    private val settings = context.applicationContext.getSharedPreferences("scan_diagnostics_v1", Context.MODE_PRIVATE)
    fun save(target: ScanTarget, geometry: SavedCalibration, result: ScrollAnalysis) {
        val prefix = target.name
        settings.edit().putLong("$prefix.at", System.currentTimeMillis())
            .putInt("$prefix.frames", result.frames).putLong("$prefix.durationMs", result.captureDurationMillis)
            .putLong("$prefix.maxGapMs", result.largestGapMillis).putInt("$prefix.rowPixels", geometry.rowHeightPx)
            .putInt("$prefix.left", geometry.region.left).putInt("$prefix.top", geometry.region.top)
            .putInt("$prefix.right", geometry.region.right).putInt("$prefix.bottom", geometry.region.bottom)
            .putInt("$prefix.failureFrame", result.failureFrame ?: -1).putString("$prefix.failure", result.failureReason)
            .putLong("$prefix.rawCount", result.count).apply()
    }
}
