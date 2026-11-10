package eu.komars.forgescanner.model

import kotlin.math.abs

/**
 * Resource counters on the game's top bar: an icon (red vial, green vial, pink gem, gold crown, egg shell)
 * followed by a number. The tabs use grey pills, while the home wallet can use dark green pills.
 * Used only to PROPOSE setup rectangles; the player confirms them.
 */
object TopCounters {
    enum class Icon { RED, GREEN, PINK, CROWN, SHELL }
    data class Counter(val icon: Icon, val region: PixelRegion)

    private fun rgb(c: Int) = Triple(c ushr 16 and 255, c ushr 8 and 255, c and 255)
    private fun grey(c: Int): Boolean { val (r, g, b) = rgb(c); return r in 80..132 && g in 80..132 && b in 80..132 && maxOf(r, g, b) - minOf(r, g, b) < 14 }
    private fun homePill(c:Int):Boolean {
        val (r,g,b)=rgb(c)
        return g in 38..112 && r<=45 && b in 10..80 && g>=r+18 && g>=b+12
    }
    private fun icon(c: Int, home:Boolean): Icon? {
        val (r, g, b) = rgb(c)
        return when {
            r > 150 && g < 70 && b < 70 -> Icon.RED
            g > 170 && r < 90 && b < 130 -> Icon.GREEN
            r > 200 && b > 150 && r - g > 50 -> Icon.PINK
            home && r>180 && b>95 && r-g>65 && b-g>23 -> Icon.PINK
            r > 215 && g in 150..225 && b < 90 -> Icon.CROWN
            else -> null
        }
    }

    private data class Blob(val l: Int, val t: Int, val r: Int, val b: Int, val area: Int) {
        val w get() = r - l + 1; val h get() = b - t + 1
    }

    private fun blobs(w: Int, p: IntArray, top: Int, bottom: Int, accept: (Int) -> Boolean): List<Blob> {
        val seen = BooleanArray(p.size); val queue = IntArray(w * (bottom - top + 1)); val out = mutableListOf<Blob>()
        for (y in top until bottom) for (x in 0 until w) {
            val s = y * w + x
            if (seen[s] || !accept(p[s])) continue
            var head = 0; var tail = 0; queue[tail++] = s; seen[s] = true
            var l = x; var t = y; var r = x; var b = y
            while (head < tail) {
                val i = queue[head++]; val xx = i % w; val yy = i / w
                l = minOf(l, xx); r = maxOf(r, xx); t = minOf(t, yy); b = maxOf(b, yy)
                for (n in intArrayOf(if (xx > 0) i - 1 else -1, if (xx < w - 1) i + 1 else -1, if (yy > top) i - w else -1, if (yy < bottom - 1) i + w else -1))
                    if (n >= 0 && !seen[n] && accept(p[n])) { seen[n] = true; queue[tail++] = n }
            }
            out += Blob(l, t, r, b, tail)
        }
        return out
    }

    fun find(w:Int,h:Int,p:IntArray):List<Counter> = findCounters(w,h,p,home=false)

    private fun findCounters(w:Int,h:Int,p:IntArray,home:Boolean):List<Counter> {
        if (w < 100 || h < w || p.size != w * h) return emptyList()
        val bottom = (h * .17).toInt()
        // The number's white digits split a pill into pieces: merge grey pieces of one row first.
        val pieces = blobs(w, p, 0, bottom) { grey(it)||(home&&homePill(it)) }.filter { it.h in (h * .012).toInt()..(h * .07).toInt() && it.area >= 12 }.sortedBy { it.l }.toMutableList()
        val merged = mutableListOf<Blob>()
        for (piece in pieces) {
            val at = merged.indexOfFirst { m -> piece.l <= m.r + m.h && minOf(m.b, piece.b) - maxOf(m.t, piece.t) > minOf(m.h, piece.h) / 2 }
            if (at < 0) merged += piece
            else { val m = merged[at]; merged[at] = Blob(minOf(m.l, piece.l), minOf(m.t, piece.t), maxOf(m.r, piece.r), maxOf(m.b, piece.b), m.area + piece.area) }
        }
        val pills = merged.filter { it.w in (w * .10).toInt()..(w * .42).toInt() && it.h in (h * .018).toInt()..(h * .07).toInt() && it.w > it.h * 2 && it.area > it.w * it.h * .20 }
        // Icons: compact coloured blobs near the bar (the vial/gem/crown art, not the small green "+" badges).
        val kinds = if(home)listOf(Icon.CROWN,Icon.PINK) else Icon.entries.filter {it!=Icon.SHELL}
        val raw = kinds.flatMap { kind ->
            blobs(w,p,0,bottom) { icon(it,home)==kind }
                .filter { it.w in (w * .018).toInt()..(w * .11).toInt() && it.h in (w * .018).toInt()..(w * .11).toInt() && it.area > it.w * it.h * .25 }
                .filter { !home || if(kind==Icon.CROWN)it.l in (w*.45).toInt()..(w*.72).toInt() else it.l>w*.66 }
                .map { kind to it }
        }
        // Small green "+" badges sit on another icon's corner: drop a blob that overlaps a larger icon of another kind.
        val icons = raw.filter { (kind, b) -> raw.none { (other, o) -> other != kind && o.area > b.area && b.l < o.r + o.w / 3 && b.r > o.l && b.t < o.b + o.h / 3 && b.b > o.t - o.h / 3 } }
        val out = mutableListOf<Counter>()
        val usedPills = mutableSetOf<Blob>()
        for ((kind, b) in icons.sortedBy { it.second.l }) {
            val cy = (b.t + b.b) / 2
            // The pill that starts at (or just right of) the icon on the same row.
            val pill = pills.filter { it !in usedPills && cy in it.t - b.h / 2..it.b + b.h / 2 && it.l in b.l - b.w..b.r + b.w }.minByOrNull { abs(it.l - b.r) }
            val right = pill?.r?.plus(2) ?: run {
                val next = icons.filter { (_, o) -> o.l > b.r + b.w && abs((o.t + o.b) / 2 - cy) < b.h }.minOfOrNull { it.second.l }
                (next?.minus((w * .012).toInt()) ?: (w * .985).toInt())
            }
            if (pill != null) usedPills += pill
            val top = minOf(b.t, pill?.t ?: b.t) - 2; val bot = maxOf(b.b, pill?.b ?: b.b) + 2
            if (out.any { it.icon == kind && abs(it.region.top - top) < b.h }) continue
            out += Counter(kind, PixelRegion(maxOf(0, b.l - 2), maxOf(0, top), minOf(w, right), minOf(h, bot)))
        }
        // A left pill with no coloured icon: the egg-shell counter of the pets tab.
        pills.filter { it !in usedPills && it.l < w / 3 }.minByOrNull { it.t }?.let { pill ->
            out += Counter(Icon.SHELL, PixelRegion(maxOf(0, pill.l - pill.h * 3 / 2), maxOf(0, pill.t - 2), minOf(w, pill.r + 2), minOf(h, pill.b + 2)))
        }
        return out
    }

    /** Proposal for one counter, decided by the screen it lives on (badges make icon colour alone ambiguous). */
    fun forCounter(icon: Icon, screen: GameScreenKind, w: Int, h: Int, p: IntArray): PixelRegion? {
        val found = findCounters(w,h,p,home=screen==GameScreenKind.HOME)
        val left = found.filter { it.region.left < w / 3 }.sortedBy { it.region.top }
        return when (icon) {
            Icon.PINK -> found.filter { it.icon == Icon.PINK && it.region.left > w / 2 }.minByOrNull { it.region.top }?.region
            // The player's power (yellow swords under the nick) is on the left; the coin crown is right of centre.
            Icon.CROWN -> found.filter { it.icon == Icon.CROWN && it.region.left > w * 9 / 20 }.minByOrNull { it.region.top }?.region?.takeIf { screen == GameScreenKind.HOME }
            Icon.SHELL -> left.firstOrNull()?.region?.takeIf { screen == GameScreenKind.PETS }
            Icon.RED -> (left.firstOrNull { it.icon == Icon.RED } ?: left.getOrNull(0))?.region?.takeIf { screen == GameScreenKind.TECHNOLOGY }
            Icon.GREEN -> (left.firstOrNull { it.icon == Icon.GREEN } ?: left.getOrNull(1))?.region?.takeIf { screen == GameScreenKind.TECHNOLOGY }
        }
    }
}
