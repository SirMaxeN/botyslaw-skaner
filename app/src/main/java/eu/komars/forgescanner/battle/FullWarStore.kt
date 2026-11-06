package eu.komars.forgescanner.battle
import android.content.Context
import org.json.JSONObject
import java.security.MessageDigest
import java.time.LocalDate
import java.time.ZoneId

/** Separate storage: no accepted self/opponent profile, pairing or calibration is overwritten. */
internal class FullWarStore(context:Context,namespace:String="full_war") {
 private val logFile=java.io.File(context.cacheDir,"${namespace}-events.jsonl")
 private val fairyProgressions=FairyProgressionCatalog(context)
 private val prefs=context.getSharedPreferences("${namespace}_profiles_v1",0)
 private val pairs by lazy {
  val file=context.getDatabasePath("${namespace}_pairs_v1.db");file.parentFile?.mkdirs()
  android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(file,null).apply {
   enableWriteAheadLogging();execSQL("CREATE TABLE IF NOT EXISTS pair_results(cache_key TEXT PRIMARY KEY,payload TEXT NOT NULL,saved_at INTEGER NOT NULL)")
   execSQL("CREATE TABLE IF NOT EXISTS run_state(id INTEGER PRIMARY KEY,payload TEXT NOT NULL)")
   execSQL("CREATE TABLE IF NOT EXISTS roster_state(side INTEGER NOT NULL,slot INTEGER NOT NULL,visual BLOB NOT NULL,reading TEXT NOT NULL,PRIMARY KEY(side,slot))")
  }
 }
 companion object {
  const val MODEL="pvp-full-v1"
  fun hash(s:String)=MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString(""){"%02x".format(it)}
 }
 private fun key(enemy:Boolean,clan:String,name:String)=hash("${state()?.optString("date")?:LocalDate.now(ZoneId.of("Europe/Warsaw")).toString()}|$enemy|${WarResumePolicy.clanKey(clan)}|${BattleText.normalize(name)}")
 @Synchronized fun entry(enemy:Boolean,clan:String,member:WarMember):JSONObject? {
  val direct=runCatching {JSONObject(prefs.getString(key(enemy,clan,member.name),null)!!)}.getOrNull()?.takeIf {it.optString("model")==MODEL&&it.optDouble("power")==member.power&&System.currentTimeMillis()-it.optLong("savedAt") in 0..86_400_000L}
  if(direct!=null)return checkFairyConfidence(direct)
  val today=if(state()!=null)System.currentTimeMillis()-86_400_000L else LocalDate.now(ZoneId.of("Europe/Warsaw")).atStartOfDay(ZoneId.of("Europe/Warsaw")).toInstant().toEpochMilli()
  return prefs.all.values.asSequence().mapNotNull {runCatching {JSONObject(it.toString())}.getOrNull()}.firstOrNull {record->record.optString("model")==MODEL&&record.optLong("savedAt")>=today&&(!record.has("enemy")||record.optBoolean("enemy")==enemy)&&WarResumePolicy.sameClan(record.optString("clan"),clan)&&BattleText.normalize(record.optString("name"))==BattleText.normalize(member.name)&&record.optDouble("power")==member.power}?.let(::checkFairyConfidence)
 }
 private fun checkFairyConfidence(record:JSONObject):JSONObject {
  val fairy=(record.optJSONObject("profile")?:record.optJSONObject("draft"))?.optJSONObject("fairy")?:return record
  val calculated=fairyProgressions.infer(fairy.optString("type"),fairy.optString("detailRaw"))
  val mismatched=calculated!=null&&fairy.optInt("level")!=calculated.level&&!fairy.optBoolean("levelConfirmed")
  val suspect=mismatched||(fairy.optInt("level") in setOf(3,5,8)&&!fairy.optBoolean("levelConfirmed")&&!fairy.optBoolean("levelByEffect")&&fairy.optString("levelSource") !in setOf("effect","glyph","manual","progression"))
  if(!suspect)return record
  val copy=JSONObject(record.toString());val draft=copy.optJSONObject("draft")?:copy.optJSONObject("profile")?:return record
  draft.optJSONObject("fairy")?.remove("level")
  return copy.put("draft",draft).put("complete",false).put("error","Poziom wróżki wymaga sprawdzenia z jej bonusem i przyrostem.").apply {remove("profile")}
 }
 @Synchronized fun save(enemy:Boolean,clan:String,member:WarMember,profile:JSONObject?,draft:JSONObject?,error:String?) {
  val record=JSONObject().put("model",MODEL).put("enemy",enemy).put("clan",clan).put("name",member.name).put("power",member.power).put("savedAt",System.currentTimeMillis()).put("complete",profile!=null)
  if(profile!=null)record.put("profile",profile)
  if(draft!=null)record.put("draft",draft)
  if(error!=null)record.put("error",error)
  check(prefs.edit().putString(key(enemy,clan,member.name),record.toString()).commit()) {"Nie zapisano postępu skanu."}
 }

 @Synchronized fun log(event:String,details:JSONObject=JSONObject()) {runCatching {
  if(logFile.exists()&&logFile.length()>512*1024)logFile.writeText(logFile.readLines().takeLast(1000).joinToString("\n",postfix="\n"))
  logFile.appendText(JSONObject().put("at",java.time.Instant.now().toString()).put("event",event).put("data",details).toString()+"\n")
 }}
 @Synchronized fun state():JSONObject?=pairs.rawQuery("SELECT payload FROM run_state WHERE id=1",null).use {if(!it.moveToFirst())null else runCatching {JSONObject(it.getString(0))}.getOrNull()?.takeIf {System.currentTimeMillis()-it.optLong("updatedAt") in 0..86_400_000L}}
 @Synchronized fun checkpoint(value:JSONObject,ours:List<RosterSavedRow>,enemies:List<RosterSavedRow>,saveRoster:Boolean=true) {
  value.put("date",state()?.optString("date")?:LocalDate.now(ZoneId.of("Europe/Warsaw")).toString()).put("updatedAt",System.currentTimeMillis())
  pairs.beginTransaction();try {
   pairs.execSQL("INSERT OR REPLACE INTO run_state VALUES(1,?)",arrayOf(value.toString()))
   for((side,rows) in if(saveRoster)listOf(0 to ours,1 to enemies)else emptyList()) {
    pairs.execSQL("DELETE FROM roster_state WHERE side=?",arrayOf(side))
    rows.forEachIndexed {slot,row->val buffer=java.io.ByteArrayOutputStream();java.io.DataOutputStream(buffer).use {out->out.writeInt(row.visual.background);out.writeDouble(row.visual.nameAspect);row.visual.avatar.forEach(out::writeInt);row.visual.name.forEach(out::writeByte)}
     val reading=JSONObject().put("name",row.reading.name?:JSONObject.NULL).put("power",row.reading.power?:JSONObject.NULL)
     pairs.execSQL("INSERT INTO roster_state VALUES(?,?,?,?)",arrayOf(side,slot,buffer.toByteArray(),reading.toString()))
    }
   };pairs.setTransactionSuccessful()
  }finally {pairs.endTransaction()}
 }
 @Synchronized fun roster(enemy:Boolean):List<RosterSavedRow> {
  val result=mutableListOf<RosterSavedRow>();pairs.rawQuery("SELECT visual,reading FROM roster_state WHERE side=? ORDER BY slot",arrayOf(if(enemy)"1"else"0")).use {cursor->while(cursor.moveToNext()) {
   val input=java.io.DataInputStream(java.io.ByteArrayInputStream(cursor.getBlob(0)));val bg=input.readInt();val aspect=input.readDouble();val avatar=IntArray(1024){input.readInt()};val name=IntArray(4096){input.readUnsignedByte()};input.close()
   val reading=JSONObject(cursor.getString(1));result+=RosterSavedRow(RosterVisual(avatar,name,bg,aspect),RosterReading(if(reading.isNull("name"))null else reading.getString("name"),if(reading.isNull("power"))null else reading.getDouble("power")))
  }};return result
 }
 @Synchronized fun attempted(enemy:Boolean,clan:String,member:WarMember)=entry(enemy,clan,member)?.let {it.optJSONObject("draft")!=null||it.optBoolean("complete")}==true
 @Synchronized fun pair(key:String):JSONObject? = pairs.rawQuery("SELECT payload FROM pair_results WHERE cache_key=?",arrayOf(key)).use {if(it.moveToFirst())runCatching {JSONObject(it.getString(0))}.getOrNull()else null}
 @Synchronized fun pair(key:String,value:JSONObject) {pairs.execSQL("INSERT OR REPLACE INTO pair_results VALUES(?,?,?)",arrayOf(key,value.toString(),System.currentTimeMillis()))}
 @Synchronized fun prune() {val boundary=System.currentTimeMillis()-2*24*60*60*1000L;val edit=prefs.edit();prefs.all.forEach {(key,value)->if(runCatching {JSONObject(value.toString()).optLong("savedAt",0)<boundary}.getOrDefault(true))edit.remove(key)};edit.apply();pairs.execSQL("DELETE FROM pair_results WHERE saved_at<?",arrayOf(boundary))}
 @Synchronized fun clear() {prefs.edit().clear().commit();pairs.execSQL("DELETE FROM pair_results");pairs.execSQL("DELETE FROM run_state");pairs.execSQL("DELETE FROM roster_state")}
}
