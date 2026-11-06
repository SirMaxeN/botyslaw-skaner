package eu.komars.forgescanner.battle

import java.text.Normalizer
import kotlin.random.Random

enum class Bonus(val label: String, vararg val aliases: String) {
    CRIT_REDUCTION("Redukcja otrzymywanych obrażeń krytycznych", "redukcja otrzymywanych obrazen krytycznych", "redukcja obrazen krytycznych", "incoming critical damage reduction"),
    FIFTH_ATTACK("Obrażenia co piąty atak", "obrazenia co piaty atak", "obrazenia co 5", "damage on every 5th attack"),
    REFLECT("Szansa na odbicie", "szansa na odbicie", "szansa odbicia", "reflect chance"),
    CRIT("Szansa na cios krytyczny", "szansa na cios krytyczny"),
    CRIT_DAMAGE("Obrażenia krytyczne", "obrazenia krytyczne"),
    BLOCK("Szansa na blok", "szansa na blok"),
    REGEN("Regeneracja zdrowia", "regeneracja zdrowia"),
    STEAL("Wysysanie życia", "wysysanie zycia"),
    DOUBLE("Szansa na podwójnie", "szansa na podwoj"),
    DAMAGE("Obrażenia", "obrazenia"),
    MELEE("Wręcz obrażenia", "wrecz obrazenia", "obrazenia wrecz"),
    RANGED("Dystansowe obrażenia", "dystansow", "obrazenia dystans"),
    SPEED("Prędkość ataku", "predkosc ataku"),
    HEALTH("Zdrowie", "zdrowie"),
    RANGE("Zasięg ataku", "zasieg ataku", "zasieg", "attack range"),
    SKILL_DAMAGE("Obrażenia umiejętności", "obrazenia umiejetnosci"),
    SKILL_COOLDOWN("Redukcja czasu odnowienia", "odnowienia umiejetnosci", "umiejetnosc czas", "czas odnowienia");
}

data class BattleProfile(val name: String, val damage: Double, val health: Double,
    val ranged: Boolean, val bonuses: Map<Bonus, Double>, val scannedAt: Long = System.currentTimeMillis(),
    val raw: String = "", val caps: Map<Bonus, Double> = emptyMap(),
    val skills:List<EquippedSkill> = emptyList(),val pets:Int=0,val mounted:Boolean=false,val skillMastery:Double=0.0,val fairy:String="",val fairyLevel:Int=0) {
    fun bonus(b: Bonus) = minOf(bonuses[b] ?: 0.0, caps[b] ?: Double.POSITIVE_INFINITY) / 100.0
}

object BattleText {
    fun normalize(s: String): String = Normalizer.normalize(s.lowercase().replace('ł', 'l'), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "").replace(Regex("\\s+"), " ").trim()
    fun number(s: String): Double? = Regex("^\\+?([0-9]+(?:[.,][0-9]+)?)\\s*([kmbt]?)$", RegexOption.IGNORE_CASE)
        .matchEntire(s.trim())?.let { m ->
            m.groupValues[1].replace(',', '.').toDoubleOrNull()?.times(when(m.groupValues[2].lowercase()) {
                "k" -> 1e3; "m" -> 1e6; "b" -> 1e9; "t" -> 1e12; else -> 1.0
            })?.takeIf { it.isFinite() && it >= 0 }
        }
    fun displayedNumber(s: String): String? = Regex("(?<![\\p{L}\\d])\\d+(?:[.,]\\d+)?\\s*[kmbt]?(?![\\p{L}\\d])", RegexOption.IGNORE_CASE)
        .findAll(s).map { it.value.trim() }.toList().singleOrNull()
    fun bonuses(text: String): Map<Bonus, Double> {
        val result = linkedMapOf<Bonus, Double>()
        text.replace('−','-').lines().forEach { line ->
            val n = normalize(line)
            val value = Regex("^\\s*[+-]?([0-9]+(?:[.,][0-9]+)?)\\s*%").find(n)
                ?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull() ?: return@forEach
            val label = n.substringAfter('%').substringBefore('(').trim()
            val key = Bonus.entries.filter { b -> b.aliases.any { if(b==Bonus.DAMAGE || b==Bonus.HEALTH) label==it else label.contains(it) } }
                .maxByOrNull { b -> b.aliases.filter { label.contains(it) }.maxOf { it.length } }
            if (key != null) result[key] = value
        }
        text.lines().forEach { line ->
            val n=normalize(line)
            val m=Regex("^\\s*[+]?(\\d+(?:[.,]\\d+)?)\\s+(?:zasieg(?: ataku)?|attack range)\\s*$").find(n)
            if(m!=null) result[Bonus.RANGE]=m.groupValues[1].replace(',','.').toDouble()
            val after=Regex("^(?:zasieg(?: ataku)?|attack range)\\s*[+:]?\\s*(\\d+(?:[.,]\\d+)?)\\s*$").find(n)
            if(after!=null) result[Bonus.RANGE]=after.groupValues[1].replace(',','.').toDouble()
        }
        return result
    }
    fun completeBonuses(text:String, existing:Map<Bonus,Double> = emptyMap()):Map<Bonus,Double> {
        val parsed=bonuses(text)
        return Bonus.entries.associateWith { key -> existing[key] ?: parsed[key] ?: 0.0 }
    }
    fun playerName(text:String):String = text.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
        .trim().trimEnd { it.isWhitespace() || it in "♂♀⚧\uFE0F" }
    fun weapon(text: String): Boolean? {
        val rows=text.lines()
        val base=rows.indexOfFirst { val n=normalize(it); (n.contains("obrazenia") || n.contains("damage")) && !n.contains('%') }
        val relevant=if(base>=0) rows.drop(base).takeWhile { !it.contains('%') }.joinToString(" ") else text
        val n = normalize(relevant)
        if (Regex("\\bdystans(?:owy)?\\b").containsMatchIn(n)) return true
        // A damaged suffix or empty title must never silently become melee.
        if (n.count(Char::isLetter) < 3 || n.contains("dyst")) return null
        return false
    }
    fun caps(text: String): Map<Bonus, Double> {
        val result=linkedMapOf<Bonus,Double>(); var previous:Bonus?=null
        text.lines().forEach { line ->
            bonuses(line).keys.singleOrNull()?.let { previous=it }
            val match=Regex("(?:maks\\.?|max\\.?)\\s*([0-9]+(?:[.,][0-9]+)?)\\s*%").find(normalize(line))
            if(match!=null && previous!=null) result[previous!!]=match.groupValues[1].replace(',','.').toDouble()
        }
        return result
    }
}

data class WarBonus(val damage: Double = 0.0, val health: Double = 0.0)
data class BattleResult(val wins: Int, val losses: Int, val draws: Int, val count: Int)

/** Independent experimental implementation of publicly documented optimizer mechanics.
 * Profile sums include static bonuses by default. Pre-bonus mode is an explicit comparison option.
 * Bonuses are percent points, not optimizer equipment-slot counts. */
object BattleEngine {
    private val thresholds = doubleArrayOf(0.0,.071,.154,.25,.364,.5,.667,.875,1.14,1.5,2.0,2.75,4.0)
    fun interval(speed: Double): Double = (17 - thresholds.indexOfLast { speed >= it }.coerceAtLeast(0)) / 10.0
    fun hit(p: BattleProfile, war: WarBonus = WarBonus(), applyStaticBonuses: Boolean = false): Double = p.damage *
        (if(applyStaticBonuses) (1+p.bonus(Bonus.DAMAGE))*(1+p.bonus(if(p.ranged) Bonus.RANGED else Bonus.MELEE)) else 1.0) * (1+war.damage/100)
    fun simulate(player: BattleProfile, opponent: BattleProfile, pw: WarBonus = WarBonus(),
        ow: WarBonus = WarBonus(), n: Int = 1000, random: Random = Random.Default, applyStaticBonuses: Boolean = false): BattleResult {
        require(n in 1..10000)
        listOf(player, opponent).forEach { p ->
            require(p.damage.isFinite() && p.damage > 0 && p.health.isFinite() && p.health > 0)
            require(p.bonuses.values.all { it.isFinite() && it in 0.0..100000.0 })
        }
        listOf(pw, ow).forEach { require(it.damage.isFinite() && it.health.isFinite() && it.damage in 0.0..100000.0 && it.health in 0.0..100000.0) }
        return PvpBattleEngine.simulate(player,opponent,pw,ow,n,random,applyStaticBonuses)
    }
}
