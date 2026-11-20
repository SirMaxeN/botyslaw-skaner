package eu.komars.forgescanner.battle

import org.junit.Assert.*
import org.junit.Test

class BattleHeaderTextTest {
    @Test fun readsWrappedStatsWithoutUsingForgeLevel() {
        val totals=BattleHeaderText.parse("Poz. 29 Kuźnia\n37.1m Łączne\nobrażenia\n111m Łączne zdrowie")
        assertEquals("37.1m",totals.damage);assertEquals("111m",totals.health)
    }
    @Test fun readsDifferentOpponentUnitsAndCommaWithSpace() {
        val totals=BattleHeaderText.parse("Poz. 31 Kuźnia ★\n1.46b Łączne obrażenia\n14,1 b Łączne zdrowie")
        assertEquals("1.46b",totals.damage);assertEquals("14,1b",totals.health)
        assertEquals("68m",BattleHeaderText.parse("Poz. 30 Kuźnia\n68m Łączne\nobrażenia\n230m Łączne zdrowie").damage)
    }
    @Test fun labelsDecideEvenWhenOrderIsDifferent() {
        val totals=BattleHeaderText.parse("92200000 Łączne zdrowie\n45.3m Łączne obrażenia\nPoz. 29 Kuźnia")
        assertEquals("45.3m",totals.damage);assertEquals("92200000",totals.health)
    }
    @Test fun missingOrAmbiguousStatsRemainMissingRatherThanBorrowingAnotherNumber() {
        assertNull(BattleHeaderText.parse("Poz. 31 Kuźnia\n14.1b Łączne zdrowie").damage)
        assertNull(BattleHeaderText.parse("31 1.46b 14.1b").damage)
        assertNull(BattleHeaderText.parse("37m Łączne obrażenia\n38m Łączne obrażenia").damage)
        assertNull(BattleHeaderText.parse("+25% Obrażenia\n111m Łączne zdrowie").damage)
    }
    /** Real war-day profile reads of 2026-11-18: the power sat between "Łączne" and "obrażenia". */
    @Test fun powerBetweenTheTwoLabelWordsIsNotTheDamage() {
        val oliwiusz=BattleHeaderText.parse("Dzień 2\n27,887 3,793,442\nTiwiusz 90\nLPL_J Poz. 31 Kuźnia\n23,4m Łączne\n927m obrażenia\n134m Łączne zdrowie")
        assertEquals("23,4m",oliwiusz.damage);assertEquals("134m",oliwiusz.health)
        val gumowy=BattleHeaderText.parse("Dzień 2\n18,887 3,666,462\nJmowyPrzetyk 0\nPoz. 35 Kuźnia\n_PL_J 43.5m Łączne\n174b obrażenia\n325m Łączne zdrowie\nM")
        assertEquals("43.5m",gumowy.damage);assertEquals("325m",gumowy.health)
        val roman=BattleHeaderText.parse("Dzień 2\n18,887 3,666,462\nzikiRoman ở\nLPL_J Poz. 10 Kuźnia\n427m Łączne\n17.8b obrażenia\n3.05b Łączne zdrowie")
        assertEquals("427m",roman.damage);assertEquals("3.05b",roman.health)
    }
    /** Enemy war-day reads of 2026-11-17/18 with extra OCR noise. */
    @Test fun noiseAroundThePowerDoesNotBreakTheSplitLabel() {
        val stray=BattleHeaderText.parse("Postep wojny\nDzień 2\n6,427,887 3,793,442\nAleynaa 90\nTLV4A] Poz. 29 Kuźnia\n24.8m Łączne\nN 824m obrażenia\n173m Łączne zdrowie")
        assertEquals("24.8m",stray.damage);assertEquals("173m",stray.health)
        val typo=BattleHeaderText.parse("Postep wojny\nDzień 2\n6,427,887 3,829,842\nAtzeAr G\nTLV4A) Poz. 28 Kuźnia\n27.6m Łaączne\n462m obrażenia\n65.3m Łączne zdrowie")
        assertEquals("27.6m",typo.damage);assertEquals("65.3m",typo.health)
        val symbol=BattleHeaderText.parse("Postep wojny\nDzień 2\n6,427,887 3,866,242\nTowerEd o\nPoz. 28 Kuźnia\n[LV4A) 6.9m Łączne\n&337m obrażenia\n59m Łączne zdrowie")
        assertEquals("6.9m",symbol.damage);assertEquals("59m",symbol.health)
        val dash=BattleHeaderText.parse("Dzień 2\n18,887 3,666,462\necikPulpecik\nPoz. 34 Kuźnia\n_PL_] 89.1m Łączne\n-2b obrażenia\n352m Łączne zdrowie\nM")
        assertEquals("89.1m",dash.damage);assertEquals("352m",dash.health)
    }
    @Test fun statsAtTheSizeOfThePowerAreRejected() {
        assertTrue(BattleHeaderText.plausible(23.4e6,134e6,927e6))
        assertTrue(BattleHeaderText.plausible(89.7e6,118e6,695e6))   // a damage build: 13 % of power
        assertFalse(BattleHeaderText.plausible(927e6,134e6,927e6))   // damage = power
        assertFalse(BattleHeaderText.plausible(174e9,325e6,1.74e9))  // unit misread on top
        assertTrue(BattleHeaderText.plausible(1e6,1e6,0.0))          // power unknown: no judgement
    }
    @Test fun rebuiltOcrColumnsKeepLabelAndNumberTogether() {
        val text=BattleOcrRows.text(listOf(BattleOcrLine("37.1m",10,30,80,45),BattleOcrLine("111m",10,70,80,85),
            BattleOcrLine("Łączne obrażenia",90,30,230,45),BattleOcrLine("Łączne zdrowie",90,70,230,85)))
        val totals=BattleHeaderText.parse(text)
        assertEquals("37.1m",totals.damage);assertEquals("111m",totals.health)
    }
}
