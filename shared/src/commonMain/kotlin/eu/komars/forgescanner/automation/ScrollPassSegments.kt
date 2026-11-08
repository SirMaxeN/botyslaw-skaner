package eu.komars.forgescanner.automation

import kotlin.math.ceil
import kotlin.math.max

/** Equal long strokes expose one logical pass without a tiny leftover movement. */
object ScrollPassSegments {
    data class Stroke(val distancePx: Double, val durationMillis: Long)
    fun split(distancePx: Double, durationMillis: Long, maximumDistancePx: Double,
              maximumDurationMillis: Long = 1000): List<Stroke> {
        require(distancePx.isFinite() && distancePx > 0 && maximumDistancePx.isFinite() && maximumDistancePx > 0)
        require(durationMillis > 0 && maximumDurationMillis >= 80)
        val count = max(ceil(distancePx / maximumDistancePx),
            ceil(durationMillis.toDouble() / maximumDurationMillis)).toInt()
        require(count in 1..32)
        val totalDuration = maxOf(durationMillis, count * 80L)
        return List(count) { index ->
            val from = totalDuration * index / count
            val until = totalDuration * (index + 1) / count
            Stroke(distancePx / count, until - from)
        }
    }
}
