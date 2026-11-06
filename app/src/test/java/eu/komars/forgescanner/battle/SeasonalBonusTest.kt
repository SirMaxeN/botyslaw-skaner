package eu.komars.forgescanner.battle
import org.junit.Assert.*
import org.junit.Test
class SeasonalBonusTest {
 @Test fun seasonalReductionDoesNotOverwriteCriticalDamage() {
  val b=BattleText.bonuses("+230% Obrażenia krytyczne\n+25% Redukcja otrzymywanych obrażeń krytycznych\n+40% Obrażenia co piąty atak\n+10% Szansa na odbicie")
  assertEquals(230.0,b[Bonus.CRIT_DAMAGE]!!,0.0)
  assertEquals(25.0,b[Bonus.CRIT_REDUCTION]!!,0.0)
  assertEquals(40.0,b[Bonus.FIFTH_ATTACK]!!,0.0)
  assertEquals(10.0,b[Bonus.REFLECT]!!,0.0)
 }
 @Test fun loraLevelThreeUsesFiftyPercentAndExplicitStatIsNotAddedTwice() {
  val p=BattleProfile("Lora",10.0,100.0,false,emptyMap(),fairy="LORA",fairyLevel=3)
  assertEquals(.5,PvpBattleEngine.fifthAttackBonus(p),.00001)
  assertEquals(.8,PvpBattleEngine.fifthAttackBonus(p.copy(bonuses=mapOf(Bonus.FIFTH_ATTACK to 80.0))),.00001)
  assertEquals(0.0,PvpBattleEngine.fifthAttackBonus(p.copy(fairy="TIRA")),.00001)
 }
}
