package eu.komars.forgescanner.battle
import org.junit.Assert.*
import org.junit.Test
class FairyTextTest {
 @Test fun currentEffectIsNotUpgradeIncrementOrLimit() {
  assertEquals("LORA",FairyText.type("Lora Poz. 3"))
  assertEquals(50.0,FairyText.firstPercent("Co 5. atak zadaje +50% (+10%) więcej obrażeń")!!,.001)
  assertEquals(24.0,FairyText.current("Mira: redukcja 5.5% (+0.5%). Łączny limit 70%. Aktualny bonus wróżki: 24%")!!,.001)
  assertNull(FairyText.current("Mira: redukcja 5.5%, limit 70%"))
 }
}
