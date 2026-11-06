package eu.komars.forgescanner.battle
import org.junit.Assert.*
import org.junit.Test
class ResearchMotionGateTest {
 @Test fun motionResetsEvidenceAndRequiresThreeStationaryFrames() {
  val gate=ResearchMotionGate()
  fun frame(offset:Int)=IntArray(4096){if((it+offset)%64<16)20 else 240}
  assertFalse(gate.add(frame(0)))
  assertFalse(gate.add(frame(1)))
  assertFalse(gate.add(frame(2)))
  assertFalse(gate.add(frame(2)))
  assertFalse(gate.add(frame(3)))
  assertFalse(gate.add(frame(3)))
  assertTrue(gate.add(frame(3)))
 }
}
