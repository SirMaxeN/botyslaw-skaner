package eu.komars.forgescanner.battle

import android.content.Context
import android.graphics.Bitmap

/**
 * Templates of the game's label digits: bundled samples cut from real screenshots plus digits learned
 * from labels whose value was confirmed by two agreeing OCR reads. Only 10×14 masks are stored.
 */
internal class GameDigitMemory(context:Context) {
 private val prefs=context.getSharedPreferences("game_digit_font_v1",0)
 private val bundled:List<GameDigitFont.Sample> by lazy {
  runCatching {context.assets.open("game_digits_v1.txt").bufferedReader().use {GameDigitFont.decode(it.readText())}}.getOrDefault(emptyList())
 }
 private fun learned()=GameDigitFont.decode(prefs.getString("samples","").orEmpty())
 private fun samples()=bundled+learned()
 private fun pixels(image:Bitmap):IntArray=IntArray(image.width*image.height).also {image.getPixels(it,0,image.width,0,0,image.width,image.height)}
 fun read(image:Bitmap):Int?=runCatching {GameDigitFont.read(image.width,image.height,pixels(image),samples())}.getOrNull()
 /** Adds the digits of a confirmed label; keeps at most 12 learned samples per digit. */
 fun learn(image:Bitmap,value:Int) {
  val fresh=runCatching {GameDigitFont.learn(image.width,image.height,pixels(image),value)}.getOrDefault(emptyList())
  if(fresh.isEmpty())return
  val kept=(learned()+fresh).groupBy {it.digit}.values.flatMap {it.takeLast(12)}
  prefs.edit().putString("samples",GameDigitFont.encode(kept)).apply()
 }
}
