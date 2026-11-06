package eu.komars.forgescanner.battle

import org.junit.Assert.*
import org.junit.Test

class BattleScanFixesTest {
    @Test fun missingBonusesAreZeroAndPolishNegativeCooldownIsRead() {
        val result=BattleText.completeBonuses("+65.4% Szansa na cios krytyczny\n−3.08% Umiejętność Czas\nodnowienia")
        assertEquals(Bonus.entries.size,result.size)
        assertEquals(65.4,result.getValue(Bonus.CRIT),.001)
        assertEquals(3.08,result.getValue(Bonus.SKILL_COOLDOWN),.001)
        assertEquals(0.0,result.getValue(Bonus.HEALTH),.001)
        assertEquals(0.0,BattleText.completeBonuses("-3.08% Umiejętność Czas",mapOf(Bonus.SKILL_COOLDOWN to 0.0)).getValue(Bonus.SKILL_COOLDOWN),.001)
    }
    @Test fun weaponMarkerToleratesMissingPunctuationWithoutGuessingFromAnEmptyRead() {
        assertEquals(true,BattleText.weapon("Łuk (dystans"))
        assertEquals(true,BattleText.weapon("Łuk\ndystans)"))
        assertEquals(false,BattleText.weapon("(\n[Podziemny świat] Buława Zagłady"))
        assertNull(BattleText.weapon(""))
        assertNull(BattleText.weapon("Łuk (dyst"))
    }
    @Test fun rangedTypeIsReadFromDamageRowAndNeverFromPercentBonus() {
        assertEquals(true,BattleText.weapon("[Podziemny świat] Otchłanny widelec\n963k Obrażenia (dystansowy)\nPoz.98\n+19.9% Szansa na podwójnie"))
        assertEquals(true,BattleText.weapon("Łuk\n1.2m Obrażenia\n(dystansowy)\n+16% Obrażenia"))
        assertEquals(false,BattleText.weapon("Buława\n1.58m Obrażenia\n+26.6% Dystansowe Obrażenia"))
        assertNull(BattleText.weapon("Łuk\n963k Obrażenia (dyst"))
    }
    @Test fun totalDamageKeepsDecimalAndUnitAndRejectsAmbiguousHeader() {
        assertEquals("37.1m",BattleText.displayedNumber("37.1m Łączne\nobrażenia"))
        assertEquals(37100000.0,BattleText.number("37.1m")!!,0.01)
        assertEquals("11,4m",BattleText.displayedNumber("11,4m Łączne obrażenia"))
        assertNull(BattleText.displayedNumber("Poz.29 37.1m"))
        assertNull(BattleText.displayedNumber(""))
    }
    @Test fun flatAttackRangeIsNotTreatedAsPercentOrGeneralDamage() {
        assertEquals(1.0,BattleText.bonuses("+1 Zasięg ataku")[Bonus.RANGE]!!,0.0)
        assertEquals(1.0,BattleText.bonuses("Zasięg +1")[Bonus.RANGE]!!,0.0)
        assertFalse(BattleText.bonuses("+1 Zasięg").containsKey(Bonus.DAMAGE))
    }
    @Test fun blockOrderedOcrColumnsAreJoinedByActualRowCoordinates() {
        val text=BattleOcrRows.text(listOf(
            BattleOcrLine("+65.4%",0,10,50,24),BattleOcrLine("-3.08%",0,40,50,54),
            BattleOcrLine("Szansa na cios krytyczny",55,10,250,24),BattleOcrLine("Umiejętność Czas",55,40,250,54),
            BattleOcrLine("odnowienia",55,56,200,70)))
        val values=BattleText.bonuses(text)
        assertEquals(65.4,values[Bonus.CRIT]!!,.001)
        assertEquals(3.08,values[Bonus.SKILL_COOLDOWN]!!,.001)
    }
}
