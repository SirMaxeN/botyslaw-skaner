package eu.komars.forgescanner.battle
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.*
class RosterRowGeometryTest {
 @Test fun actualRosterCardsIgnoreAvatarOrderNamesAndRanks() {
  val dir=System.getenv("ROSTER_ROW_FIXTURES");assumeTrue(dir!=null)
  for(name in listOf("roster-current","test25-roster-clean")) {
   DataInputStream(BufferedInputStream(File(dir,"$name.argb").inputStream())).use {f ->
    val w=f.readInt();val h=f.readInt();val p=IntArray(w*h){f.readInt()}
    val rows=RosterRowGeometry.find(w,h,p)
    assertEquals("$name: $rows",6,rows.size)
    assertTrue(rows.all {it.height in 40..65})
   }
  }
 }
 @Test fun partialCardsAndRankSeparatorsAreExcludedAtDifferentSizes() {
  for(w in listOf(200,320,640)) {
   val h=w*2;val p=IntArray(w*h){0xffffffff.toInt()}
   fun card(top:Int,bottom:Int){for(y in maxOf(0,top) until minOf(h,bottom))for(x in w/20 until w*19/20)p[y*w+x]=0xffe8e8e8.toInt()}
   card(-w/12,w/12);card(w/5,w*2/5);card(w/2,w*7/10);card(h-w/12,h+w/12)
   // White gaps, a black rank separator and colored avatars cannot become extra cards.
   for(x in 0 until w)p[(w*9/20)*w+x]=0xff000000.toInt()
   val rows=RosterRowGeometry.find(w,h,p)
   assertEquals(2,rows.size);assertEquals(w/5,rows.first().top)
  }
 }
 @Test fun contrastVotesCannotSilentlyPairAcrossDifferentPlayers() {
  val a=WarMember("GraczA",123e6);val b=WarMember("GraczB",456e6)
  assertEquals(a,RosterRowEvidence.resolve(listOf(listOf(a),emptyList(),listOf(a))))
  assertNull(RosterRowEvidence.resolve(listOf(listOf(a),listOf(b),emptyList())))
  assertNull(RosterRowEvidence.resolve(listOf(listOf(a,b),emptyList())))
 }
}
