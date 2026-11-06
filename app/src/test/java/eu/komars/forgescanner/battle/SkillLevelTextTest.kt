package eu.komars.forgescanner.battle
import org.junit.Assert.*
import org.junit.Test
class SkillLevelTextTest {
 @Test fun readsTooltipLevelWithCommonOcrPunctuation() {
  for(s in listOf("Poz. 3","Poz,3","Poziom: 3","LV.3")) assertEquals(3,SkillLevelText.parse("Stampede\n4.07m Obrażenia\n$s"))
 }
 @Test fun doesNotBorrowDamageOrPickBetweenConflictingLevels() {
  assertNull(SkillLevelText.parse("Stampede 4.07m Obrażenia"))
  assertNull(SkillLevelText.parse("Poz.3\nPoz.30"))
  assertNull(SkillLevelText.parse("Poz.113"))
 }
}
