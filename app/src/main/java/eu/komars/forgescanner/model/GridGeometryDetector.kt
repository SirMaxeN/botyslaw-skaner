package eu.komars.forgescanner.model

import kotlin.math.abs
import kotlin.math.roundToInt

/** Fits icon centers to the game's regular inventory lattice inside a loosely selected area. */
class GridGeometryDetector {
    data class Geometry(val region: PixelRegion, val columns: Int, val rowHeight: Double, val icons: Int)
    private data class Component(val left: Int, val top: Int, val right: Int, val bottom: Int, val area: Int) {
        val width get() = right - left
        val height get() = bottom - top
        val x get() = (left + right) / 2.0
        val y get() = (top + bottom) / 2.0
    }

    fun detect(frame: GrayScrollFrame, rough: PixelRegion, columns: Int = 5): Geometry? {
        require(columns in 1..12)
        val expected = frame.width.toDouble() / columns
        val padding = (expected * 0.7).roundToInt()
        val search = PixelRegion((rough.left - padding).coerceAtLeast(0), (rough.top - padding).coerceAtLeast(0),
            (rough.right + padding).coerceAtMost(frame.width), (rough.bottom + padding).coerceAtMost(frame.height))
        if (search.width <= 0 || search.height <= 0) return null
        val visited = BooleanArray(frame.pixels.size)
        val queue = IntArray(frame.pixels.size)
        val components = mutableListOf<Component>()
        for (y in search.top until search.bottom) for (x in search.left until search.right) {
            val first = y * frame.width + x
            if (visited[first] || frame.pixel(x, y) >= 190) continue
            var head = 0; var tail = 0
            var left = x; var right = x; var top = y; var bottom = y
            queue[tail++] = first; visited[first] = true
            while (head < tail) {
                val index = queue[head++]; val px = index % frame.width; val py = index / frame.width
                left = minOf(left, px); right = maxOf(right, px); top = minOf(top, py); bottom = maxOf(bottom, py)
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = px + dx; val ny = py + dy
                    if (nx !in search.left until search.right || ny !in search.top until search.bottom) continue
                    val next = ny * frame.width + nx
                    if (!visited[next] && frame.pixel(nx, ny) < 190) {
                        visited[next] = true; queue[tail++] = next
                    }
                }
            }
            components += Component(left, top, right + 1, bottom + 1, tail)
        }
        val icons = components.filter {
            // Currency bars and headings are wide and shallow. They must not establish a row.
            it.height in (expected * .45).roundToInt()..(expected * 1.30).roundToInt() &&
                it.width in (expected * .14).roundToInt()..(expected * 1.40).roundToInt() &&
                it.area >= expected * expected * .012 &&
                it.x >= rough.left - expected * .4 && it.x <= rough.right + expected * .4 &&
                it.y >= rough.top - expected * .4 && it.y <= rough.bottom + expected * .4
        }.filter { candidate ->
            components.none { outer -> outer !== candidate && outer.area > candidate.area &&
                outer.width < expected * 1.4 && outer.height < expected * 1.3 &&
                outer.left <= candidate.left && outer.right >= candidate.right &&
                outer.top <= candidate.top && outer.bottom >= candidate.bottom }
        }
        if (icons.isEmpty()) return null
        fun clusters(values: List<Double>): List<Double> {
            val groups = mutableListOf<MutableList<Double>>()
            for (value in values.sorted()) {
                if (groups.isEmpty() || abs(value - groups.last().average()) > expected * .25) groups += mutableListOf(value)
                else groups.last() += value
            }
            return groups.map { it.average() }
        }
        val xs = clusters(icons.map { it.x }); val ys = clusters(icons.map { it.y })
        val distances = (xs.zipWithNext { a, b -> b - a } + ys.zipWithNext { a, b -> b - a })
            .filter { it in expected * .55..expected * 1.45 }.sorted()
        // The mount inventory lives in a white modal. Keep its dark outside border out of cells.
        // Find a broad white horizontal span below an icon, including for a one-item inventory.
        val whiteSpans = icons.mapNotNull { icon ->
            val y = (icon.bottom + expected * .20).roundToInt().coerceAtMost(frame.height - 1)
            val center = icon.x.roundToInt().coerceIn(0, frame.width - 1)
            if (frame.pixel(center, y) < 245) null else {
                var a = center; var b = center
                while (a > 0 && frame.pixel(a - 1, y) >= 245) a--
                while (b + 1 < frame.width && frame.pixel(b + 1, y) >= 245) b++
                if (b - a >= frame.width * .75) a to b + 1 else null
            }
        }
        val panel = whiteSpans.maxByOrNull { it.second - it.first } ?: (0 to frame.width)
        val pitch = if (distances.isEmpty()) (panel.second - panel.first).toDouble() / columns
            else distances[distances.size / 2]
        if (pitch < 8) return null
        var left = xs.first() - pitch / 2
        while (left - pitch >= panel.first) left -= pitch
        val gridWidth = minOf((panel.second - panel.first).toDouble(), pitch * columns)
        left = left.coerceIn(panel.first.toDouble(), panel.second - gridWidth)
        val top = (ys.first() - pitch / 2).roundToInt().coerceAtLeast(0)
        // Fixed wide panels below the grid (equipped slots/buttons) are not inventory rows.
        val boundary = components.filter { it.width >= pitch * 1.55 && it.height >= pitch * .2 &&
            it.top > top + pitch && it.x > left && it.x < left + gridWidth }.minOfOrNull { it.top }
        val bottom = minOf(frame.height, boundary?.minus(2) ?: frame.height,
            (rough.bottom + pitch * .65).roundToInt())
        if (bottom - top < pitch * .8) return null
        return Geometry(PixelRegion(left.roundToInt(), top, (left + gridWidth).roundToInt(), bottom),
            columns, pitch, icons.size)
    }
}
