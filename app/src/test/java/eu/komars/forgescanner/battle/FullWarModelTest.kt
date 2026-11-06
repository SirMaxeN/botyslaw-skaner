package eu.komars.forgescanner.battle
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random
class FullWarModelTest {
 @Test fun memberAvatarRouteReturnsToTheRosterAndCannotStartOnHome() {
  val graph=eu.komars.forgescanner.model.GameNavigationGraph
  val cursor=eu.komars.forgescanner.model.GameNavigationGraph.Cursor()
  cursor.observe(eu.komars.forgescanner.model.GameNavigationGraph.Screen.HOME)
  assertFalse(cursor.allows(eu.komars.forgescanner.model.GameNavigationGraph.Action.OPEN_MEMBER_PROFILE))
  cursor.observe(eu.komars.forgescanner.model.GameNavigationGraph.Screen.ROSTER)
  cursor.completed(eu.komars.forgescanner.model.GameNavigationGraph.Action.OPEN_MEMBER_PROFILE)
  cursor.completed(eu.komars.forgescanner.model.GameNavigationGraph.Action.CLOSE_MEMBER_PROFILE)
  assertEquals(eu.komars.forgescanner.model.GameNavigationGraph.Screen.ROSTER,cursor.screen)
 }
 @Test fun memberIndicesFollowConfirmedAvatarOverlapInsteadOfFixedRowNumbers() {
  fun row(i:Int):RosterObservation {val random=Random(i);return RosterObservation(RosterVisual(IntArray(1024){random.nextInt()},IntArray(4096){random.nextInt(256)},220),listOf(RosterReading("Nick$i",100.0),RosterReading("Nick$i",100.0)))}
  val rows=(0..3).map(::row);val registry=RosterRegistry()
  registry.addView(rows.take(3),4).getOrThrow();assertEquals(listOf(0,1,2),registry.visibleIndices(3))
  registry.addView(rows.drop(1),4).getOrThrow();assertEquals(listOf(1,2,3),registry.visibleIndices(3))
 }
 @Test fun globalAssignmentBeatsGreedyAndLeavesUnfavourableOrUnknownPairsEmpty() {
  assertArrayEquals(intArrayOf(1,0),WarAssignment.best(arrayOf(doubleArrayOf(.90,.89),doubleArrayOf(.88,.01))))
  assertArrayEquals(intArrayOf(-1,-1),WarAssignment.best(arrayOf(doubleArrayOf(Double.NaN,.5),doubleArrayOf(.2,.1))))
  assertArrayEquals(intArrayOf(-1,0),WarAssignment.best(arrayOf(doubleArrayOf(.8),doubleArrayOf(.9))))
 }
 @Test fun everySmallAssignmentAgreesWithExhaustiveSearch() {
  fun optimum(a:Array<DoubleArray>,i:Int=0,used:Set<Int> = emptySet()):Double {
   if(i==a.size)return 0.0;var best=optimum(a,i+1,used)
   for(j in a[i].indices)if(j !in used&&a[i][j]>.5)best=maxOf(best,a[i][j]+optimum(a,i+1,used+j));return best
  }
  for(mask in 0 until 729) {var k=mask;val a=Array(2){DoubleArray(3){val value=doubleArrayOf(Double.NaN,.6,.9)[k%3];k/=3;value}}
   val got=WarAssignment.best(a).mapIndexed {i,j->if(j>=0)a[i][j]else 0.0}.sum();assertEquals(optimum(a),got,1e-10)
  }
 }
 @Test fun occupiedEmptyAndUnknownEquipmentRemainDistinct() {
  val bg=0xff444b60.toInt();assertEquals(false,ProfileSlotPresence.occupied(16,16,IntArray(256){bg},bg))
  assertNull(ProfileSlotPresence.occupied(16,16,IntArray(256){0xffb09070.toInt()},bg))
  assertEquals(true,ProfileSlotPresence.occupied(16,16,IntArray(256){if(it%2==0)0xff000000.toInt()else 0xffff0000.toInt()},bg))
 }
 @Test fun scratchArrayReusePreservesOldSeededCombatResultsAcrossSkillsAndBonuses() {
  val random=Random(413)
  repeat(72) {at->
   fun profile(side:Int)=BattleProfile("P$side",1e6+random.nextInt(1_000_000),1e8,random.nextBoolean(),mapOf(Bonus.CRIT to random.nextDouble(80.0),Bonus.STEAL to 20.0,Bonus.DOUBLE to 30.0),
    skills=listOf(EquippedSkill(BattleSkill.entries[(at+side)%18],1+at%99)),pets=at%4,mounted=at%2==0)
   val a=profile(0);val b=profile(1);val war=WarBonus(87.0,77.0);val rules=PvpRules(seconds=8.0)
   assertEquals(PreviousPvpEngine.simulate(a,b,war,war,7,Random(at),rules=rules),PvpBattleEngine.simulate(a,b,war,war,7,Random(at),rules=rules))
  }
 }
 @Test fun cancellationStopsAtStartAndInsideALongFight() {
  val p=BattleProfile("A",1.0,1e20,false,emptyMap())
  try {PvpBattleEngine.simulate(p,p,shouldStop={true});fail("not cancelled")}catch(_:java.util.concurrent.CancellationException){}
  var calls=0;try {PvpBattleEngine.simulate(p,p,n=1,shouldStop={++calls>1});fail("not cancelled")}catch(_:java.util.concurrent.CancellationException){}
  assertEquals(2,calls)
 }
 @Test fun benchmarkInitialPairBudgetWithoutAnUnstableTimeAssertion() {
  val p=BattleProfile("A",1e7,1e9,false,mapOf(Bonus.CRIT to 65.0,Bonus.DOUBLE to 30.0),pets=3,mounted=true)
  val start=System.nanoTime();val result=PvpBattleEngine.simulate(p,p,n=128,random=Random(42))
  assertEquals(128,result.count);println("Full-war benchmark: 128 trials = ${(System.nanoTime()-start)/1_000_000} ms on this test host; not a phone timing promise")
 }
}
