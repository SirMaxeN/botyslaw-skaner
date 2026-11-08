package eu.komars.forgescanner.sync

import android.content.Context
import eu.komars.forgescanner.battle.GameDigitFont
import eu.komars.forgescanner.battle.GameDigitMemory
import eu.komars.forgescanner.battle.SkillVisualMemory
import org.json.JSONArray
import org.json.JSONObject

/**
 * Keeps the reading knowledge (skill icons, game-font digits) in a base shared by all paired phones
 * on the bot server: uploads what this phone learned and downloads what others added since the last
 * sync. Survives app updates and reinstalls; never blocks a scan (runs on a worker thread, best effort).
 */
class SharedKnowledgeSync(private val context:Context,private val mobile:MobileSyncClient) {
 private val prefs=context.getSharedPreferences("shared_learning_v1",0)
 private val running=java.util.concurrent.atomic.AtomicBoolean(false)

 fun syncAsync(force:Boolean=false) {
  if(!mobile.paired)return
  if(!force&&System.currentTimeMillis()-prefs.getLong("lastAttempt",0)<10*60_000L)return
  if(!running.compareAndSet(false,true))return
  Thread {
   try {runCatching {sync()}} finally {running.set(false)}
  }.apply {isDaemon=true}.start()
 }

 private fun sync() {
  prefs.edit().putLong("lastAttempt",System.currentTimeMillis()).apply()
  val visual=SkillVisualMemory(context);val digits=GameDigitMemory.shared?:GameDigitMemory(context)
  val sentIcons=prefs.getStringSet("sentIcons",emptySet())!!.toMutableSet()
  val sentDigits=prefs.getStringSet("sentDigits",emptySet())!!.toMutableSet()
  // Upload only icons that look new for their skill (a new star variant or appearance), not near-duplicates.
  val known=visual.allIcons()
  val fresh=visual.exportIcons().filter {"${it.first}_${it.second}" !in sentIcons}
  val icons=fresh.filter {(type,hash,icon)->known.none {(t,h,other)->t==type&&h!=hash&&("${t}_$h" in sentIcons||visual.isShared(t,h))&&(eu.komars.forgescanner.battle.IconThumbnails.iconDistance(icon,other)?:99.0)<12.0}}.take(30)
  sentIcons+=fresh.filter {it !in icons}.map {"${it.first}_${it.second}"}
  val samples=digits.exportLearned().filter {"${it.digit}_${it.glyph.bits}" !in sentDigits}.take(60)
  val payload=JSONObject().put("since",prefs.getLong("since",0))
   .put("icons",JSONArray().apply {icons.forEach {(type,hash,icon)->put(JSONObject().put("type",type).put("hash",hash).put("icon",icon))}})
   .put("digits",JSONArray().apply {samples.forEach {put(JSONObject().put("digit",it.digit).put("aspect",it.glyph.aspect).put("bits",it.glyph.bits))}})
  val response=mobile.learningBlocking(payload)
  val got=response.optJSONArray("icons")?:JSONArray()
  for(i in 0 until got.length())got.optJSONObject(i)?.let {visual.importShared(it.optString("type"),it.optString("hash"),it.optString("icon"))}
  val gotDigits=response.optJSONArray("digits")?:JSONArray()
  digits.importShared((0 until gotDigits.length()).mapNotNull {i->gotDigits.optJSONObject(i)?.let {
   val bits=it.optString("bits");if(bits.length!=GameDigitFont.W*GameDigitFont.H)null
   else GameDigitFont.Sample(it.optInt("digit"),GameDigitFont.Glyph(it.optDouble("aspect"),bits))}})
  sentIcons+=icons.map {"${it.first}_${it.second}"};sentDigits+=samples.map {"${it.digit}_${it.glyph.bits}"}
  prefs.edit().putLong("since",response.optLong("now",prefs.getLong("since",0))).putStringSet("sentIcons",sentIcons).putStringSet("sentDigits",sentDigits).apply()
 }
}
