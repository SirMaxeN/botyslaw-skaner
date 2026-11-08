package eu.komars.forgescanner.battle
import org.junit.Assert.*
import org.junit.Test
class RivalBonusDialogTest {
 @Test fun manualBonusAcceptsNumbersWithOrWithoutPercent() {
  assertEquals(0.0,RivalBonusDialog.parse("0")!!,0.0)
  assertEquals(0.0,RivalBonusDialog.parse("0%")!!,0.0)
  assertEquals(8700.0,RivalBonusDialog.parse("8700 %")!!,0.0)
  assertEquals(8700.0,RivalBonusDialog.parse("8.7k%")!!,0.0)
  assertEquals(12.5,RivalBonusDialog.parse("12,5")!!,0.0)
  assertNull(RivalBonusDialog.parse(""))
  assertNull(RivalBonusDialog.parse("abc"))
 }
}
