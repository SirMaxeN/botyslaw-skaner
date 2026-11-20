package eu.komars.forgescanner.battle

/** Extract only values attached to stat labels; forge level and player power are not stats. */
object BattleHeaderText {
    data class Totals(val damage:String?,val health:String?)
    private const val NUMBER="(\\d+(?:[.,]\\d+)?\\s*[kmbt]?)"
    fun parse(text:String):Totals {
        val n=BattleText.normalize(text)
        fun value(label:String):String? {
            fun clean(match:MatchResult)=match.groupValues[1].replace(Regex("\\s+"),"")
            // The game writes "<value> Łączne / obrażenia" over two lines. In the war-day profile view the player's
            // power ("927m") is read between the two words: the value belongs to "Łączne", never to the number next
            // to the label (diagnostics 2026-11-18: 18 profiles had damage = power, ranking 2026-11-20 broken).
            // OCR noise seen there too: "Łaączne", a stray letter or symbol before the power ("N 824m", "&337m", "-2b").
            val split=Regex("(?<![\\p{L}\\d.,])$NUMBER\\s*la{1,2}c?zne\\s*(?:[a-z]{1,2}\\s+|[^\\p{L}\\d\\s]{1,2}\\s*)?(?:\\d+(?:[.,]\\d+)?\\s*[kmbt]?\\s*)?$label\\b").findAll(n).map(::clean).toList()
            if(split.isNotEmpty())return split.singleOrNull()?.takeIf { (BattleText.number(it) ?: 0.0)>0 }
            val pattern=Regex("(?<![\\p{L}\\d.,])$NUMBER\\s*(?:la{1,2}c?zne\\s*)?$label\\b")
            val values=pattern.findAll(n).map(::clean).toList()
            return values.singleOrNull()?.takeIf { (BattleText.number(it) ?: 0.0)>0 }
        }
        return Totals(value("obrazenia"),value("zdrowie"))
    }

    /**
     * Total damage and health are a small part of the displayed power (2-20 % in every clan scan so far). A value
     * at half of the power or more is the power itself or a misread unit, never a real stat.
     */
    fun plausible(damage:Double,health:Double,power:Double):Boolean =
        damage>0&&health>0&&(power<=0||(damage<power*.5&&health<power*.5))
}
