package eu.komars.forgescanner.model

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class GameNumberParserTest {
    @Test fun readsAbbreviationsAndGrouping() {
        assertEquals(9880L, GameNumberParser.parse("9.88k")!!.wholeValueOrNull())
        assertEquals(9880L, GameNumberParser.parse("9,88K")!!.wholeValueOrNull())
        assertEquals(2840L, GameNumberParser.parse("2.84k")!!.wholeValueOrNull())
        assertEquals(1_250_000L, GameNumberParser.parse("1,25m")!!.wholeValueOrNull())
        assertEquals(11026L, GameNumberParser.parse("11 026")!!.wholeValueOrNull())
        assertEquals(11026L, GameNumberParser.parse("11\u202f026")!!.wholeValueOrNull())
        assertEquals(1080L, GameNumberParser.parse("1,080")!!.wholeValueOrNull())
    }

    @Test fun doesNotInventImplicitThousandsFromOcr() {
        assertEquals(0, BigDecimal("9.88").compareTo(GameNumberParser.parse("9.88")!!.value))
        assertNull(GameNumberParser.parse("9.88")!!.wholeValueOrNull())
        assertEquals(188L, GameNumberParser.parse("188")!!.wholeValueOrNull())
        assertEquals(0L, GameNumberParser.parse("0")!!.wholeValueOrNull())
    }

    @Test fun rejectsUnsafeAndMalformedValues() {
        listOf("-10", "NaN", "Infinity", "1e10", "3+2", "9kfoo", "1.2.3", "1 2 3", "9999999999999999999999").forEach {
            assertNull("Accepted $it", GameNumberParser.parse(it))
        }
    }
}
