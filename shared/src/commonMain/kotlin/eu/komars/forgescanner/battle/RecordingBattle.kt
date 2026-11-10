package eu.komars.forgescanner.battle

data class RecordingChoice(val key: String, val title: String)
data class RecordingBattleStats(val damage: String, val health: String, val bonuses: Map<String, Double>, val caps: Map<String, Double>)

/** Capture one profile per broadcast. Accumulate only values repeated on the same visible profile. */
class RecordingBattleReader {
    private var previous: BattleHeaderText.Totals? = null
    private var current: BattleHeaderText.Totals? = null
    private var candidateBonuses = emptyMap<Bonus, Double>()
    private var candidateCaps = emptyMap<Bonus, Double>()
    private val bonuses = linkedMapOf<Bonus, Double>()
    private val caps = linkedMapOf<Bonus, Double>()

    fun observe(text: String): RecordingBattleStats? {
        val totals = BattleHeaderText.parse(text)
        if (totals.damage == null || totals.health == null || (current != null && current != totals)) {
            previous = null
            candidateBonuses = emptyMap()
            candidateCaps = emptyMap()
            return null
        }
        val latest = BattleText.bonuses(text)
        val latestCaps = BattleText.caps(text)
        if (previous == totals) {
            current = totals
            bonuses.putAll(latest.filter { (key, value) -> candidateBonuses[key] == value })
            caps.putAll(latestCaps.filter { (key, value) -> candidateCaps[key] == value })
        }
        previous = totals
        candidateBonuses = latest
        candidateCaps = latestCaps
        if (current == null) return null
        return RecordingBattleStats(totals.damage, totals.health, bonuses.mapKeys { it.key.name }, caps.mapKeys { it.key.name })
    }
}

data class RecordingBattleInput(
    val damage: String, val health: String, val ranged: Boolean,
    val bonuses: Map<String, String>, val caps: Map<String, String>, val skills: Map<String, String>,
    val pets: Int, val mounted: Boolean, val skillMastery: String, val skillAscension: Int,
    val fairy: String, val fairyLevel: String
)

object RecordingBattleSimulator {
    fun bonusChoices(): List<RecordingChoice> = Bonus.entries.map { RecordingChoice(it.name, it.label) }
    fun skillChoices(): List<RecordingChoice> = BattleSkill.entries.map { RecordingChoice(it.name, it.label) }

    private fun profile(name: String, input: RecordingBattleInput): BattleProfile {
        fun stat(text: String) = BattleText.number(text.trim())?.takeIf { it.isFinite() && it > 0 }
            ?: error("Sprawdź obrażenia i zdrowie.")
        fun percent(text: String): Double = text.trim().replace(',', '.').removeSuffix("%").toDoubleOrNull()
            ?.takeIf { it.isFinite() && it in 0.0..100000.0 } ?: error("Sprawdź wartości procentowe.")
        fun modifiers(values: Map<String, String>) = values.filterValues { it.isNotBlank() }
            .map { (key, value) -> Bonus.valueOf(key) to percent(value) }.toMap()
        val skills = input.skills.map { (key, value) ->
            EquippedSkill(BattleSkill.valueOf(key), value.trim().toIntOrNull()?.takeIf { it in 1..100 }
                ?: error("Podaj poziom każdej wybranej umiejętności (1–100)."))
        }
        val fairyLevel = if (input.fairy.isEmpty()) 0 else input.fairyLevel.trim().toIntOrNull()?.takeIf { it in 1..20 }
            ?: error("Podaj poziom wróżki (1–20).")
        return BattleProfile(name, stat(input.damage), stat(input.health), input.ranged, modifiers(input.bonuses),
            caps = modifiers(input.caps), skills = skills, pets = input.pets, mounted = input.mounted,
            skillMastery = if (input.skillMastery.isBlank()) 0.0 else percent(input.skillMastery),
            skillAscension = input.skillAscension, fairy = input.fairy, fairyLevel = fairyLevel)
    }

    data class Outcome(val result: BattleResult?, val error: String?)

    fun simulate(player: RecordingBattleInput, opponent: RecordingBattleInput): Outcome = try {
        Outcome(PvpBattleEngine.simulate(profile("Moja postać", player), profile("Przeciwnik", opponent)), null)
    } catch (e: IllegalArgumentException) {
        Outcome(null, e.message ?: "Sprawdź dane obu postaci.")
    } catch (e: IllegalStateException) {
        Outcome(null, e.message ?: "Sprawdź dane obu postaci.")
    }
}
