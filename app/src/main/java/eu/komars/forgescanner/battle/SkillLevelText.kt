package eu.komars.forgescanner.battle

object SkillLevelText {
    fun parse(text:String):Int? {
        val values=Regex("(?:poz[.,]?|poziom|level|lv[.,]?)\\s*[:.,]?\\s*([0-9]{1,3})(?![0-9])",RegexOption.IGNORE_CASE)
            .findAll(text).mapNotNull { it.groupValues[1].toIntOrNull()?.takeIf { v -> v in 1..100 } }.distinct().toList()
        return values.singleOrNull()
    }
}
