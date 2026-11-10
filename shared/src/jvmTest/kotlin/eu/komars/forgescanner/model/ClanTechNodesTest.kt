package eu.komars.forgescanner.model

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

import kotlin.test.Test
import java.io.File
import javax.imageio.ImageIO

class ClanTechNodesTest {
    private val navy = 0xFF1B2440.toInt(); private val blue = 0xFF7CA6E2.toInt(); private val green = 0xFF8CD48C.toInt(); private val white = 0xFFF2F2F2.toInt()

    private fun canvas(w: Int, h: Int, discs: List<Triple<Int, Int, Int>>): IntArray {
        val p = IntArray(w * h) { white }
        for ((cx, cy, color) in discs) {
            val rad = 22
            for (y in cy - rad - 3..cy + rad + 3) for (x in cx - rad - 3..cx + rad + 3) {
                val d2 = (x - cx) * (x - cx) + (y - cy) * (y - cy)
                if (d2 <= rad * rad) p[y * w + x] = color else if (d2 <= (rad + 3) * (rad + 3)) p[y * w + x] = navy
            }
            // An icon in the middle (not disc-coloured).
            for (y in cy - 8..cy + 8) for (x in cx - 8..cx + 8) p[y * w + x] = 0xFFE8B030.toInt()
        }
        return p
    }

    @Test fun findsBlueAndMaxedGreenDiscsInReadingOrderWithLabelsBelow() {
        val w = 360; val h = 720
        val discs = listOf(Triple(60, 200, blue), Triple(150, 200, green), Triple(240, 200, blue), Triple(60, 300, blue), Triple(150, 300, blue))
        val nodes = ClanTechNodes.find(w, h, canvas(w, h, discs))
        assertEquals(5, nodes.size)
        assertEquals(listOf(60, 150, 240, 60, 150), nodes.map { (it.disc.left + it.disc.right) / 2 })
        assertTrue(nodes.all { it.label.top == it.disc.bottom - it.disc.height / 4 && it.label.bottom > it.disc.bottom })
    }

    @Test fun ignoresDiscsOutsideTheTreeAreaAndFlatBars() {
        val w = 360; val h = 720
        val p = canvas(w, h, listOf(Triple(60, 40, blue)))
        for (y in 400..420) for (x in 20..340) p[y * w + x] = blue // a wide tab bar, not a node
        assertEquals(0, ClanTechNodes.find(w, h, p).size)
    }

    @Test fun parsesLevelsPercentAndName() {
        assertEquals(3 to 10, ClanTechNodes.level("3/10"))
        assertEquals(100 to 100, ClanTechNodes.level("Poz. 100 / 100"))
        assertNull(ClanTechNodes.level("11/10"))
        assertEquals(8700.0, ClanTechNodes.percent("Obrażenia\n+8 700% obrażeń w atakach")!!, 0.0)
        assertEquals(4.5, ClanTechNodes.percent("+4,5% punktów"))
        assertEquals(10000.0, ClanTechNodes.percent("+10000%"))
        assertEquals("Wyścig badań klanu", ClanTechNodes.name("12\nWyścig badań klanu\n+5% darowizn"))
    }

    /** User screenshot of 16 clan-war nodes (local, not committed), placed inside a phone-shaped frame. */
    @Test fun findsAllSixteenNodesOnTheRealScreenshot() {
        val file = File(System.getProperty("user.dir")).resolve("../.local/clan-tech/nodes-crop.png")
        if (!file.exists()) return
        val crop = ImageIO.read(file)
        val w = 380; val h = 900; val p = IntArray(w * h) { white }
        val dy = 220
        for (y in 0 until minOf(crop.height, h - dy)) for (x in 0 until minOf(crop.width, w)) p[(y + dy) * w + x] = crop.getRGB(x, y) or (0xFF shl 24).toInt()
        val nodes = ClanTechNodes.find(w, h, p)
        assertEquals(16, nodes.size, nodes.joinToString { "${it.disc}" })
        assertEquals(1, nodes.take(4).map { it.disc.top / 20 }.toSet().size, "first row read left to right")
    }
}

class ClanTechFingerprintTest {
    /** Every node of the real screenshot, re-detected on a 0.85× rescaled copy, matches itself and nothing else. */
    @kotlin.test.Test fun iconsMatchThemselvesAcrossScaleAndNotTheirNeighbours() {
        val file = java.io.File(System.getProperty("user.dir")).resolve("../.local/clan-tech/nodes-crop.png")
        if (!file.exists()) return
        val crop = javax.imageio.ImageIO.read(file)
        fun frame(scale: Double): Triple<Int, IntArray, List<ClanTechNodes.Node>> {
            val w = 380; val h = 900; val p = IntArray(w * h) { 0xFFF2F2F2.toInt() }
            val cw = (crop.width * scale).toInt(); val ch = (crop.height * scale).toInt()
            for (y in 0 until ch) for (x in 0 until minOf(cw, w)) p[(y + 230) * w + x] = crop.getRGB((x / scale).toInt().coerceAtMost(crop.width - 1), (y / scale).toInt().coerceAtMost(crop.height - 1)) or (0xFF shl 24).toInt()
            return Triple(w, p, ClanTechNodes.find(w, h, p))
        }
        val (w1, p1, a) = frame(1.0); val (w2, p2, b) = frame(.85)
        kotlin.test.assertEquals(16, a.size); kotlin.test.assertEquals(16, b.size)
        val fa = a.map { ClanTechNodes.fingerprint(w1, 900, p1, it.disc) }; val fb = b.map { ClanTechNodes.fingerprint(w2, 900, p2, it.disc) }
        val report = StringBuilder()
        for (i in fa.indices) {
            val d = fb.map { ClanTechNodes.distance(fa[i], it) }
            val best = d.indices.minByOrNull { d[it] }!!
            val second = d.filterIndexed { j, _ -> j != best }.minOrNull()!!
            report.append("node $i: best=$best self=${"%.3f".format(d[i])} next=${"%.3f".format(second)}\n")
        }
        val assigned = ClanTechNodes.assign(fb, fa)
        kotlin.test.assertEquals(fa.indices.associateWith { it }, assigned, report.toString())
        // A tracked subset (e.g. only days 1, 3, 5) is still found exactly, the rest stays unmatched.
        val subset = listOf(10, 12, 14)
        kotlin.test.assertEquals(subset.withIndex().associate { (k, v) -> v to k }, ClanTechNodes.assign(fb, subset.map { fa[it] }).filterKeys { it in subset })
        println(report)
    }
}

class ClanTechScopeReplayTest {
    @Test fun userScreensSelectExactlyTheSixteenWarPointNodesAndTheLastTwoPotions() {
        val base=File("../.local/clan-tech")
        val files=listOf(File(base,"scope-91-war.jpg"),File(base,"scope-91-potions.jpg"))
        if(files.any {!it.isFile})return
        for((page,file) in files.withIndex()) {
            val source=ImageIO.read(file)
            val w=360
            val h=source.height*w/source.width
            val image=java.awt.image.BufferedImage(w,h,java.awt.image.BufferedImage.TYPE_INT_ARGB)
            val canvas=image.createGraphics()
            canvas.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            canvas.drawImage(source,0,0,w,h,null)
            canvas.dispose()
            val p=image.getRGB(0,0,w,h,null,0,w)
            val nodes=ClanTechNodes.find(w,h,p)
            val presets=ClanTechPresets.tracked
            fun prints(nodes:List<ClanTechNodes.Node>)=nodes.map {ClanTechNodes.fingerprint(w,h,p,it.disc)}
            if(page==0) {
                val war=nodes.filter {it.disc.top>h*.34}
                assertEquals((0..15).toList(),ClanTechNodes.assignWarPoints(w,war.map {it.disc},prints(war),presets).toSortedMap().values.toList())
            } else {
                val extraWar=nodes.filter {it.disc.top<h*.49}
                assertEquals(emptyMap(),ClanTechNodes.assignWarPoints(w,extraWar.map {it.disc},prints(extraWar),presets))
                val potions=nodes.filter {it.disc.top>h*.49}
                assertEquals(mapOf(4 to 0,5 to 1),ClanTechNodes.assign(prints(potions),presets.drop(16)))
            }
        }
    }
}
