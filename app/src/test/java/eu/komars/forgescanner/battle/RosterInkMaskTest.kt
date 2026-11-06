package eu.komars.forgescanner.battle
import org.junit.Assert.*
import org.junit.Test
class RosterInkMaskTest {
 @Test fun whiteLettersAreSeparatedFromTheirCardAndBlackOutline() {
  assertTrue(RosterInkMask.name(255,255,255));assertFalse(RosterInkMask.name(232,232,232));assertFalse(RosterInkMask.name(15,15,15));assertFalse(RosterInkMask.name(40,180,255))
 }
 @Test fun goldenDigitsAndDecimalDotsRemainInkWithoutTheSwordsOrBackground() {
  assertTrue(RosterInkMask.power(255,196,58));assertTrue(RosterInkMask.power(174,122,24));assertFalse(RosterInkMask.power(232,232,232));assertFalse(RosterInkMask.power(180,200,200));assertFalse(RosterInkMask.power(0,0,0))
 }
}
