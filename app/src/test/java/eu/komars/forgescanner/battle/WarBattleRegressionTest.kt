package eu.komars.forgescanner.battle

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class WarBattleRegressionTest {
    private fun profile(damage:Double,health:Double,skills:List<EquippedSkill> = emptyList()) =
        BattleProfile("test",damage,health,true,mapOf(Bonus.CRIT to 30.0,Bonus.CRIT_DAMAGE to 100.0),skills=skills)

    @Test fun warResearchChangesTheFightOnlyWhenExplicitlySupplied() {
        val own=profile(100.0,1000.0)
        val rival=profile(300.0,3000.0)
        val normal=PvpBattleEngine.simulate(own,rival,n=1,random=Random(1))
        val war=PvpBattleEngine.simulate(own,rival,WarBonus(10000.0,10000.0),n=1,random=Random(1),rules=PvpRules(seconds=120.0))
        assertEquals(1,normal.losses)
        assertEquals(1,war.wins)
        assertEquals(normal,PvpBattleEngine.simulate(own,rival,n=1,random=Random(1)))
    }

    @Test fun equalWarMultipliersPreserveScaleForEverySkill() {
        BattleSkill.entries.forEach { skill ->
            val own=profile(100.0,1e9,listOf(EquippedSkill(skill,40)))
            val rival=profile(110.0,1e9,listOf(EquippedSkill(skill,39)))
            val normal=PvpBattleEngine.simulate(own,rival,n=2,random=Random(13),rules=PvpRules(seconds=120.0))
            val war=PvpBattleEngine.simulate(own,rival,WarBonus(10000.0,10000.0),WarBonus(10000.0,10000.0),n=2,random=Random(13),rules=PvpRules(seconds=120.0))
            assertEquals(skill.name,normal,war)
        }
    }
}
