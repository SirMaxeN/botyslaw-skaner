package eu.komars.forgescanner.automation

import android.content.Context
import android.graphics.Rect
import eu.komars.forgescanner.model.CalibrationStore
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.roundToInt

data class NavigationCalibration(val region: Rect, val before: ScreenCheckpoint, val after: ScreenCheckpoint?) {
    val x: Float get() = region.exactCenterX()
    val y: Float get() = region.exactCenterY()
}

/** Device-local bounded navigation geometry and layout signatures. No resources or images. */
class AutoScanStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("auto_scan_navigation_v1", Context.MODE_PRIVATE)

    fun saveTechnologyTab(region:Rect,width:Int,height:Int) {
        require(width>0 && height>0 && !region.isEmpty && Rect(0,0,width,height).contains(region))
        preferences.edit().putString("research_technology_tab",JSONObject().put("w",width).put("h",height).put("l",region.left).put("t",region.top).put("r",region.right).put("b",region.bottom).toString()).apply()
    }
    fun technologyTab(width:Int,height:Int):Rect? = runCatching {
        val o=JSONObject(preferences.getString("research_technology_tab",null)!!)
        if(o.getInt("w")!=width || o.getInt("h")!=height)return null
        Rect(o.getInt("l"),o.getInt("t"),o.getInt("r"),o.getInt("b"))
    }.getOrNull()

    fun saveNavigation(step: AutoScanStep, region: Rect, frameWidth: Int, frameHeight: Int, before: ScreenCheckpoint) {
        require(step.isNavigation && frameWidth > 0 && frameHeight > 0 && before.valid)
        require(region.left >= 0 && region.top >= 0 && region.right <= frameWidth && region.bottom <= frameHeight && region.width() >= 2 && region.height() >= 2)
        val saved = JSONObject()
            .put("left", region.left.toDouble() / frameWidth).put("top", region.top.toDouble() / frameHeight)
            .put("right", region.right.toDouble() / frameWidth).put("bottom", region.bottom.toDouble() / frameHeight)
            .put("aspect", frameWidth.toDouble() / frameHeight).put("before", checkpointJson(before))
        // Reconfiguring a source action always invalidates its old destination.
        preferences.edit().putString(step.name, saved.toString()).apply()
    }

    fun saveDestination(step: AutoScanStep, after: ScreenCheckpoint) {
        require(step.isNavigation && after.valid)
        val saved = saved(step) ?: throw IllegalStateException("Najpierw ustaw przycisk nawigacji.")
        saved.put("after", checkpointJson(after))
        preferences.edit().putString(step.name, saved.toString()).apply()
    }

    fun navigation(step: AutoScanStep, width: Int, height: Int): NavigationCalibration? {
        if (!step.isNavigation || width <= 0 || height <= 0) return null
        if(step==AutoScanStep.CLOSE_RESEARCH) {
            val open=navigation(AutoScanStep.OPEN_RESEARCH,width,height) ?: return null
            return open.after?.let {NavigationCalibration(open.region,it,open.before)}
        }
        val saved = saved(step) ?: return null
        return runCatching {
            val aspect = saved.getDouble("aspect")
            if (!aspect.isFinite() || aspect <= 0 || abs((width.toDouble() / height) / aspect - 1) > .025) return null
            val edges = listOf("left", "top", "right", "bottom").map(saved::getDouble)
            if (edges.any { !it.isFinite() || it !in 0.0..1.0 } || edges[2] <= edges[0] || edges[3] <= edges[1]) return null
            val rect = Rect((edges[0] * width).roundToInt(), (edges[1] * height).roundToInt(),
                (edges[2] * width).roundToInt(), (edges[3] * height).roundToInt())
            if (rect.width() < 2 || rect.height() < 2) return null
            val before = checkpoint(saved.getJSONObject("before")) ?: return null
            val after = saved.optJSONObject("after")?.let(::checkpoint)
            NavigationCalibration(rect, before, after)
        }.getOrNull()
    }

    fun hasNavigation(step: AutoScanStep): Boolean {
        if(step==AutoScanStep.CLOSE_RESEARCH)return hasNavigation(AutoScanStep.OPEN_RESEARCH)
        val saved = saved(step) ?: return false
        return runCatching {
            val before = checkpoint(saved.getJSONObject("before"))
            val after = checkpoint(saved.getJSONObject("after"))
            before != null && after != null
        }.getOrDefault(false)
    }

    fun isReady(calibrations: CalibrationStore): Boolean = AutoScanStep.entries.all { step ->
        step.target?.let(calibrations::has) ?: hasNavigation(step)
    }
    fun clear(step: AutoScanStep) = preferences.edit().remove(step.name).apply()
    fun clear() = preferences.edit().clear().apply()

    private fun saved(step: AutoScanStep): JSONObject? = runCatching {
        val raw = preferences.getString(step.name, null) ?: return null
        if (raw.length > 24_000) return null
        JSONObject(raw)
    }.getOrNull()

    private fun checkpointJson(value: ScreenCheckpoint): JSONObject = JSONObject().put("aspect", value.aspect.toDouble())
        .put("pixels", JSONArray(value.samples))
    private fun checkpoint(value: JSONObject): ScreenCheckpoint? = runCatching {
        val pixels = value.getJSONArray("pixels")
        if (pixels.length() != ScreenCheckpoint.WIDTH * ScreenCheckpoint.HEIGHT) return null
        ScreenCheckpoint(value.getDouble("aspect").toFloat(), (0 until pixels.length()).map(pixels::getInt)).takeIf { it.valid }
    }.getOrNull()
}
