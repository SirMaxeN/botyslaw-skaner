package eu.komars.forgescanner.battle
import org.json.JSONObject
import org.json.JSONArray

internal class BatchScanException(message:String,val draft:JSONObject):IllegalStateException(message)
internal object FullProfileCodec {
 fun accept(expected:String,draft:JSONObject):JSONObject {
  require(BattleText.normalize(BattleText.playerName(draft.optString("name")))==BattleText.normalize(expected)) {"Nick profilu nie zgadza się z wybraną kartą: ${draft.optString("name")}."}
  val damage=BattleText.number(draft.optString("damage"));val health=BattleText.number(draft.optString("health"))
  require(damage!=null&&damage>0&&health!=null&&health>0) {"Brak łącznych obrażeń lub zdrowia."}
  val ranged=BattleText.weapon(draft.optString("weapon"));require(ranged!=null) {"Brak typu broni."}
  val scans=draft.optJSONArray("skillScans");require(scans?.length()==3) {"Brak kompletu trzech pól umiejętności."}
  val skills=JSONArray();val seen=mutableSetOf<String>()
  for(i in 0..2) {val s=scans!!.getJSONObject(i);if(s.optBoolean("empty"))continue
   val type=runCatching {BattleSkill.valueOf(s.optString("type"))}.getOrNull();require(type!=null&&s.optInt("level") in 1..100&&seen.add(type.name)) {"Niepewna umiejętność lub poziom w polu ${i+1}."}
   skills.put(JSONObject().put("type",type.name).put("level",s.getInt("level")))
  }
  val fairy=draft.optJSONObject("fairy")?:error("Brak odczytu wróżki.")
  val absent=fairy.has("present")&&!fairy.optBoolean("present")
  require(absent||(fairy.optString("type") in listOf("LORA","MIRA","TIRA")&&fairy.optInt("level") in 1..20)) {"Niepewna wróżka lub jej poziom."}
  val bonuses=draft.optJSONObject("bonuses")?.let {JSONObject(it.toString())}?:JSONObject()
  if(!absent&&fairy.optString("type")=="MIRA") {
   val listed=bonuses.optDouble(Bonus.CRIT_REDUCTION.name,0.0)
   if(fairy.optDouble("criticalReduction",0.0)<=0&&listed<=0) {
    val coefficient=FairyEffects.miraCoefficient(fairy.optString("detailRaw"))
    require(coefficient!=null&&draft.has("raw")) {"Nie odczytano współczynnika wróżki Mira."}
    // A missing cooldown line is a confirmed zero only after the whole bonus list was read.
    val cooldown=bonuses.optDouble(Bonus.SKILL_COOLDOWN.name,0.0)
    bonuses.put(Bonus.CRIT_REDUCTION.name,FairyEffects.miraReduction(coefficient,cooldown))
    fairy.put("criticalReductionSource","calculated").put("criticalReductionCoefficient",coefficient)
   }
  }
  require(draft.has("pets")&&draft.optInt("pets",-1) in 0..3&&draft.has("mounted")) {"Nie potwierdzono zwierzaków lub wierzchowca."}
  return JSONObject(draft.toString()).put("name",expected).put("damage",damage).put("health",health).put("ranged",ranged).put("skills",skills).put("skillsReviewed",true).put("pets",draft.getInt("pets")).put("mounted",draft.getBoolean("mounted")).put("skillMastery",0.0).put("bonuses",bonuses).put("fairy",fairy)
 }
 fun profile(o:JSONObject):BattleProfile {
  val values=o.optJSONObject("bonuses");val raw=o.optString("raw")
  return BattleProfile(o.getString("name"),o.getDouble("damage"),o.getDouble("health"),o.getBoolean("ranged"),
   BattleText.completeBonuses(raw,Bonus.entries.filter {values?.has(it.name)==true}.associateWith {values!!.getDouble(it.name)}),o.optLong("scannedAt"),raw,BattleText.caps(raw),
   (0 until o.getJSONArray("skills").length()).map {i->val e=o.getJSONArray("skills").getJSONObject(i);EquippedSkill(BattleSkill.valueOf(e.getString("type")),e.getInt("level"))},
   o.getInt("pets"),o.getBoolean("mounted"),o.optDouble("skillMastery",0.0),o.optJSONObject("fairy")?.optString("type").orEmpty(),o.optJSONObject("fairy")?.optInt("level",0)?:0)
 }
}
