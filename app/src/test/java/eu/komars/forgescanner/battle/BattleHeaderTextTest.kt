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
    @Test fun rebuiltOcrColumnsKeepLabelAndNumberTogether() {
        val text=BattleOcrRows.text(listOf(BattleOcrLine("37.1m",10,30,80,45),BattleOcrLine("111m",10,70,80,85),
            BattleOcrLine("Łączne obrażenia",90,30,230,45),BattleOcrLine("Łączne zdrowie",90,70,230,85)))
        val totals=BattleHeaderText.parse(text)
        assertEquals("37.1m",totals.damage);assertEquals("111m",totals.health)
    }
}
