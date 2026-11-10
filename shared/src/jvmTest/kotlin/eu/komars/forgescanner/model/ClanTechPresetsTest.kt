package eu.komars.forgescanner.model

import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The 18 tracked research icons (16 "Wojna klanów" + 2 green-potion nodes of "Klanowe mikstury techniczne"),
 * stored as fingerprints in [ClanTechPresets]. Regenerate with -DclanTechPresets=write (local screenshots needed).
 */
class ClanTechPresetsTest {
    private val warPicks = (0 until 16).toList()
    private val potionPicks = listOf(4, 5)

    private fun scaled(name: String, scale: Double): Triple<Int, IntArray, List<ClanTechNodes.Node>>? {
        val file = File(System.getProperty("user.dir")).resolve("../.local/clan-tech/$name")
        if (!file.exists()) return null
        val crop = ImageIO.read(file)
        val cw = (crop.width * scale).toInt(); val ch = (crop.height * scale).toInt()
        val w = maxOf(360, cw); val h = maxOf(w * 2, ch + 400); val p = IntArray(w * h) { 0xFFF2F2F2.toInt() }
        for (y in 0 until ch) for (x in 0 until cw) {
            // Box filter: average the source pixels behind each destination pixel (like Bitmap.createScaledBitmap with filtering).
            val x0 = (x / scale).toInt(); val y0 = (y / scale).toInt(); val x1 = minOf(crop.width, ((x + 1) / scale).toInt().coerceAtLeast(x0 + 1)); val y1 = minOf(crop.height, ((y + 1) / scale).toInt().coerceAtLeast(y0 + 1))
            var r = 0; var g = 0; var b = 0; var n = 0
            for (yy in y0 until y1) for (xx in x0 until x1) { val c = crop.getRGB(xx, yy); r += c ushr 16 and 255; g += c ushr 8 and 255; b += c and 255; n++ }
            p[(y + 200) * w + x] = (0xFF shl 24) or ((r / n) shl 16) or ((g / n) shl 8) or (b / n)
        }
        return Triple(w, p, ClanTechNodes.find(w, h, p))
    }

    /** Blue in-progress discs repainted as the green "maxed" disc and the other way round (icon pixels untouched). */
    private fun swapDiscColour(p: IntArray) {
        for (i in p.indices) {
            val c = p[i]; val r = c ushr 16 and 255; val g = c ushr 8 and 255; val b = c and 255
            if (!ClanTechNodes.discColor(c)) continue
            p[i] = if (b > g) (0xFF shl 24) or ((r * 9 / 10) shl 16) or (minOf(255, g + 37) shl 8) or (b * 55 / 100)
            else (0xFF shl 24) or (r shl 16) or (maxOf(0, g - 37) shl 8) or minOf(255, b * 18 / 10)
        }
    }

    @Test fun discColourDoesNotChangeTheMatch() {
        if (ClanTechPresets.tracked.isEmpty()) return
        for ((name, expected) in listOf("section-war.png" to warPicks.associateWith { it }, "section-potions.png" to mapOf(4 to 16, 5 to 17))) {
            val (w, p, _) = scaled(name, .8) ?: return
            swapDiscColour(p)
            val h = p.size / w
            val nodes = ClanTechNodes.find(w, h, p)
            assertEquals(if (name == "section-war.png") 20 else 6, nodes.size, "all discs still detected after the colour swap: $name")
            assertEquals(expected, ClanTechNodes.assign(nodes.map { ClanTechNodes.fingerprint(w, h, p, it.disc) }, ClanTechPresets.tracked), name)
        }
    }

    private fun prints(name: String, scale: Double, picks: List<Int>): List<IntArray>? {
        val (w, p, nodes) = scaled(name, scale) ?: return null
        val h = p.size / w
        return picks.map { ClanTechNodes.fingerprint(w, h, p, nodes[it].disc) }
    }

    @Test fun presetsAreFoundOnTheSectionsAtPhoneScaleAndNothingElseIsTapped() {
        val war = prints("section-war.png", 1.0, warPicks) ?: return
        val potions = prints("section-potions.png", 1.0, potionPicks) ?: return
        val generated = war + potions
        if (System.getProperty("clanTechPresets") == "write") {
            val target = File(System.getProperty("user.dir")).resolve("src/commonMain/kotlin/eu/komars/forgescanner/model/ClanTechPresets.kt")
            target.writeText(ClanTechPresets.source(generated))
        }
        assertEquals(18, ClanTechPresets.tracked.size, "presets present")
        for (scale in listOf(1.0, .8, .64)) {
            val (w, p, nodes) = scaled("section-war.png", scale)!!
            val found = ClanTechNodes.assign(nodes.map { ClanTechNodes.fingerprint(w, p.size / w, p, it.disc) }, ClanTechPresets.tracked)
            assertEquals(warPicks.associateWith { it }, found, "war section at $scale")
            val (w2, p2, nodes2) = scaled("section-potions.png", scale)!!
            val found2 = ClanTechNodes.assign(nodes2.map { ClanTechNodes.fingerprint(w2, p2.size / w2, p2, it.disc) }, ClanTechPresets.tracked)
            assertEquals(mapOf(4 to 16, 5 to 17), found2, "potion section at $scale")
        }
    }
}

/** Real phone frame (1080×2340, "Moc" section, 13 nodes) downscaled to the 360 px analysis width like the app. */
class ClanTechPhoneFrameTest {
    @Test fun powerSectionNodesAreDetectedButNoneIsMistakenForATrackedIcon() {
        val file = File(System.getProperty("user.dir")).resolve("../.local/clan-tech/phone-page0.png")
        if (!file.exists() || ClanTechPresets.tracked.isEmpty()) return
        val full = ImageIO.read(file)
        val w = 360; val h = full.height * w / full.width
        val scaled = java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB)
        scaled.createGraphics().apply { setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR); drawImage(full, 0, 0, w, h, null); dispose() }
        val p = IntArray(w * h) { scaled.getRGB(it % w, it / w) }
        val nodes = ClanTechNodes.find(w, h, p)
        assertEquals(13, nodes.size, nodes.joinToString { "${it.disc}" })
        val prints = nodes.map { ClanTechNodes.fingerprint(w, h, p, it.disc) }
        val nearest = prints.map { f -> ClanTechPresets.tracked.minOf { ClanTechNodes.distance(f, it) } }
        assertEquals(emptyMap(), ClanTechNodes.assign(prints, ClanTechPresets.tracked), "nearest distances: $nearest")
        println("nearest preset distance per Moc node: ${nearest.map { "%.2f".format(it) }}")
    }
}
