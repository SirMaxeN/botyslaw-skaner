package eu.komars.forgescanner.battle
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.*
class RosterVisualReplayTest {
 private fun frame(name:String):Triple<Int,Int,IntArray> {
  val dir=System.getenv("ROSTER_ROW_FIXTURES");assumeTrue(dir!=null)
  return DataInputStream(BufferedInputStream(File(dir,"$name.argb").inputStream())).use {f ->val w=f.readInt();val h=f.readInt();Triple(w,h,IntArray(w*h){f.readInt()})}
 }
 @Test fun actualSirMaxenFailureExcludesTheClippedAvatarAndKeepsOtherCards() {
  val (w,h,p)=frame("clipped-self-test65");val cards=RosterCardLayout.cards(w,h,p)
  assertEquals("The screenshot has six fully framed avatars; the blue last one is clipped",6,cards.size)
  assertTrue(cards.all {it.avatar.bottom<h})
 }
 @Test fun actualCardsRemainTheSamePeopleAfterSubpixelScrollAndChangedOcrNames() {
  for(name in listOf("roster-current","test25-roster-clean","roster-duplicates")) {
   val (w,h,p)=frame(name);val cards=RosterCardLayout.cards(w,h,p);assertEquals(name,6,cards.size)
   val shifted=IntArray(p.size) {i ->val y=i/w;val x=i%w;val a=p[maxOf(0,y-1)*w+maxOf(0,x-1)];val b=p[y*w+maxOf(0,x-1)]
    var out=0xff000000.toInt();for(shift in intArrayOf(0,8,16))out=out or (((((a ushr shift and 255)+(b ushr shift and 255))/2)) shl shift);out
   }
   val again=RosterCardLayout.cards(w,h,shifted);assertEquals(name,6,again.size)
   val registry=RosterRegistry();assertTrue(registry.addView(cards.mapIndexed {i,c->RosterObservation(c.visual,listOf(RosterReading("Nick$i",1e9)))},50).isSuccess)
   assertTrue("$name: visual drift was mistaken for new players",registry.addView(again.mapIndexed {i,c->RosterObservation(c.visual,listOf(RosterReading("N1ck$i",1e9)))},50).isSuccess)
   assertEquals(6,registry.size)
  }
 }
 @Test fun numericCropDoesNotAcceptTheCrossedSwordsAsADigitOrAName() {
  assertEquals(1.23e9,RosterCardText.power("1.23b")!!,.01)
  assertEquals(897e6,RosterCardText.power("897m")!!,.01)
  assertNull(RosterCardText.power("X128b R4"));assertNull(RosterCardText.power("89Tm"))
  assertNull(RosterCardText.name("R4"))
 }
 @Test fun recordedRankLabelsCannotSplitCardsAndOurBlueCardIsIncluded() {
  for(second in listOf(20,25,30,35,45,50,55,60,65,70)) {
   val (w,h,p)=frame("recording-$second");val cards=RosterCardLayout.cards(w,h,p)
   assertEquals("Recording $second",6,cards.size)
  }
  val (w,h,p)=frame("recording-30");val own=RosterCardLayout.cards(w,h,p).single {it.name.top in 490..505}
  assertTrue("Blue background must not be mistaken for an icon",own.name.width>45)
 }
 @Test fun actualOvershootCannotAppendPlayersButCanRecoverAfterOneRowBack() {
  fun rows(name:String,prefix:String):List<RosterObservation> {val (w,h,p)=frame(name);return RosterCardLayout.cards(w,h,p).mapIndexed {i,c->RosterObservation(c.visual,listOf(RosterReading("$prefix$i",1e9),RosterReading("$prefix$i",1e9)))}}
  val before=rows("top-before-overshoot","old");val after=rows("actual-overshoot","new")
  assertEquals(6,before.size);assertEquals(7,after.size)
  val registry=RosterRegistry();registry.addView(before,50).getOrThrow()
  assertTrue(registry.addView(after,50).isFailure);assertEquals(6,registry.size)
  assertTrue("The lone Wojtekg anchor should trigger a short backtrack",registry.needsOverlapRecovery(after))
  val corrected=(listOf(before[4])+after).take(7)
  registry.addView(corrected,50).getOrThrow();assertEquals(11,registry.size)
 }
}
