package eu.komars.forgescanner.model

import kotlin.math.abs

/**
 * Research nodes of the clan tech tree: light-blue (in progress) or light-green (maxed) discs with a
 * dark outline and an icon inside, each with a "x/10" label underneath. Works on a downscaled ARGB frame.
 */
object ClanTechNodes {
    data class Node(val disc: PixelRegion, val label: PixelRegion)

    fun discColor(c: Int): Boolean {
        val r = c ushr 16 and 255; val g = c ushr 8 and 255; val b = c and 255
        // Lit part ≈ (126,177,231), shaded rim ≈ (100,141,187) on the real screenshot.
        val blue = b > 165 && b > r + 40 && g > r + 20 && g in 115..220 && r in 60..175
        // Maxed: lit ≈ (130,214,127), shaded ≈ (109,180,105).
        val green = g > 140 && g > r + 25 && g > b + 25 && r in 70..200 && b in 60..190
        return blue || green
    }

    fun find(w: Int, h: Int, p: IntArray): List<Node> {
        if (w < 100 || h < w || p.size != w * h) return emptyList()
        val top = (h * .12).toInt(); val bottom = (h * .86).toInt()
        val seen = BooleanArray(p.size); val queue = IntArray(p.size)
        val minSide = (w * .085).toInt(); val maxSide = (w * .24).toInt()
        val found = mutableListOf<PixelRegion>()
        for (y in top until bottom) for (x in 0 until w) {
            val start = y * w + x
            if (seen[start] || !discColor(p[start])) continue
            var head = 0; var tail = 0; queue[tail++] = start; seen[start] = true
            var l = x; var t = y; var r = x; var b = y
            while (head < tail) {
                val i = queue[head++]; val xx = i % w; val yy = i / w
                l = minOf(l, xx); r = maxOf(r, xx); t = minOf(t, yy); b = maxOf(b, yy)
                for (n in intArrayOf(if (xx > 0) i - 1 else -1, if (xx < w - 1) i + 1 else -1, if (yy > top) i - w else -1, if (yy < bottom - 1) i + w else -1))
                    if (n >= 0 && !seen[n] && discColor(p[n])) { seen[n] = true; queue[tail++] = n }
            }
            val bw = r - l + 1; val bh = b - t + 1
            if (bw !in minSide..maxSide || bh !in minSide..maxSide || abs(bw - bh) > maxOf(bw, bh) / 4) continue
            // A disc (minus its icon) fills a good part of its box; flat bars and text do not have that shape.
            if (tail < bw * bh * .30) continue
            if (!outlined(w, h, p, l, t, r, b)) continue
            found += PixelRegion(l, t, r + 1, b + 1)
        }
        val ordered = found.sortedWith(compareBy<PixelRegion> { it.top / maxOf(1, (w * .06).toInt()) }.thenBy { it.left })
        return ordered.map { d ->
            val pad = d.width / 4
            // The "0/10" label overlaps the disc's lower rim: start a quarter of the disc above its bottom edge.
            Node(d, PixelRegion(maxOf(0, d.left - pad), maxOf(0, d.bottom - d.height / 4), minOf(w, d.right + pad), minOf(h, d.bottom + d.height * 2 / 5 + 2)))
        }
    }

    /** The game draws every node with a dark navy ring just outside the coloured disc. */
    private fun outlined(w: Int, h: Int, p: IntArray, l: Int, t: Int, r: Int, b: Int): Boolean {
        val cx = (l + r) / 2; val cy = (t + b) / 2
        var dark = 0; var probes = 0
        for ((dx, dy) in listOf(0 to -1, 0 to 1, -1 to 0, 1 to 0)) {
            probes++
            var x = cx; var y = cy
            // Walk outward from the disc edge for a few pixels looking for the dark outline.
            x = if (dx < 0) l else if (dx > 0) r else cx
            y = if (dy < 0) t else if (dy > 0) b else cy
            for (step in 1..7) {
                val xx = x + dx * step; val yy = y + dy * step
                if (xx !in 0 until w || yy !in 0 until h) break
                val c = p[yy * w + xx]
                val lum = ((c ushr 16 and 255) * 299 + (c ushr 8 and 255) * 587 + (c and 255) * 114) / 1000
                if (lum < 105) { dark++; break }
            }
        }
        return probes > 0 && dark >= 3
    }

    const val GRID = 28

    /**
     * Icon fingerprint independent of the disc colour (blue in progress, green maxed) and of the frame scale:
     * a GRID×GRID sampling inside the disc where disc-coloured and outline pixels are "background" (-1)
     * and icon pixels keep a coarse RGB (4 bits per channel).
     */
    fun fingerprint(w: Int, h: Int, p: IntArray, disc: PixelRegion): IntArray {
        val inset = disc.width * .14
        return IntArray(GRID * GRID) { i ->
            val x = (disc.left + inset + (i % GRID + .5) * (disc.width - 2 * inset) / GRID).toInt().coerceIn(0, w - 1)
            val y = (disc.top + inset + (i / GRID + .5) * (disc.height - 2 * inset) / GRID).toInt().coerceIn(0, h - 1)
            val c = p[y * w + x]
            val r = c ushr 16 and 255; val g = c ushr 8 and 255; val b = c and 255
            val lum = (r * 299 + g * 587 + b * 114) / 1000
            if (discColor(c) || lum < 55) -1 else (r shr 4 shl 8) or (g shr 4 shl 4) or (b shr 4)
        }
    }

    private fun cell(x: Int, y: Int): Double = if (x < 0 || y < 0) (if (x < 0 && y < 0) 0.0 else 1.0)
        else minOf(1.0, (abs((x shr 8) - (y shr 8)) + abs((x shr 4 and 15) - (y shr 4 and 15)) + abs((x and 15) - (y and 15))) / 9.0)

    /** Each ink cell of [a] against the best cell of [b] within one cell of the same place (frames shift by a pixel or two). */
    private fun directed(a: IntArray, b: IntArray): Pair<Double, Int> {
        var sum = 0.0; var cells = 0
        for (i in a.indices) {
            if (a[i] < 0) continue
            cells++
            val x = i % GRID; val y = i / GRID
            var best = 1.0
            for (dy in -1..1) for (dx in -1..1) {
                val xx = x + dx; val yy = y + dy
                if (xx in 0 until GRID && yy in 0 until GRID) best = minOf(best, cell(a[i], b[yy * GRID + xx]))
            }
            sum += best
        }
        return sum to cells
    }

    /** 0 = identical icon, 1 = nothing in common; symmetric, shift-tolerant, disc colour ignored. */
    fun distance(a: IntArray, b: IntArray): Double {
        if (a.size != b.size || a.size != GRID * GRID) return 1.0
        val (s1, c1) = directed(a, b); val (s2, c2) = directed(b, a)
        return if (c1 + c2 == 0) 1.0 else (s1 + s2) / (c1 + c2)
    }

    /**
     * Unique assignment of detected icons to tracked fingerprints: the most certain pairs first, each used once,
     * only below [limit]. Returns detected index → tracked index. Day 1–6 icons differ only by a small digit,
     * so a global choice beats picking the nearest icon independently.
     */
    fun assign(detected: List<IntArray>, tracked: List<IntArray>, limit: Double = .44): Map<Int, Int> {
        val pairs = detected.indices.flatMap { i -> tracked.indices.map { j -> Triple(i, j, distance(detected[i], tracked[j])) } }
            .filter { it.third < limit }.sortedBy { it.third }
        val out = linkedMapOf<Int, Int>(); val used = mutableSetOf<Int>()
        for ((i, j, _) in pairs) if (i !in out && j !in used) { out[i] = j; used += j }
        return out
    }

    fun calendarDistance(icon:IntArray, tracked:List<IntArray>):Double =
        tracked.subList(10,16).minOf {distance(icon,it)}

    /** Match the first four rows of "Wojna klanów". The six tiny calendar digits share one icon shape. */
    fun assignWarPoints(width:Int, boxes:List<PixelRegion>, icons:List<IntArray>, tracked:List<IntArray>):Map<Int,Int> {
        val result=assign(icons,tracked.take(10)).toMutableMap()
        val calendars=icons.indices.filter {it !in result && calendarDistance(icons[it],tracked)<.52}
        fun column(i:Int):Int? {
            val center=(boxes[i].left+boxes[i].right)/2.0/width
            return (0..3).singleOrNull {abs(center-(.195+it*.203))<.06}
        }
        fun row(a:Int,b:Int)=abs(boxes[a].top-boxes[b].top)<minOf(boxes[a].height,boxes[b].height)/3
        val mounts=result.filterValues {it==8||it==9}.keys
        for(i in calendars) {
            val col=column(i)?:continue
            val hasMount=mounts.any {row(it,i)}
            val lastRow=calendars.count {row(it,i)&&column(it)!=null}>=3
            val index=when {
                hasMount&&col>=2 -> 8+col
                !hasMount&&lastRow -> 12+col
                else -> continue
            }
            if(index !in result.values)result[i]=index
        }
        return result
    }

    /** "3/10", "10 / 20" under a node: current and maximum level. */
    fun level(text: String): Pair<Int, Int>? {
        val m = Regex("(\\d{1,4})\\s*/\\s*(\\d{1,4})").find(text) ?: return null
        val current = m.groupValues[1].toInt(); val max = m.groupValues[2].toInt()
        return if (max in 1..1000 && current in 0..max) current to max else null
    }

    /** A maxed node shows "Maks." instead of "x/y" under its disc. */
    fun maxed(text: String): Boolean = Regex("ma[kx]s", RegexOption.IGNORE_CASE).containsMatchIn(text)

    /** First "+X%" in a research description (also "+10000%" and "+4,5%"). */
    fun percent(text: String): Double? =
        Regex("\\+\\s*([0-9][0-9\\s.,]*?)\\s*%").find(text)?.groupValues?.get(1)?.replace(Regex("\\s"), "")?.replace(',', '.')
            ?.let { raw -> if (raw.count { it == '.' } > 1) raw.replace(".", "") else raw }?.toDoubleOrNull()

    /** Node name: the first OCR line with letters (descriptions follow on later lines). */
    fun name(text: String): String = text.lines().map { it.trim() }.firstOrNull { line -> line.count { it.isLetter() } >= 3 }.orEmpty().take(80)
}
