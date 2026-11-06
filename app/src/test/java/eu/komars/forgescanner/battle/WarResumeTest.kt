package eu.komars.forgescanner.battle
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random
class WarResumeTest {
 @Test fun unreadableHeaderCannotEraseFiftyAndClanTagOcrNoiseDoesNotLoseTheSession() {
  assertEquals(50,WarResumePolicy.validCount(null,50));assertEquals(49,WarResumePolicy.validCount(49,50));assertNull(WarResumePolicy.validCount(null,0))
  assertTrue(WarResumePolicy.sameClan("PolskaMOC[_PL_J","PolskaMOC[_PL_]"));assertFalse(WarResumePolicy.sameClan("ClanA","ClanB"))
 }
 @Test fun clippedBottomAvatarNeedsCenteringWithoutChangingFieldsOrTheTarget() {
  assertFalse(WarResumePolicy.safe(RosterBox(10,1120,130,1240),1209));assertTrue(WarResumePolicy.safe(RosterBox(10,600,130,720),1209))
  assertTrue(WarResumePolicy.centerDistance(1209) in 150f..303f)
 }
 @Test fun reorderedCardsAndChangedPowerDoNotAppendDuplicatePeople() {
  fun row(i:Int,power:Double=100.0):RosterObservation {val r=Random(i);return RosterObservation(RosterVisual(IntArray(1024){r.nextInt()},IntArray(4096){r.nextInt(256)},130),listOf(RosterReading("N$i",power),RosterReading("N$i",power)))}
  val registry=RosterRegistry();registry.addUnorderedView((0..5).map {row(it)},50).getOrThrow()
  registry.addUnorderedView(listOf(row(0),row(2),row(1,803e6),row(4),row(3)),50).getOrThrow()
  assertEquals(6,registry.size);assertEquals(listOf(0,2,1,4,3),registry.visibleIndices(5));assertEquals(803e6,registry.partial(1).power!!,.1)
  val fresh=RosterRegistry();fresh.restore(registry.savedRows());assertEquals(registry.values,fresh.values)
 }
 @Test fun independentLoraEffectDisambiguatesAnOtherwiseUncertainLevel() {
  val evidence=FairyLevelEvidence();evidence.add(10,false);evidence.add(10,true);evidence.add(10,true)
  assertEquals(10,evidence.acceptedWithLoraBonus(120.0));assertNull(evidence.acceptedWithLoraBonus(110.0))
  val ambiguous=FairyLevelEvidence();ambiguous.add(3,false);ambiguous.add(8,true);assertNull(ambiguous.acceptedWithLoraBonus(50.0))
 }
 @Test fun aCompletedMemberCanCloseUsingItsObservedAnchorDespiteAnOverlayCoveringTheControl() {
  val box=eu.komars.forgescanner.model.PixelRegion(470,1950,610,2090)
  val anchor=MemberProfileAnchor("XlawoK",1080,2340,box)
  assertEquals(box,anchor.closeFor("XLAWOK",1080,2340,true))
  assertNull(anchor.closeFor("XlawoK",1080,2340,false))
  assertNull(anchor.closeFor("Other",1080,2340,true))
  assertNull(anchor.closeFor("XlawoK",720,1560,true))
 }
 @Test fun incompleteMiraEffectMustBeReadAgainInsteadOfFinishingTheSameInvalidCachedProfile() {
  assertFalse(CachedFairyPolicy.reusable(false,"MIRA",7,Double.NaN))
  assertFalse(CachedFairyPolicy.reusable(false,"MIRA",7,0.0))
  assertTrue(CachedFairyPolicy.reusable(false,"MIRA",7,40.0))
  assertFalse(CachedFairyPolicy.reusable(false,"LORA",10,Double.NaN))
  assertTrue(CachedFairyPolicy.reusable(false,"LORA",10,120.0))
  assertTrue(CachedFairyPolicy.reusable(true,"",0,Double.NaN))
 }
 @Test fun duplicatedNewCardsCannotConsumeTwoMemberSlotsOrPartiallyMutateTheRoster() {
  val random=Random(71);val v=RosterVisual(IntArray(1024){random.nextInt()},IntArray(4096){random.nextInt(256)},130)
  val row=RosterObservation(v,listOf(RosterReading("Same",100.0),RosterReading("Same",100.0)))
  val list=RosterRegistry()
  assertTrue(list.addUnorderedView(listOf(row,row),50).isFailure);assertEquals(0,list.size)
  list.addUnorderedView(listOf(row),50).getOrThrow()
  assertTrue(list.addUnorderedView(listOf(row,row),50).isFailure);assertEquals(1,list.size)
 }
}
