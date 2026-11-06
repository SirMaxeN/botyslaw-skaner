package eu.komars.forgescanner.battle
import org.junit.Assert.*
import org.junit.Test
class FairyLevelGlyphTest {
 @Test fun unknownConflictingOrTwoDigitShapesAreNeverAssignedByDefault() {
  val a=FairyLevelGlyph.Signature(1,.6,"1".repeat(280)+"0".repeat(280))
  val b=FairyLevelGlyph.Signature(1,.6,"0".repeat(280)+"1".repeat(280))
  assertNull(FairyLevelGlyph.match(a,emptyList()))
  assertNull(FairyLevelGlyph.match(a,listOf(3 to a,8 to a)))
  assertNull(FairyLevelGlyph.match(a.copy(digits=2),listOf(3 to a)))
  assertEquals(3,FairyLevelGlyph.match(a,listOf(3 to a,8 to b)))
  assertEquals(8,FairyLevelGlyph.match(b,listOf(3 to a,8 to b)))
 }
 @Test fun blankAnIconAndAnUnlabelledDigitCannotBecomeALevel() {
  assertNull(FairyLevelGlyph.extract(100,100,IntArray(10000){0xff44495c.toInt()}))
  assertNull(FairyLevelGlyph.extract(100,100,IntArray(10000){0xffffffff.toInt()}))
 }
}
