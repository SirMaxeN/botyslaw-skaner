package eu.komars.forgescanner.battle
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class GameDigitFontTest {
 private fun glyph(seed:Int)=GameDigitFont.Glyph(.6,(0 until GameDigitFont.W*GameDigitFont.H).joinToString("") {if((it*7+seed*13)%5<2)"1" else "0"})
 @Test fun templatesRoundTripAndBundledSetParses() {
  val samples=listOf(GameDigitFont.Sample(3,glyph(3)),GameDigitFont.Sample(8,glyph(8)))
  assertEquals(samples,GameDigitFont.decode(GameDigitFont.encode(samples)))
  val bundled=GameDigitFont.decode(File("src/main/assets/game_digits_v1.txt").readText())
  assertTrue(bundled.size>=30)
  assertTrue(bundled.map {it.digit}.toSet().containsAll(listOf(0,1,3,4,5,7,9)))
 }
 @Test fun sameZeroShapeCanBeLearnedInWhiteAndReadInRed() {
  fun counter(color:Int):IntArray = IntArray(30*22) {i->
   val x=i%30;val y=i/30
   if(x in 15..22&&y in 4..16&&(x in 15..16||x in 21..22||y in 4..5||y in 15..16))color
   else 0xff262626.toInt()
  }
  val white=counter(0xffffffff.toInt())
  val red=counter(0xffe54250.toInt())
  val samples=GameDigitFont.learnCounter(30,22,white,0)
  assertEquals(1,samples.size)
  assertEquals(0L,GameDigitFont.readCounterShape(30,22,red,samples))
  assertEquals(samples,GameDigitFont.learnCounter(30,22,red,0))
  assertTrue(GameDigitFont.learnCounter(30,22,red,10).isEmpty())
 }
 @Test fun ambiguousOrUnknownGlyphsAreNotGuessed() {
  val samples=listOf(GameDigitFont.Sample(3,glyph(3)),GameDigitFont.Sample(8,glyph(3)))
  assertNull(GameDigitFont.classify(glyph(3),samples))
  assertEquals(3,GameDigitFont.classify(glyph(3),listOf(GameDigitFont.Sample(3,glyph(3)),GameDigitFont.Sample(8,glyph(1)))))
  assertNull(GameDigitFont.read(emptyList(),samples))
  assertNull(GameDigitFont.read(listOf(glyph(3)),emptyList()))
 }
}
