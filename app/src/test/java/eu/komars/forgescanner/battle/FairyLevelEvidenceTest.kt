package eu.komars.forgescanner.battle
import org.junit.Assert.*
import org.junit.Test
class FairyLevelEvidenceTest {
 @Test fun repeatedEightStillRequiresConfirmationForKnownThreeEightConfusion() {
  for(level in listOf(3,8)) {
   val e=FairyLevelEvidence();repeat(2){e.add(level,false);e.add(level,true)}
   assertNull(e.accepted())
  }
 }
 @Test fun conflictingDetailCannotOverwriteProfileAndMissingReadsAreNotVotes() {
  val e=FairyLevelEvidence();repeat(2){e.add(3,false);e.add(8,true)}
  assertNull(e.accepted());assertEquals(listOf(3,8),e.candidates)
  val missing=FairyLevelEvidence();missing.add(12,false);repeat(3){missing.add(null,true)};assertNull(missing.accepted())
 }
 @Test fun otherLevelIsAcceptedOnceTheFairyWindowAgreesWithAnotherRead() {
  val e=FairyLevelEvidence();repeat(2){e.add(12,false)};assertNull(e.accepted())
  e.add(12,true);assertEquals(12,e.accepted())
  val one=FairyLevelEvidence();one.add(1,true);assertNull(one.accepted());one.add(1,true);assertEquals(1,one.accepted())
 }
 @Test fun singleRemainingSuggestionIsChosenWithoutAskingExceptAmbiguousDigits() {
  val one=FairyLevelEvidence();one.add(1,false);assertEquals(1,one.automaticChoice(emptyList()))
  val eight=FairyLevelEvidence();eight.add(8,false);eight.add(8,true);assertNull(eight.automaticChoice(emptyList()))
  assertEquals(8,eight.automaticChoice(listOf(8)))
  val two=FairyLevelEvidence();two.add(10,false);two.add(11,true);assertNull(two.automaticChoice(emptyList()))
 }
 @Test fun confirmedLoraEffectCanResolveThreeFiveEightDisagreement() {
  for((level,bonus) in listOf(3 to 50.0,5 to 70.0,8 to 100.0)) {
   val e=FairyLevelEvidence();e.add(3,false);e.add(8,false);repeat(2){e.add(5,true);e.addLoraEffect(bonus,bonus)}
   assertEquals(level,e.acceptedWithLoraBonus(bonus))
  }
 }
 @Test fun contradictoryPercentagesCannotTeachOrSelectTheWrongDigit() {
  val e=FairyLevelEvidence();repeat(2){e.add(3,false);e.add(8,true);e.addLoraEffect(50.0,100.0)}
  assertNull(e.acceptedWithLoraBonus(50.0))
 }
 @Test fun learnedShapeCanCorrectSystematicEightOcrButCannotEraseATwoDigitLevel() {
  val e=FairyLevelEvidence();repeat(2){e.add(8,false);e.add(8,true)}
  assertEquals(3,e.acceptedWithGlyph(3));assertNull(e.acceptedWithGlyph(null))
  e.add(13,true);assertNull(e.acceptedWithGlyph(3))
 }
 @Test fun verifiedGlyphCanRescueMissingOcrOnlyAfterFairyDetailsWereActuallyRead() {
  val e=FairyLevelEvidence();assertNull(e.acceptedWithGlyph(3))
  e.add(null,true);assertNull(e.acceptedWithGlyph(3));e.add(null,true)
  assertEquals(3,e.acceptedWithGlyph(3));assertNull(e.acceptedWithGlyph(null))
 }
}
