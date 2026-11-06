package eu.komars.forgescanner.model
import android.content.Context
import org.json.JSONObject
/** Numeric draft only. Frames and credentials are never included. */
class ResourceDraftStore(context:Context) {
 private val prefs=context.getSharedPreferences("resource_drafts_v1",0)
 fun save(account:String,s:ScanSnapshot) {
  val values=JSONObject();s.values.forEach {(k,v)->values.put(k.name,JSONObject().put("value",v.value).put("confirmed",v.confirmed).put("approximate",v.approximate))}
  prefs.edit().putString(account,JSONObject().put("step",s.step.name).put("started",s.startedAtMillis).put("updated",s.updatedAtMillis).put("values",values).toString()).apply()
 }
 fun load(account:String):ScanSession?=runCatching {
  val o=JSONObject(prefs.getString(account,null)?:return null);val values=o.getJSONObject("values")
  val fields=ResourceField.entries.filter {values.has(it.name)}.associateWith {key->val v=values.getJSONObject(key.name);ResourceObservation(v.getLong("value"),v.optBoolean("confirmed"),v.optBoolean("approximate"))}
  ScanSession.restore(ScanSnapshot(ScanStep.valueOf(o.getString("step")),fields,o.getLong("started"),o.getLong("updated")))
 }.getOrNull()
}
