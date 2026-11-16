package eu.komars.forgescanner.model

import eu.komars.forgescanner.battle.GameDigitFont
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/** Log 2026-11-16: the hammer icon touching "9307" was cut as a first glyph and the strict read gave nothing. */
class GameDigitIconCounterTest {
    @Test fun aLeadingIconThatIsNoDigitIsSkipped() {
        val file = File(System.getProperty("user.dir")).resolve("../app/src/main/assets/game_digits_v1.txt")
        if (!file.exists()) return
        val samples = GameDigitFont.decode(file.readText())
        val h = 42; val top = 16; val w = 260; val height = 75
        val gray = 0xFF808080.toInt(); val white = 0xFFFFFFFF.toInt()
        fun image(icon: Boolean): IntArray {
            val p = IntArray(w * height) { gray }
            var x = 20
            if (icon) {
                // A hammer-like blob: wide head and a diagonal handle, one connected white shape.
                for (y in 0 until 44) for (dx in 0 until 40) {
                    val head = y < 16 && dx in 4..36
                    val handle = y >= 16 && kotlin.math.abs(dx - (12 + (y - 16) / 2)) <= 3
                    if (head || handle) p[(top - 2 + y) * w + x + dx] = white
                }
                x += 40 + 6
            }
            for (digit in listOf(9, 3, 0, 7)) {
                val glyph = samples.first { it.digit == digit }.glyph
                val gw = (glyph.aspect * h).toInt().coerceAtLeast(6)
                for (y in 0 until h) for (dx in 0 until gw) {
                    val bit = glyph.bits[(y * 14 / h) * 10 + dx * 10 / gw]
                    if (bit == '1') p[(top + y) * w + x + dx] = white
                }
                x += gw + 5
            }
            return p
        }
        assertEquals(9307L, GameDigitFont.readCounterShape(w, height, image(icon = false), samples))
        assertEquals(9307L, GameDigitFont.readCounterShape(w, height, image(icon = true), samples))
    }
}
