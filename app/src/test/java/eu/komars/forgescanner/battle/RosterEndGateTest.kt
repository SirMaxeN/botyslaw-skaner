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
}
