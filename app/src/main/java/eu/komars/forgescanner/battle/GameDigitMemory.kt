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
 }
 private val prefs=context.getSharedPreferences("game_digit_font_v1",0)
 private val bundled:List<GameDigitFont.Sample> by lazy {
  runCatching {context.assets.open("game_digits_v1.txt").bufferedReader().use {GameDigitFont.decode(it.readText())}}.getOrDefault(emptyList())
 }
 private fun learned()=GameDigitFont.decode(prefs.getString("samples","").orEmpty())
 private fun samples()=bundled+learned()
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
 /** Adds the digits of a confirmed label; keeps at most 12 learned samples per digit. */
 fun learn(image:Bitmap,value:Int) {
  store(runCatching {GameDigitFont.learn(image.width,image.height,pixels(image),value)}.getOrDefault(emptyList()))
 }
}
