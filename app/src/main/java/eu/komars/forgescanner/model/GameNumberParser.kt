package eu.komars.forgescanner.model

import java.math.BigDecimal
import java.math.RoundingMode

data class ParsedGameNumber(val value: BigDecimal, val abbreviated: Boolean, val source: String) {
    fun wholeValueOrNull(): Long? = try {
        value.setScale(0, RoundingMode.UNNECESSARY).longValueExact().takeIf { it in 0..ScanSession.MAX_VALUE }
    } catch (_: ArithmeticException) { null }
}

/** Parses what the game displays. A bare decimal is NEVER silently multiplied by 1000. */
object GameNumberParser {
    private val complete = Regex("^([0-9][0-9., \\u00a0\\u202f]*)([kKmMbB]?)$")
    val tokenPattern = Regex("(?<![\\p{L}\\d])\\d+(?:[.,]\\d+|[ \\u00a0\\u202f]\\d{3})*\\s*[kKmMbB]?(?![\\p{L}\\d])")

    fun parse(raw: String): ParsedGameNumber? {
        val match = complete.matchEntire(raw.trim()) ?: return null
        val suffix = match.groupValues[2].lowercase()
        val rawBody = match.groupValues[1].trim()
        if (rawBody.any { it == ' ' || it == '\u00a0' || it == '\u202f' } &&
            !Regex("^[0-9]{1,3}(?:[ \\u00a0\\u202f][0-9]{3})+(?:[.,][0-9]+)?$").matches(rawBody)) return null
        val body = rawBody.replace(Regex("[ \\u00a0\\u202f]"), "")
        val normalized = normalize(body, suffix.isNotEmpty()) ?: return null
        val number = normalized.toBigDecimalOrNull() ?: return null
        val multiplier = when (suffix) {
            "k" -> BigDecimal(1000)
            "m" -> BigDecimal(1_000_000)
            "b" -> BigDecimal(1_000_000_000)
            else -> BigDecimal.ONE
        }
        val value = number.multiply(multiplier)
        if (value.signum() < 0 || value > BigDecimal.valueOf(ScanSession.MAX_VALUE)) return null
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
