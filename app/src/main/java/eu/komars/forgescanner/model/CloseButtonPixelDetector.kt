package eu.komars.forgescanner.model

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Conservative visual proposal only. A red circle is insufficient: require a white diagonal X.
 * The caller still controls which navigation state permits using the result.
 */
class CloseButtonPixelDetector {
    private data class Component(val bounds: PixelRegion, val area: Int)

    fun detect(width: Int, height: Int, argb: IntArray, expected: PixelRegion? = null): PixelRegion? {
        if (width < 16 || height < 16 || width.toLong() * height > 1_600_000L ||
            argb.size.toLong() != width.toLong() * height) return null
        if (expected != null && (expected.width <= 0 || expected.height <= 0 ||
                expected.left < 0 || expected.top < 0 || expected.right > width || expected.bottom > height)) return null
        val search = expected?.let {
            val padding = max(it.width, it.height)
            PixelRegion(max(0, it.left - padding), max(0, it.top - padding),
                min(width, it.right + padding), min(height, it.bottom + padding))
        } ?: PixelRegion(0, 0, width, height)
        val visited = BooleanArray(argb.size)
        val queue = IntArray(search.width * search.height)
        val candidates = mutableListOf<PixelRegion>()
        for (y in search.top until search.bottom) for (x in search.left until search.right) {
            val index = y * width + x
            if (visited[index] || !red(argb[index])) continue
            var head = 0; var tail = 0
            var left = x; var right = x; var top = y; var bottom = y
            visited[index] = true; queue[tail++] = index
            while (head < tail) {
                val at = queue[head++]; val px = at % width; val py = at / width
                left = min(left, px); right = max(right, px); top = min(top, py); bottom = max(bottom, py)
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = px + dx; val ny = py + dy
                    if (nx !in search.left until search.right || ny !in search.top until search.bottom) continue
                    val next = ny * width + nx
                    if (!visited[next] && red(argb[next])) { visited[next] = true; queue[tail++] = next }
                }
            }
            val component = Component(PixelRegion(left, top, right + 1, bottom + 1), tail)
            if (roundRedShape(component, width, height, argb) && whiteX(component.bounds, width, argb) &&
                compatible(component.bounds, expected)) candidates += component.bounds
        }
        // A choice between two plausible X buttons must be made by the user, not a heuristic.
        return candidates.singleOrNull()
    }

    private fun red(color: Int): Boolean {
        val r = color ushr 16 and 255; val g = color ushr 8 and 255; val b = color and 255
        return r >= 145 && g <= 155 && b <= 180 && r >= g * 1.35 && r >= b * 1.12
    }

    private fun white(color: Int): Boolean {
        val r = color ushr 16 and 255; val g = color ushr 8 and 255; val b = color and 255
        return min(r, min(g, b)) >= 190 && max(r, max(g, b)) - min(r, min(g, b)) <= 50
    }

    private fun roundRedShape(component: Component, frameWidth: Int, frameHeight: Int, argb: IntArray): Boolean {
        val b = component.bounds
        val minimum = max(14, (min(frameWidth, frameHeight) * .024).roundToInt())
        val maximum = (min(frameWidth, frameHeight) * .24).roundToInt()
        if (b.width !in minimum..maximum || b.height !in minimum..maximum ||
            b.width.toDouble() / b.height !in .80..1.25) return false
        val fraction = component.area.toDouble() / (b.width * b.height)
        if (fraction !in .35.. .84) return false
        // The corners of a circular component must be mostly outside the red fill.
        var redCorners = 0; var corners = 0
        val dx = max(2, b.width / 5); val dy = max(2, b.height / 5)
        for (y in b.top until b.bottom) for (x in b.left until b.right) {
            if ((x < b.left + dx || x >= b.right - dx) && (y < b.top + dy || y >= b.bottom - dy)) {
                corners++; if (red(argb[y * frameWidth + x])) redCorners++
            }
        }
        if (redCorners > corners * .32) return false
        // White-background red-cross badges can have round-ish bounds but lack the red ring.
        val cx = (b.left + b.right - 1) / 2.0; val cy = (b.top + b.bottom - 1) / 2.0
        var ring = 0
        for (angle in 0 until 16) {
            val radians = angle * Math.PI / 8
            val x = (cx + kotlin.math.cos(radians) * b.width * .36).roundToInt().coerceIn(b.left, b.right - 1)
            val y = (cy + kotlin.math.sin(radians) * b.height * .36).roundToInt().coerceIn(b.top, b.bottom - 1)
            if (red(argb[y * frameWidth + x])) ring++
        }
        return ring >= 12
    }

    private fun whiteX(b: PixelRegion, frameWidth: Int, argb: IntArray): Boolean {
        val cx = (b.left + b.right - 1) / 2.0; val cy = (b.top + b.bottom - 1) / 2.0
        val points = mutableListOf<Pair<Int, Int>>()
        val inner = PixelRegion((cx - b.width * .33).roundToInt(), (cy - b.height * .33).roundToInt(),
            (cx + b.width * .33).roundToInt(), (cy + b.height * .33).roundToInt())
        for (y in inner.top..inner.bottom) for (x in inner.left..inner.right) {
            if (white(argb[y * frameWidth + x])) points += x to y
        }
        if (points.size < 8) return false
        // X is one connected glyph; notification numbers such as 99 are separate glyphs.
        val remaining=points.map {(x,y)->y*frameWidth+x}.toMutableSet()
        var largest=0
        while(remaining.isNotEmpty()) {
            val queue=java.util.ArrayDeque<Int>();queue.add(remaining.first());remaining.remove(queue.first)
            var count=0
            while(queue.isNotEmpty()) {
                val at=queue.removeFirst();count++;val x=at%frameWidth;val y=at/frameWidth
                for(dy in -1..1)for(dx in -1..1) {val nx=x+dx;val ny=y+dy
                    if(nx in inner.left..inner.right && ny in inner.top..inner.bottom && remaining.remove(ny*frameWidth+nx))queue.add(ny*frameWidth+nx)
                }
            }
            largest=max(largest,count)
        }
        if(largest<points.size*.8)return false
        val left = points.minOf { it.first }; val right = points.maxOf { it.first }
        val top = points.minOf { it.second }; val bottom = points.maxOf { it.second }
        val symbolWidth = right - left + 1; val symbolHeight = bottom - top + 1
        if (symbolWidth < b.width * .20 || symbolHeight < b.height * .20 ||
            symbolWidth.toDouble() / symbolHeight !in .65..1.55) return false
        val sx = (left + right) / 2.0; val sy = (top + bottom) / 2.0
        if (abs(sx - cx) > b.width * .12 || abs(sy - cy) > b.height * .12) return false
        val quadrants = IntArray(4)
        var arms = 0; var diagonal = 0; var center = 0
        for ((x, y) in points) {
            val nx = (x - sx) / (symbolWidth / 2.0); val ny = (y - sy) / (symbolHeight / 2.0)
            if (abs(nx) <= .24 && abs(ny) <= .24) center++
            if (max(abs(nx), abs(ny)) >= .48) {
                arms++
                // Allow one raster-pixel displacement after screen resampling, retaining all four X arms.
                if (abs(abs(nx) - abs(ny)) <= .34 + 2.0 / min(symbolWidth, symbolHeight)) {
                    diagonal++
                    quadrants[(if (nx >= 0) 1 else 0) + (if (ny >= 0) 2 else 0)]++
                }
            }
        }
        return center >= 1 && arms >= 4 && diagonal >= arms * .72 && quadrants.all { it >= max(1, arms / 16) }
    }

    private fun compatible(candidate: PixelRegion, expected: PixelRegion?): Boolean {
        if (expected == null) return true
        if (candidate.width.toDouble() / expected.width !in .45..1.9 ||
            candidate.height.toDouble() / expected.height !in .45..1.9) return false
        val cx = (candidate.left + candidate.right) / 2.0; val cy = (candidate.top + candidate.bottom) / 2.0
        val ex = (expected.left + expected.right) / 2.0; val ey = (expected.top + expected.bottom) / 2.0
        return abs(cx - ex) <= expected.width && abs(cy - ey) <= expected.height
    }
}
