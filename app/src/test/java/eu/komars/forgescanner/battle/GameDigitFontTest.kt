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
 @Test fun ambiguousOrUnknownGlyphsAreNotGuessed() {
  val samples=listOf(GameDigitFont.Sample(3,glyph(3)),GameDigitFont.Sample(8,glyph(3)))
  assertNull(GameDigitFont.classify(glyph(3),samples))
  assertEquals(3,GameDigitFont.classify(glyph(3),listOf(GameDigitFont.Sample(3,glyph(3)),GameDigitFont.Sample(8,glyph(1)))))
  assertNull(GameDigitFont.read(emptyList(),samples))
  assertNull(GameDigitFont.read(listOf(glyph(3)),emptyList()))
 }
}
