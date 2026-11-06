package eu.komars.forgescanner.battle

import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class BattleSkillsTest {
    private fun p(d:Double=1.0,h:Double=1000.0,skills:List<EquippedSkill> = emptyList(),bonuses:Map<Bonus,Double> = emptyMap())=BattleProfile("test",d,h,true,bonuses,skills=skills)
    @Test fun allEighteenSkillsHaveCompleteMonotonicLevelTablesAndRecognizeNames() {
        assertEquals(18,BattleSkill.entries.size)
        BattleSkill.entries.forEach { s ->
            assertEquals(s,BattleSkill.recognize(s.aliases.substringBefore('|')))
            s.label.split(" / ").forEach { assertEquals(s,BattleSkill.recognize(it)) }
            assertTrue(s.cooldown>0)
            for(lv in 1..100) {
                assertTrue(s.damage(lv)>0 || s.health(lv)>0)
                if(lv>1) { assertTrue(s.damage(lv)>=s.damage(lv-1));assertTrue(s.health(lv)>=s.health(lv-1)) }
            }
        }
        assertEquals(9711477.0,BattleSkill.LIGHTNING.damage(100),0.0)
        assertEquals(155383712.0,BattleSkill.HIGHER_MORALE.health(100),0.0)
        assertEquals(BattleSkill.HIGHER_MORALE,BattleSkill.recognize("Higher Morale"))
        assertNull(BattleSkill.recognize("nieczytelne"))
    }
    @Test fun sharedHpMultiplierUsesLargerLoadoutAndCapsAreHonored() {
        val full=p(skills=listOf(EquippedSkill(BattleSkill.MEAT,1),EquippedSkill(BattleSkill.ARROWS,1),EquippedSkill(BattleSkill.SHOUT,1))).copy(pets=3,mounted=true)
        assertEquals(6.0,PvpBattleEngine.hpMultiplier(full),0.0)
        assertEquals(1.0,PvpBattleEngine.hpMultiplier(p()),0.0)
        assertEquals(1.5,PvpBattleEngine.cooldown(p(bonuses=mapOf(Bonus.SKILL_COOLDOWN to 50.0)),BattleSkill.LIGHTNING),0.0)
        assertEquals(6.0,PvpBattleEngine.skillScale(p(bonuses=mapOf(Bonus.DAMAGE to 100.0,Bonus.SKILL_DAMAGE to 200.0))),0.0)
    }
    @Test fun everyDamageSkillCanDecideTheBattleBeforeFirstWeaponAttack() {
        BattleSkill.entries.filter { it.kind!=SkillKind.BUFF }.forEach { s ->
            val own=p(skills=listOf(EquippedSkill(s,1)))
            val result=PvpBattleEngine.simulate(own,p(),n=1,random=Random(1),rules=PvpRules(seconds=1.0))
            if(s.damage(1)>1500) assertEquals(s.name,1,result.wins)
        }
    }
    @Test fun timedSkillCooldownReductionDoesNotShortenItsActiveDuration() {
        val half=p(bonuses=mapOf(Bonus.SKILL_COOLDOWN to 50.0))
        assertEquals(18.0,PvpBattleEngine.cycle(p(),BattleSkill.MORALE),0.0)
        assertEquals(14.0,PvpBattleEngine.cycle(half,BattleSkill.MORALE),0.0)
        assertEquals(12.0,PvpBattleEngine.cycle(half,BattleSkill.HIGHER_MORALE),0.0)
        assertEquals(1.0,PvpBattleEngine.skillHealthScale(p(bonuses=mapOf(Bonus.DAMAGE to 500.0))),0.0)
    }
    @Test fun timerComparesRemainingPercentInsteadOfAlwaysDrawing() {
        val result=PvpBattleEngine.simulate(p(d=2.0,h=1e9),p(d=1.0,h=1e9),n=1,rules=PvpRules(seconds=3.0))
        assertEquals(1,result.wins)
    }
    @Test fun repeatedSkillsAreRejectedInsteadOfDoublingAnUnrealLoadout() {
        val own=p(skills=listOf(EquippedSkill(BattleSkill.WORM,1),EquippedSkill(BattleSkill.WORM,2)))
        assertThrows(IllegalArgumentException::class.java) { PvpBattleEngine.simulate(own,p(),n=1) }
    }
}
