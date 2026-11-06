package eu.komars.forgescanner.battle

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class BattleModelTest {
    @Test fun parsesPolishPercentagesWithoutTreatingCapsAsBonuses() {
        val b=BattleText.bonuses("+31.8% Szansa na cios krytyczny\n(maks. 80%)\n+255% Obrażenia krytyczne\n+4,62% Dystansowe Obrażenia\n+16% Obrażenia\n+2.42% Regeneracja zdrowia")
        assertEquals(31.8,b[Bonus.CRIT]!!,.0001)
        assertEquals(255.0,b[Bonus.CRIT_DAMAGE]!!,.0001)
        assertEquals(4.62,b[Bonus.RANGED]!!,.0001)
        assertEquals(16.0,b[Bonus.DAMAGE]!!,.0001)
        assertEquals(2.42,b[Bonus.REGEN]!!,.0001)
        assertFalse(b.containsKey(Bonus.HEALTH))
        val caps=BattleText.caps("+31.8% Szansa na cios krytyczny\n(maks. 80%)\n+35% Szansa na blok (maks. 30%)")
        assertEquals(80.0,caps[Bonus.CRIT]!!,.0001)
        assertEquals(30.0,caps[Bonus.BLOCK]!!,.0001)
    }
    @Test fun doesNotTurnOtherDamageTypesIntoGeneralDamage() {
        assertTrue(BattleText.bonuses("+25% Odbite obrażenia").isEmpty())
    }
    @Test fun requiresUnambiguousNumberAndWeaponTitle() {
        assertEquals(37_100_000.0,BattleText.number("37,1m")!!,.1)
        assertEquals(1.46e9,BattleText.number("1.46b")!!,.1)
        assertNull(BattleText.displayedNumber("Poziom 29 37.1m"))
        assertEquals(true,BattleText.weapon("Łuk (dystans)"))
        assertEquals(false,BattleText.weapon("Miecz"))
        assertNull(BattleText.weapon("Łuk (dyst"))
        assertNull(BattleText.weapon(""))
    }
    private fun p(d:Double,h:Double,bs:Map<Bonus,Double> = emptyMap())=BattleProfile("test",d,h,false,bs)
    @Test fun damageAndWarBonusesAppliedExactlyOnce() {
        val profile=p(100.0,100.0,mapOf(Bonus.DAMAGE to 20.0,Bonus.MELEE to 50.0))
        assertEquals(100.0,BattleEngine.hit(profile),.0001)
        assertEquals(120.0,BattleEngine.hit(profile,WarBonus(20.0)),.0001)
        assertEquals(180.0,BattleEngine.hit(profile,applyStaticBonuses=true),.0001)
        assertEquals(216.0,BattleEngine.hit(profile,WarBonus(20.0),true),.0001)
    }
    @Test fun deterministicWinsDrawsAndBreakpoints() {
        val r=BattleEngine.simulate(p(1000.0,10000.0),p(1.0,10.0),n=20,random=Random(1))
        assertEquals(20,r.wins); assertEquals(0,r.draws)
        val d=BattleEngine.simulate(p(1.0,10000.0),p(1.0,10000.0),n=20,random=Random(1))
        assertEquals(20,d.draws)
        assertEquals(1.7,BattleEngine.interval(.07),.0001)
        assertEquals(1.6,BattleEngine.interval(.071),.0001)
        assertEquals(.5,BattleEngine.interval(4.0),.0001)
    }
}
