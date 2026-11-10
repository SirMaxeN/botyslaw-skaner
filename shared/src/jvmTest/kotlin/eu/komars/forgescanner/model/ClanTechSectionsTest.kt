package eu.komars.forgescanner.model

import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals

/** User screenshots of the two research sections (local only). Pads them into a phone-shaped frame. */
class ClanTechSectionsTest {
    companion object {
        fun frame(name: String): Triple<Int, IntArray, List<ClanTechNodes.Node>>? {
            val file = File(System.getProperty("user.dir")).resolve("../.local/clan-tech/$name")
            if (!file.exists()) return null
            val crop = ImageIO.read(file)
            val w = maxOf(380, crop.width); val h = maxOf(w * 2, crop.height + 400); val p = IntArray(w * h) { 0xFFF2F2F2.toInt() }
            for (y in 0 until crop.height) for (x in 0 until crop.width) p[(y + 200) * w + x] = crop.getRGB(x, y) or (0xFF shl 24).toInt()
            return Triple(w, p, ClanTechNodes.find(w, h, p))
        }
    }

    @Test fun warSectionHasTwentyNodesAndPotionSectionSix() {
        frame("section-war.png")?.let { (_, _, nodes) -> assertEquals(20, nodes.size, nodes.joinToString { "${it.disc}" }) }
        frame("section-potions.png")?.let { (_, _, nodes) -> assertEquals(6, nodes.size, nodes.joinToString { "${it.disc}" }) }
    }
}
