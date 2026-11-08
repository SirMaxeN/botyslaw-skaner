package eu.komars.forgescanner.battle

import android.content.Context
import android.graphics.Bitmap

/**
 * Templates of the game's label digits: bundled samples cut from real screenshots plus digits learned
 * from labels whose value was confirmed by two agreeing OCR reads. Only 10×14 masks are stored.
 */
class GameDigitMemory(context:Context) {
 companion object {
  /** Set by ScannerApplication so readers without a Context (resource counters) can use the font. */
  @Volatile var shared:GameDigitMemory?=null
   set(value) {field=value;eu.komars.forgescanner.model.CounterConfirmations.listener=value?.let {memory->{key,confirmed->memory.confirmCounter(key,confirmed)}}}
 }
 private val prefs=context.getSharedPreferences("game_digit_font_v1",0)
 private val bundled:List<GameDigitFont.Sample> by lazy {
  runCatching {context.assets.open("game_digits_v1.txt").bufferedReader().use {GameDigitFont.decode(it.readText())}}.getOrDefault(emptyList())
 }
 private fun learned()=GameDigitFont.decode(prefs.getString("samples","").orEmpty())
 /** Samples learned by other players' phones, downloaded from the bot server (kept apart from local ones). */
 private fun sharedSamples()=GameDigitFont.decode(prefs.getString("shared_samples","").orEmpty())
 private fun samples()=bundled+learned()+sharedSamples()
 /** Local learned samples for upload to the shared base. */
 fun exportLearned():List<GameDigitFont.Sample> = learned()
 fun importShared(fresh:List<GameDigitFont.Sample>) {
  if(fresh.isEmpty())return
  val kept=(sharedSamples()+fresh).distinctBy {it.digit to it.glyph.bits}.groupBy {it.digit}.values.flatMap {it.takeLast(24)}
  prefs.edit().putString("shared_samples",GameDigitFont.encode(kept)).apply()
 }
 private fun pixels(image:Bitmap):IntArray=IntArray(image.width*image.height).also {image.getPixels(it,0,image.width,0,0,image.width,image.height)}
 fun read(image:Bitmap):Int?=runCatching {GameDigitFont.read(image.width,image.height,pixels(image),samples())}.getOrNull()
 /** Plain counter ("9383") from raw pixels; null unless every digit is unambiguous. */
 fun readCounter(w:Int,h:Int,pixels:IntArray):Long?=runCatching {GameDigitFont.readCounter(w,h,pixels,samples())}.getOrNull()
 /** Learns a counter confirmed by three agreeing OCR frames (only when its digit count matches). */
 fun learnCounter(w:Int,h:Int,pixels:IntArray,value:Long) {
  val glyphs=runCatching {GameDigitFont.segment(w,h,pixels,prefixed=false)}.getOrNull()?:return
  val digits=value.toString().map {it-'0'}
  // A leading resource icon may precede the digits: keep only the trailing glyphs.
  if(glyphs.size<digits.size)return
  store(glyphs.takeLast(digits.size).zip(digits).map {(g,d)->GameDigitFont.Sample(d,g)})
 }
 private fun store(fresh:List<GameDigitFont.Sample>) {
  if(fresh.isEmpty())return
  val kept=(learned()+fresh).groupBy {it.digit}.values.flatMap {it.takeLast(12)}
  prefs.edit().putString("samples",GameDigitFont.encode(kept)).apply()
 }
 private val pendingCounters=java.util.concurrent.ConcurrentHashMap<String,Pair<Long,Triple<Int,Int,IntArray>>>()
 /** Keeps the last crop of a counter for ten minutes, so a manual confirmation can teach its digits. */
 fun rememberCounterCrop(key:String,w:Int,h:Int,pixels:IntArray) {pendingCounters[key]=System.currentTimeMillis() to Triple(w,h,pixels)}
 /** The user confirmed this value for the counter: learn from its last crop if the font could not read it. */
 fun confirmCounter(key:String,value:Long) {
  val (at,crop)=pendingCounters.remove(key)?:return
  if(System.currentTimeMillis()-at>10*60_000L)return
  val (w,h,p)=crop
  if(readCounter(w,h,p)!=value)learnCounter(w,h,p,value)
 }
 /** Adds the digits of a confirmed label; keeps at most 12 learned samples per digit. */
 fun learn(image:Bitmap,value:Int) {
  store(runCatching {GameDigitFont.learn(image.width,image.height,pixels(image),value)}.getOrDefault(emptyList()))
 }
}
