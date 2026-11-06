package eu.komars.forgescanner.automation

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Legacy setup metadata; runtime recognition never compares these pixel samples. */
class AutoReadCheckpoints(context: Context) {
    private val preferences = context.getSharedPreferences("auto_read_checkpoints_v1", Context.MODE_PRIVATE)
    fun save(step: AutoScanStep, checkpoint: ScreenCheckpoint) {
        require(checkpoint.valid)
        preferences.edit().putString(step.name, JSONObject().put("aspect", checkpoint.aspect.toDouble())
            .put("samples", JSONArray(checkpoint.samples)).toString()).apply()
    }
    fun get(step: AutoScanStep): ScreenCheckpoint? = runCatching {
        val raw = preferences.getString(step.name, null) ?: return null
        if (raw.length > 24000) return null
        val data = JSONObject(raw); val samples = data.getJSONArray("samples")
        if (samples.length() != ScreenCheckpoint.WIDTH * ScreenCheckpoint.HEIGHT) return null
        ScreenCheckpoint(data.getDouble("aspect").toFloat(), (0 until samples.length()).map(samples::getInt)).takeIf { it.valid }
    }.getOrNull()
    fun clear() = preferences.edit().clear().apply()
}
