package eu.komars.forgescanner.battle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecordingBattleTest {
    @Test fun confirmsTotalsAndBonusPagesWithoutCombiningTwoProfiles() {
        val reader = RecordingBattleReader()
        val first = "37.1m Łączne obrażenia\n111m Łączne zdrowie\n+65.4% Szansa na cios krytyczny"
        assertNull(reader.observe(first))
        val totals = assertNotNull(reader.observe(first))
        assertEquals("37.1m", totals.damage)
        assertEquals("111m", totals.health)
        assertEquals(mapOf("CRIT" to 65.4), totals.bonuses)

        val nextPage = "37.1m Łączne obrażenia\n111m Łączne zdrowie\n+18.9% Wysysanie życia"
        assertEquals(totals.bonuses, reader.observe(nextPage)?.bonuses)
        assertEquals(mapOf("CRIT" to 65.4, "STEAL" to 18.9), reader.observe(nextPage)?.bonuses)

        repeat(2) { assertNull(reader.observe("68m Łączne obrażenia\n280m Łączne zdrowie\n+30% Szansa na blok")) }
        assertEquals(2, reader.observe(first)?.bonuses?.size)
    }

    @Test fun bridgesCorrectedProfilesToSharedEngineAndRejectsInvalidSkillLevels() {
        val player = RecordingBattleInput("68m", "280m", true, emptyMap(), emptyMap(),
            mapOf("LIGHTNING" to "12"), 1, true, "", 0, "", "")
        val rival = player.copy(damage = "1", health = "100", ranged = false, skills = emptyMap())
        val battle = assertNotNull(RecordingBattleSimulator.simulate(player, rival).result)
        assertEquals(battle.count, battle.wins)
        val invalid = RecordingBattleSimulator.simulate(player.copy(skills = mapOf("LIGHTNING" to "0")), rival)
        assertNull(invalid.result)
        assertTrue(invalid.error.orEmpty().contains("poziom"))
    }
}
