package eu.komars.forgescanner.model

/**
 * Exact non-negative decimal as shown by the game: integer digits and fraction digits, then × [multiplier].
 * Plain Kotlin (no BigDecimal) so the parser also runs on iOS; GameNumberParserParityTest pins it to the
 * former BigDecimal implementation.
 */
class GameDecimal constructor(private val integer: String, private val fraction: String, private val multiplierDigits: Int) {
    /** Exact value when it is a whole number within [0, ScanSession.MAX_VALUE]; null otherwise. */
    fun wholeOrNull(): Long? {
        if (fraction.length > multiplierDigits) return null
        val whole = (integer + fraction + "0".repeat(multiplierDigits - fraction.length)).trimStart('0')
        if (whole.isEmpty()) return 0L
        if (whole.length > 18) return null
        return whole.toLong().takeIf { it in 0..ScanSession.MAX_VALUE }
    }

    /** True when the exact value is greater than [limit]. */
    fun exceeds(limit: Long): Boolean {
        val shifted = integer + fraction.take(multiplierDigits).padEnd(multiplierDigits, '0')
        val wholePart = shifted.trimStart('0')
        val remainder = fraction.length > multiplierDigits // trailing zeros are trimmed, so any extra digit is non-zero
        if (wholePart.length > 18) return true
        val w = if (wholePart.isEmpty()) 0L else wholePart.toLong()
        return w > limit || (w == limit && remainder)
    }

    /** Canonical plain decimal string of the exact value (e.g. "9.88", "9880"). */
    fun toPlainString(): String {
        val digits = integer + fraction
        val scale = fraction.length - multiplierDigits
        val raw = if (scale <= 0) digits + "0".repeat(-scale) else digits.padStart(scale + 1, '0').let { it.dropLast(scale) + "." + it.takeLast(scale) }
        val trimmed = raw.trimStart('0').let { if (it.isEmpty() || it.startsWith(".")) "0$it" else it }
        return trimmed
    }

    override fun equals(other: Any?) = other is GameDecimal && other.toPlainString() == toPlainString()
    override fun hashCode() = toPlainString().hashCode()
    override fun toString() = toPlainString()
}

data class ParsedGameNumber(val value: GameDecimal, val abbreviated: Boolean, val source: String) {
    fun wholeValueOrNull(): Long? = value.wholeOrNull()
}

/** Parses what the game displays. A bare decimal is NEVER silently multiplied by 1000. */
object GameNumberParser {
    private val complete = Regex("^([0-9][0-9., \\u00a0\\u202f]*)([kKmMbB]?)$")
    val tokenPattern = Regex("(?<![\\p{L}\\d])\\d+(?:[.,]\\d+|[ \\u00a0\\u202f]\\d{3})*\\s*[kKmMbB]?(?![\\p{L}\\d])")
    private val plainDecimal = Regex("^([0-9]+)(?:\\.([0-9]*))?$")

    fun parse(raw: String): ParsedGameNumber? {
        val match = complete.matchEntire(raw.trim()) ?: return null
        val suffix = match.groupValues[2].lowercase()
        val rawBody = match.groupValues[1].trim()
        if (rawBody.any { it == ' ' || it == ' ' || it == ' ' } &&
            !Regex("^[0-9]{1,3}(?:[ \\u00a0\\u202f][0-9]{3})+(?:[.,][0-9]+)?$").matches(rawBody)) return null
        val body = rawBody.replace(Regex("[ \\u00a0\\u202f]"), "")
        val normalized = normalize(body, suffix.isNotEmpty()) ?: return null
        val number = plainDecimal.matchEntire(normalized) ?: return null
        val multiplierDigits = when (suffix) {
            "k" -> 3
            "m" -> 6
            "b" -> 9
            else -> 0
        }
        val value = GameDecimal(number.groupValues[1], number.groupValues[2].trimEnd('0'), multiplierDigits)
        if (value.exceeds(ScanSession.MAX_VALUE)) return null
        return ParsedGameNumber(value, suffix.isNotEmpty(), raw.trim())
    }

    private fun normalize(body: String, abbreviated: Boolean): String? {
        if (body.isEmpty()) return null
        val separators = body.filter { it == ',' || it == '.' }
        if (separators.isEmpty()) return body
        if (!abbreviated && Regex("^[0-9]{1,3}([,.][0-9]{3})+$").matches(body) && separators.toSet().size == 1) {
            return body.replace(",", "").replace(".", "")
        }
        if (separators.length == 1) return body.replace(',', '.')
        // Mixed locale formatting is accepted only with unambiguous grouped thousands.
        val decimal = body.last { it == ',' || it == '.' }
        val grouping = if (decimal == ',') '.' else ','
        val parts = body.split(decimal)
        if (parts.size != 2 || parts[1].isEmpty() || !parts[1].all(Char::isDigit)) return null
        val integer = parts[0]
        if (!Regex("^[0-9]{1,3}(\\${grouping}[0-9]{3})+$").matches(integer)) return null
        return integer.replace(grouping.toString(), "") + "." + parts[1]
    }
}
