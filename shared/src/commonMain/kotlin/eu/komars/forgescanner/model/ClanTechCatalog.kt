package eu.komars.forgescanner.model

/**
 * Names, stable keys and level rules of the 18 tracked research nodes, in the order of [ClanTechPresets.tracked]
 * (16 "Wojna klanów" war-point nodes, then the 2 green-potion nodes of "Klanowe mikstury techniczne").
 * The node is known from its icon, so its name never depends on OCR of the game's decorative font.
 * Per-level bonus and max level: forgemaster.pro clan tech (game 2.11) — war actions/days +4% (max 10),
 * clan tech race nodes +5% (max 20).
 */
object ClanTechCatalog {
    data class Entry(val key: String, val name: String, val stepPercent: Double, val maxLevel: Int)

    val entries: List<Entry> = listOf(
        Entry("war_forging", "Kucie wyposażenia", 4.0, 10),
        Entry("war_skill_summon", "Przyzywanie umiejętności", 4.0, 10),
        Entry("war_skill_upgrade", "Ulepszanie umiejętności", 4.0, 10),
        Entry("war_tech_tree", "Drzewo technologii", 4.0, 10),
        Entry("war_forge_upgrades", "Ulepszenia kuźni", 4.0, 10),
        Entry("war_dungeon_keys", "Klucze do lochów", 4.0, 10),
        Entry("war_eggs", "Wykluwanie jaj", 4.0, 10),
        Entry("war_pet_merge", "Łączenie zwierzaków", 4.0, 10),
        Entry("war_mount_summon", "Przyzywanie wierzchowców", 4.0, 10),
        Entry("war_mount_merge", "Łączenie wierzchowców", 4.0, 10),
        Entry("war_day_1", "Wojna klanów: dzień 1", 4.0, 10),
        Entry("war_day_2", "Wojna klanów: dzień 2", 4.0, 10),
        Entry("war_day_3", "Wojna klanów: dzień 3", 4.0, 10),
        Entry("war_day_4", "Wojna klanów: dzień 4", 4.0, 10),
        Entry("war_day_5", "Wojna klanów: dzień 5", 4.0, 10),
        Entry("war_day_6", "Wojna klanów: dzień 6", 4.0, 10),
        Entry("race_rewards", "Mikstury z wyścigu klanu", 5.0, 20),
        Entry("race_donations", "Wyścig technologii klanu (darowizny)", 5.0, 20),
    )

    fun entry(index: Int): Entry? = entries.getOrNull(index)

    fun labelLevel(index:Int,text:String):Int? {
        val e=entry(index)?:return null
        return ClanTechNodes.level(text)?.takeIf {it.second==e.maxLevel}?.first
            ?:e.maxLevel.takeIf {ClanTechNodes.maxed(text)}
    }

    /** Ignore cost/progress fractions and the increase for the next level in parentheses. */
    fun level(index: Int, labels: List<String>, window: String): Int? {
        val e = entry(index) ?: return null
        fun fraction(text:String) = ClanTechNodes.level(text)?.takeIf { it.second == e.maxLevel }?.first
        val readings = labels.mapNotNull {labelLevel(index,it)}
        val listed = readings.distinct().singleOrNull() ?: return null
        val description = currentDescription(window)
        val values = percentages(description)
        val percent = levelFromPercent(e, values)
        if(values.isNotEmpty() && percent==null)return null
        val detailed = percent ?: fraction(description)
        // Some panels list only the increase for the next level. In that case use
        // two agreeing reads of the isolated label, in different contrast variants.
        return listed.takeIf { if(detailed!=null)it==detailed else readings.size>=2 }
    }

    /** A verified node can fall back to its popup when the tiny tree label is unreadable. */
    fun levelFromPopupReads(index: Int, labels: List<String>, windows: List<String>): Int? {
        val e = entry(index) ?: return null
        val readings = windows.map { it to percentages(currentDescription(it)) }
        if (readings.any { (_, values) -> values.isNotEmpty() && levelFromPercent(e, values) == null }) return null
        val levels = readings.mapNotNull { (window, values) -> levelFromPercent(e, values)?.let { window to it } }
        val agreed = levels.map { it.second }.distinct().singleOrNull() ?: return null
        val identified = levels.count { (window, _) -> popupMatches(index, window) }
        return agreed.takeIf { identified >= 2 && labels.mapNotNull { labelLevel(index, it) }.all { it == agreed } }
    }

    private fun popupMatches(index: Int, window: String): Boolean {
        if (index in 10..15) return dayMatches(index, window)
        val text = eu.komars.forgescanner.platform.normalizeNfd(window.lowercase())
            .filterNot { it in '\u0300'..'\u036f' }.replace('ł', 'l')
        val words = when (index) {
            0 -> listOf("kuci", "ekwip")
            1 -> listOf("przyzyw", "umiejet")
            2 -> listOf("ulepsz", "umiejet")
            3 -> listOf("drzew", "technolog")
            4 -> listOf("kuzni", "punkt")
            5 -> listOf("klucz", "loch")
            6 -> listOf("wykluw", "jaj")
            7 -> listOf("laczen", "zwierzak")
            8 -> listOf("przyzyw", "wierzchow")
            9 -> listOf("aczen", "wierzchow")
            16 -> listOf("mikstur", "nagrod")
            17 -> listOf("darowizn", "wyscig")
            else -> return false
        }
        return words.all(text::contains)
    }

    private fun currentDescription(window: String) = window.replace(Regex("\\([^)]*\\)"), "")

    private fun percentages(text: String): List<Double> = Regex("(?<![\\p{L}\\d.,])([0-9]+(?:[.,][0-9]+)?|[Oo])\\s*%")
        .findAll(text).mapNotNull { match ->
            val raw = match.groupValues[1]
            if (raw.equals("o", ignoreCase = true)) 0.0 else raw.replace(',', '.').toDoubleOrNull()
        }.distinct().toList()

    private fun levelFromPercent(entry: Entry, values: List<Double>): Int? {
        val value = values.singleOrNull() ?: return null
        val level = (value / entry.stepPercent).toInt()
        return level.takeIf { it in 0..entry.maxLevel && it * entry.stepPercent == value }
    }

    /** Day icons only differ by a digit. Require the same day in the opened description. */
    fun dayMatches(index:Int, window:String):Boolean {
        if(index !in 10..15)return true
        val normalized=window.lowercase().replace('ń','n').replace('ś','s').replace('ó','o')
        val days=Regex("(?:dzien|dnia|dniu|day)\\s*[:.]?\\s*([1-6])(?![0-9])").findAll(normalized)
            .map {it.groupValues[1].toInt()}.distinct().toList()
        return days.singleOrNull()==index-9
    }

    /** Current bonus from the level (the window shows the per-level step, not the total). */
    fun bonus(index: Int, level: Int?): Double? = entry(index)?.let { e -> level?.let { it * e.stepPercent } }
}
