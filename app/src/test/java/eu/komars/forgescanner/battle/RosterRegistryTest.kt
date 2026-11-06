package eu.komars.forgescanner.battle
import org.junit.Assert.*
import org.junit.Test
class RosterRegistryTest {
 private fun row(id:Int,name:String="Gracz$id",power:Double=1e9):RosterObservation {
  val rng=java.util.Random(id.toLong()+123)
  val visual=RosterVisual(IntArray(1024){0xff777777.toInt()+(id%5)*0x111111},IntArray(4096){if(rng.nextInt(5)==0)25 else 232},232)
  return RosterObservation(visual,listOf(RosterReading(name,power),RosterReading(name,power)))
 }
 @Test fun fiftyPeopleStayFiftyDespiteChangingOcrSpellingAcrossOverlappingViewsAndSecondPass() {
  val registry=RosterRegistry()
  for(pass in 0..1) {
   registry.beginPass()
   for(start in (0..42 step 3).toList()+44) {
    val rows=(start until minOf(50,start+6)).map {row(it,if(pass==0&&start%2==0)"Gracz$it"else"Cracz$it")}
    assertTrue("start=$start pass=$pass",registry.addView(rows,50).isSuccess)
   }
   assertEquals(50,registry.size)
  }
 }
 @Test fun sameAvatarAndPowerDoNotMergeDifferentNicknameArtwork() {
  val registry=RosterRegistry();assertTrue(registry.addView((0..5).map {row(it*5)},50).isSuccess)
  assertTrue(registry.addView((3..8).map {row(it*5)},50).isSuccess)
  assertEquals(9,registry.size)
 }
 @Test fun unconfirmedNewScreenDoesNotAppendOrOverwriteAnything() {
  val registry=RosterRegistry();registry.addView((0..5).map {row(it)},50)
  assertTrue(registry.addView((20..25).map {row(it)},50).isFailure)
  assertEquals(6,registry.size)
 }
 @Test fun oneAccidentalMatchingAvatarAndNicknameCannotAnchorAnEntirePage() {
  val registry=RosterRegistry();registry.addView((0..5).map {row(it)},50)
  assertTrue(registry.addView(listOf(row(5),row(30),row(31)),50).isFailure)
  assertEquals(6,registry.size)
 }
 @Test fun correctedReadingsUpdateTheSameSlotsAndConflictsStayUnresolved() {
  val registry=RosterRegistry();registry.addView((0..5).map {row(it,power=123e6)},50)
  registry.addView((0..5).map {row(it,power=128e6)},50)
  assertEquals(6,registry.size);assertEquals(0,registry.resolvedCount)
  registry.addView((0..5).map {row(it,power=123e6)},50)
  assertEquals(6,registry.resolvedCount)
 }
 @Test fun duplicateTextAcrossDistinctCardsRemainsUnresolvedRatherThanSilentlyMergingPlayers() {
  val registry=RosterRegistry();registry.addView((0..5).map {row(it,name="TenSamNick")},50)
  assertEquals(6,registry.size);assertEquals(0,registry.resolvedCount)
 }
 @Test fun majorityOfContrastsIsOneViewVoteNotATieBetweenAllDistinctReadings() {
  val registry=RosterRegistry();val rows=(0..5).map {row(it).copy(readings=listOf(RosterReading("Gracz$it",123e6),RosterReading("Gracz$it",123e6),RosterReading("Cracz$it",128e6)))}
  registry.addView(rows,50);assertEquals(6,registry.resolvedCount);assertTrue(registry.values.all {it.power==123e6})
 }
 @Test fun singleNumericGuessNeedsAnotherImageBeforeItCanBecomeACompleteMember() {
  val registry=RosterRegistry();val weak=(0..5).map {row(it).copy(readings=listOf(RosterReading("Gracz$it",128e9)))}
  registry.addView(weak,50);assertEquals(0,registry.resolvedCount)
  registry.addView(weak,50);assertEquals(6,registry.resolvedCount)
 }
 private fun cache()=RosterSnapshot(20,20,1,1,1,1,IntArray(800){0xffffffff.toInt()})
 @Test fun missingOneOfFiftyIsRepairedFromOnlyItsCachedImageWithoutAddingOrRevisitingPeople() {
  val registry=RosterRegistry()
  for(start in 0..44 step 4) {
   val rows=(start until start+6).map {i->row(i).let {if(i==13)it.copy(readings=listOf(RosterReading(null,null)),snapshot=cache())else it}}
   registry.addView(rows,50).getOrThrow()
  }
  assertEquals(50,registry.size);assertEquals(49,registry.resolvedCount)
  assertEquals(listOf(13),registry.repairTargets.map {it.index})
  val others=registry.values.toList()
  registry.repair(13,listOf(RosterReading("Gracz13",1e9),RosterReading("Gracz13",1e9)))
  assertEquals(50,registry.size);assertEquals(50,registry.resolvedCount)
  assertTrue(registry.values.containsAll(others));assertTrue(registry.repairTargets.isEmpty())
 }
 @Test fun cachedVariantsRemainEvidenceFromTheSameImageAndDoNotInventFrameVotes() {
  val registry=RosterRegistry();registry.addView(listOf(row(0).copy(readings=listOf(RosterReading("Gracz0",128e9)),snapshot=cache())),1)
  registry.repair(0,listOf(RosterReading("Gracz0",123e9)))
  assertEquals(0,registry.resolvedCount)
  registry.repair(0,listOf(RosterReading("Gracz0",123e9),RosterReading("Gracz0",123e9)))
  assertEquals(1,registry.resolvedCount);assertEquals(123e9,registry.values.single().power,.01)
 }
 @Test fun manualFixChangesOnlyTheUnresolvedPersonAndRejectsAnExistingNickname() {
  val registry=RosterRegistry();registry.addView((0..5).map {row(it).let {r->if(it==2)r.copy(readings=listOf(RosterReading(null,null)))else r}},6)
  val before=registry.values.toList()
  assertFalse(registry.setManual(2,"Gracz1",123e6));assertTrue(registry.setManual(2,"Poprawiony",123e6))
  assertEquals(6,registry.resolvedCount);assertTrue(registry.values.containsAll(before))
 }
 @Test fun resumedFullRosterReclaimsSlotsTakenByDuplicateNamesWithMatchingAvatars() {
  val registry=RosterRegistry()
  val saved=(0..46).map {id->row(id).let {RosterSavedRow(it.visual,it.readings.first())}}
  val duplicates=listOf(3,9).map {id->RosterSavedRow(row(id+50).visual,saved[id].reading)}
  registry.restore(saved+duplicates,repairDuplicates=true)
  assertEquals(47,registry.size)
  registry.addUnorderedView((44..49).map {row(it)},50).getOrThrow()
  assertEquals(50,registry.resolvedCount)
 }
 @Test fun resumedFullRosterRejectsAConflictBetweenNicknameAndAvatarEvidence() {
  val registry=RosterRegistry()
  val saved=(0..5).map {id->row(id).let {RosterSavedRow(it.visual,it.readings.first())}}
  registry.restore(saved)
  assertTrue(registry.addUnorderedView(listOf(row(0,name="Gracz5"),row(6)),50).isFailure)
  assertEquals(6,registry.resolvedCount)
 }
 @Test fun acceptedFullProfileRetainsItsCapturedPowerAcrossLivePowerChanges() {
  val registry=RosterRegistry()
  registry.addUnorderedView((0..5).map {row(it)},50).getOrThrow()
  registry.addUnorderedView((3..8).map {row(it,power=123e6)},50) {it.name=="Gracz4"}.getOrThrow()
  assertEquals(1e9,registry.values.single {it.name=="Gracz4"}.power,.01)
  assertEquals(123e6,registry.values.single {it.name=="Gracz5"}.power,.01)
 }
 @Test fun rosterUsesTheActualSevenMembersRatherThanTheFiftyMemberCapacity() {
  val registry=RosterRegistry();registry.addView((0..5).map {row(it)},7).getOrThrow()
  registry.addView((1..6).map {row(it)},7).getOrThrow()
  assertEquals(7,registry.size);assertEquals(7,registry.resolvedCount)
 }
 @Test fun onlyAMatchingTailCanAuthorizeARecoveryMovement() {
  val registry=RosterRegistry();registry.addView((0..5).map {row(it)},50).getOrThrow()
  assertTrue(registry.needsOverlapRecovery((5..11).map {row(it)}))
  assertFalse(registry.needsOverlapRecovery((20..26).map {row(it)}))
  assertFalse(registry.needsOverlapRecovery(listOf(row(2),row(20),row(21))))
  assertEquals(6,registry.size)
 }
}
