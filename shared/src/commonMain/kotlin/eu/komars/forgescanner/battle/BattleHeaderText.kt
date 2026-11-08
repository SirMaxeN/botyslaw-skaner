package eu.komars.forgescanner.battle

/** Extract only values attached to stat labels; forge level and player power are not stats. */
object BattleHeaderText {
    data class Totals(val damage:String?,val health:String?)
    fun parse(text:String):Totals {
        val n=BattleText.normalize(text)
        fun value(label:String):String? {
            val pattern=Regex("(?<![\\p{L}\\d.,])(\\d+(?:[.,]\\d+)?\\s*[kmbt]?)\\s*(?:laczne\\s*)?$label\\b")
            val values=pattern.findAll(n).map { it.groupValues[1].replace(Regex("\\s+"),"") }.toList()
            return values.singleOrNull()?.takeIf { (BattleText.number(it) ?: 0.0)>0 }
        }
        return Totals(value("obrazenia"),value("zdrowie"))
    }
}
