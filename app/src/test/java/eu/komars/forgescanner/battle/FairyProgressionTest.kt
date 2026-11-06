package eu.komars.forgescanner.battle
import org.junit.Assert.*
import org.junit.Test
class FairyProgressionTest {
 private val tira=FairyProgression.Rule(5.5,.5)
 @Test fun actualWi3rzbaConflictIsElevenFromCoefficientAndGreenIncrement() {
  val text="Tira\nZa każde +10% Zdrowie w\nekwipunku: +10.5% (+0.5%)\nPoz. 10\nObrażenia.\nAktualny bonus wróżki:\n+21%Ohraienia"
  val read=FairyProgression.infer("TIRA",text,tira)!!
  assertEquals(11,read.level);assertEquals(10.5,read.coefficient,.0001);assertEquals(.5,read.increment,.0001)
  val evidence=FairyProgressionEvidence();evidence.add(read);assertNull(evidence.accepted());evidence.add(read);assertEquals(11,evidence.accepted()!!.level)
 }
 @Test fun allCurrentFairyTypesUseTheDisplayedStepRatherThanTotalOrCappedBonus() {
  assertEquals(3,FairyProgression.infer("TIRA","Tira Za każde +10% Zdrowie: +6,5% (+0,5%) Obrażenia. Aktualny bonus wróżki +90%",tira)!!.level)
  assertEquals(3,FairyProgression.infer("LORA","Lora Co 5. atak +50% (+10%) Aktualny bonus wróżki +50%",FairyProgression.Rule(30.0,10.0))!!.level)
  assertEquals(11,FairyProgression.infer("MIRA","Mira Zmniejsz obrażenia krytyczne o +9.5% (+0.5%) za każde -4% cooldown. Aktualny bonus wróżki 70%",FairyProgression.Rule(4.5))!!.level)
 }
 @Test fun missingStepWrongSeasonFractionalLevelsAndConflictsDoNotBecomeGuesses() {
  for(text in listOf("Tira +10.5%", "Tira +10.5% (+1%)", "Tira +10.6% (+0.5%)", "Tira +50% (+0.5%)","Mira +10.5% (+0.5%)"))assertNull(FairyProgression.infer("TIRA",text,tira))
  val evidence=FairyProgressionEvidence();evidence.add(FairyProgression.infer("TIRA","Tira +10.5% (+0.5%)",tira));evidence.add(FairyProgression.infer("TIRA","Tira +10% (+0.5%)",tira))
  assertNull(evidence.accepted());assertFalse(evidence.agreesWith(11));assertEquals(listOf(10,11),evidence.candidates)
 }
 @Test fun confirmationButtonsOnlyShowActualOcrAndCalculatedCandidates() {
  assertEquals(listOf(10,11),FairyLevelChoices.values(listOf(11,10),listOf(11)))
  assertEquals(emptyList<Int>(),FairyLevelChoices.values(emptyList(),emptyList()))
  assertEquals(listOf(3,8),FairyLevelChoices.values(listOf(3,8),emptyList()))
 }
}
