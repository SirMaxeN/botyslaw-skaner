package eu.komars.forgescanner.battle
import org.junit.Assert.*
import org.junit.Test

class FairyEffectsTest {
 private val realMira="Mira\nZmniejsz otrzymywane obrażenia\nkrytyczne o 4.5% (+0.5%) za każde -4%\nPoz,1 Umiejętność Czas odnowienia w\nekwipunku.\nRedukcja otrzymywanych obrażeń\nkrvtvCZOvch: acz0v imit to 70%."
 @Test fun miraUsesWholeCooldownStepsAndTheSeventyPercentCap() {
  assertEquals(4.5,FairyEffects.miraCoefficient(realMira)!!,1e-9)
  assertEquals(13.5,FairyEffects.miraReduction(4.5,12.0),1e-9)
  assertEquals(13.5,FairyEffects.miraReduction(4.5,15.99),1e-9)
  assertEquals(18.0,FairyEffects.miraReduction(4.5,15.9995),1e-9)
  assertEquals(0.0,FairyEffects.miraReduction(4.5,0.0),1e-9)
  assertEquals(70.0,FairyEffects.miraReduction(14.0,40.0),1e-9)
  assertEquals(70.0,FairyEffects.miraReduction(7.0,8.0,65.0),1e-9)
 }
 @Test fun cachedMiraIsReusableWhenItsCoefficientWasRead() {
  assertTrue(CachedFairyPolicy.reusable(false,"MIRA",1,Double.NaN,realMira))
  assertFalse(CachedFairyPolicy.reusable(false,"MIRA",1,Double.NaN,"Mira"))
 }
}
