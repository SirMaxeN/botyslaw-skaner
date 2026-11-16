package eu.komars.forgescanner.model

import eu.komars.forgescanner.battle.GameDigitFont
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Leave-one-out over the bundled font: genuine digit shapes must pass the learning filter. */
class GameDigitBundledPlausibilityTest {
    @Test fun genuineDigitSamplesAreKept() {
        val file = File(System.getProperty("user.dir")).resolve("../app/src/main/assets/game_digits_v1.txt")
        if (!file.exists()) return
        val samples = GameDigitFont.decode(file.readText())
        val rejected = samples.indices.filterNot { i -> GameDigitFont.plausible(samples[i], samples.filterIndexed { j, _ -> j != i }) }
        println("bundled=${samples.size} rejected=${rejected.size} digits=${rejected.map { samples[it].digit }}")
        assertTrue(rejected.size * 20 <= samples.size, "more than 5% of genuine samples rejected: ${rejected.size}/${samples.size}")
    }
}
