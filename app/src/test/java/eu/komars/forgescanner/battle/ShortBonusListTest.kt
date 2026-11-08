package eu.komars.forgescanner.battle
import org.junit.Assert.*
import org.junit.Test
class ShortBonusListTest {
 /** SroczkinssPL's whole list (2026-11-08): six bonuses, no plain damage or health line. */
 @Test fun sixBonusProfileIsAShortCompleteList() {
  val text="+35.9% Szansa na cios krytyczny\n+322% Obrażenia krytyczne\n+35.9% Wysysanie życia\n+251% Wręcz Obrażenia\n+107% Prędkość ataku\n+110% Obrażenia co 5. atak"
  val parsed=BattleText.bonuses(text)
  assertTrue(parsed.toString(),parsed.size in 1..ShortBonusList.MAX)
 }
}
