package eu.komars.forgescanner.battle
import org.junit.Assert.*
import org.junit.Test
class RosterEndGateTest {
 @Test fun unchangedPixelsReachEndEvenWhenOcrNamesWouldChange() {
  val g=RosterEndGate();val a=IntArray(1200){it%255}
  assertFalse(g.add(a));assertFalse(g.add(a));assertTrue(g.add(a))
 }
 @Test fun actualScrollResetsBoundaryEvidence() {
  val g=RosterEndGate();val a=IntArray(1200){10};val b=IntArray(1200){120}
  assertFalse(g.add(a));assertFalse(g.add(a));assertFalse(g.add(b));assertFalse(g.add(b));assertTrue(g.add(b))
 }
 @Test fun tinyStatusPixelChangesDoNotKeepListMovingForever() {
  val g=RosterEndGate();val a=IntArray(1200){50}
  assertFalse(g.add(a));val b=a.copyOf();b[0]=200
  assertFalse(g.add(b));assertTrue(g.add(a))
 }
 @Test fun mostlyWhiteCardsWithChangedSparseTextAreNotTheEnd() {
  val a=IntArray(8192){255};for(i in 0 until 200)a[i*40]=25
  val b=IntArray(8192){255};for(i in 0 until 200)b[i*40+2]=25
  val gate=RosterEndGate();assertFalse(gate.add(a));assertFalse(gate.add(b));assertFalse(gate.add(a))
 }
 @Test fun blankOrSolidLightGrayViewportDoesNotProveAListBoundary() {
  for(color in listOf(255,232)) {
   val gate=RosterEndGate();repeat(5){assertFalse(gate.add(IntArray(8192){color}))}
  }
 }
 /** Top of the list: "X godz. temu" and avatars change ~4% of ink; that is still the top, a scrolled list is not. */
 @Test fun topGateToleratesLiveDetailsButNotMovement() {
  val top=IntArray(4000){if(it%3==0)40 else 230}
  val live=top.copyOf().also {for(i in 0 until 4000 step 75)it[i]=if(it[i]==40)200 else 40}
  val strict=RosterEndGate(1);strict.add(top);assertFalse(strict.add(live))
  val gate=RosterEndGate(1,6);gate.add(top);assertTrue(gate.add(live))
  val moved=IntArray(4000){if((it+1)%3==0)40 else 230}
  val again=RosterEndGate(1,6);again.add(top);assertFalse(again.add(moved))
 }
}
