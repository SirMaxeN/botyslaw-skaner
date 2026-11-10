package eu.komars.forgescanner.model

/**
 * Sections of the clan research tree, from the bold headers between the grey panels ("Moc", "Zegary jaj",
 * "Wojna klanów", "Klanowe mikstury techniczne", …). A node belongs to the nearest header above it; nodes above
 * the first header on screen continue the section that was last seen on the previous page.
 */
object ClanTechSections {
    enum class Section { WAR, POTIONS, OTHER }
    data class Header(val y: Int, val section: Section)

    /** OCR line → section header, or null for anything that is not a header (levels "0/10", "Maks.", numbers). */
    fun header(text: String): Section? {
        val t = text.trim().lowercase()
        if (t.count { it.isLetter() } < 3 || t.any { it.isDigit() || it == '/' || it == '%' } || Regex("ma[kx]s").containsMatchIn(t)) return null
        return when {
            Regex("wojn\\w*\\s+klan").containsMatchIn(t) -> Section.WAR
            Regex("mikstur").containsMatchIn(t) -> Section.POTIONS
            else -> Section.OTHER
        }
    }

    fun sectionOf(nodeTop: Int, headers: List<Header>, carried: Section?): Section? =
        headers.filter { it.y < nodeTop }.maxByOrNull { it.y }?.section ?: carried

    /** Section still open at the bottom of this page (carried to the next page). */
    fun last(headers: List<Header>, carried: Section?): Section? = headers.maxByOrNull { it.y }?.section ?: carried

    /** Which tracked icons may match in a section: the 16 war-point icons in "Wojna klanów", the 2 potions in "Klanowe mikstury techniczne". */
    fun allowed(section: Section?, trackedIndex: Int, warCount: Int = 16): Boolean = when (section) {
        Section.WAR -> trackedIndex < warCount
        Section.POTIONS -> trackedIndex >= warCount
        else -> false
    }
}
