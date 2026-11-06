package eu.komars.forgescanner.battle
import android.content.Context
import org.json.JSONObject

/** Numeric season data lives outside scanner logic. Green increments still come from the game. */
internal class FairyProgressionCatalog(context:Context) {
 private val catalog=runCatching {JSONObject(context.assets.open("fairy_progressions_v1.json").bufferedReader().use {it.readText()})}.getOrNull()
 val version get()=catalog?.optString("version").orEmpty()
 fun infer(type:String,text:String):FairyProgression.Reading? {
  val rule=catalog?.optJSONObject("rules")?.optJSONObject(type)?:return null
  return FairyProgression.infer(type,text,FairyProgression.Rule(rule.optDouble("base",Double.NaN),if(rule.has("increment"))rule.optDouble("increment")else null))
 }
 fun evidence(value:FairyProgression.Reading)=JSONObject().put("rules",version).put("base",value.base).put("coefficient",value.coefficient).put("increment",value.increment).put("level",value.level)
}
