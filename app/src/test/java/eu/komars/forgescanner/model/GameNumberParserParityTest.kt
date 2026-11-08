package eu.komars.forgescanner.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.random.Random

/** Pins the plain-Kotlin parser (shared with iOS) to the former BigDecimal implementation, bit for bit. */
class GameNumberParserParityTest {
    private object Legacy {
        private val complete = Regex("^([0-9][0-9., \\u00a0\\u202f]*)([kKmMbB]?)$")
        fun parse(raw: String): Triple<BigDecimal, Boolean, Long?>? {
            val match = complete.matchEntire(raw.trim()) ?: return null
            val suffix = match.groupValues[2].lowercase()
            val rawBody = match.groupValues[1].trim()
            if (rawBody.any { it == ' ' || it == ' ' || it == ' ' } &&
                !Regex("^[0-9]{1,3}(?:[ \\u00a0\\u202f][0-9]{3})+(?:[.,][0-9]+)?$").matches(rawBody)) return null
            val body = rawBody.replace(Regex("[ \\u00a0\\u202f]"), "")
            val normalized = normalize(body, suffix.isNotEmpty()) ?: return null
            val number = normalized.toBigDecimalOrNull() ?: return null
            val multiplier = when (suffix) { "k" -> BigDecimal(1000); "m" -> BigDecimal(1_000_000); "b" -> BigDecimal(1_000_000_000); else -> BigDecimal.ONE }
            val value = number.multiply(multiplier)
            if (value.signum() < 0 || value > BigDecimal.valueOf(ScanSession.MAX_VALUE)) return null
            val whole = try { value.setScale(0, RoundingMode.UNNECESSARY).longValueExact().takeIf { it in 0..ScanSession.MAX_VALUE } } catch (_: ArithmeticException) { null }
            return Triple(value, suffix.isNotEmpty(), whole)
        }
        private fun normalize(body: String, abbreviated: Boolean): String? {
            if (body.isEmpty()) return null
            val separators = body.filter { it == ',' || it == '.' }
            if (separators.isEmpty()) return body
            if (!abbreviated && Regex("^[0-9]{1,3}([,.][0-9]{3})+$").matches(body) && separators.toSet().size == 1) return body.replace(",", "").replace(".", "")
            if (separators.length == 1) return body.replace(',', '.')
            val decimal = body.last { it == ',' || it == '.' }
            val grouping = if (decimal == ',') '.' else ','
            val parts = body.split(decimal)
            if (parts.size != 2 || parts[1].isEmpty() || !parts[1].all(Char::isDigit)) return null
            val integer = parts[0]
            if (!Regex("^[0-9]{1,3}(\\${grouping}[0-9]{3})+$").matches(integer)) return null
            return integer.replace(grouping.toString(), "") + "." + parts[1]
        }
    }

    private fun check(input: String) {
        val old = Legacy.parse(input)
        val new = GameNumberParser.parse(input)
        assertEquals("null-ness of '$input'", old == null, new == null)
        if (old == null || new == null) return
        assertEquals("abbreviated '$input'", old.second, new.abbreviated)
        assertEquals("whole '$input'", old.third, new.wholeValueOrNull())
        assertEquals("exact '$input'", 0, old.first.compareTo(BigDecimal(new.value.toPlainString())))
    }

    @Test fun matchesLegacyOnHandPickedCases() {
        listOf("0", "00", "007", "9.88", "9.88k", "9,88K", "12.", "12,", "12.k", "1,080", "1.080", "1.080k", "11 026", "11 026", "1,25m",
            "9000000000000", "9000000000001", "9000000000000.1", "9000000000000.0", "9000b", "9000.000000001b", "9000.0000000001b",
            "8999.9999999999b", "0.0000000001", "0.5k", "0.0005k", "1.234.567", "1,234,567", "1.234,5", "1,234.5", "1.2.3", "99999999999999999999",
            "000000000000000000000001", "1.000000000000000000000000k", " 42 ", "42 k", "-1", "1e3", "").forEach(::check)
    }

    @Test fun matchesLegacyOnRandomInputs() {
        val random = Random(20261108)
        val alphabet = "0123456789000000.,.,    kKmMbB"
        repeat(300_000) {
            val length = 1 + random.nextInt(16)
            check(buildString { repeat(length) { append(alphabet[random.nextInt(alphabet.length)]) } })
        }
    }
}
