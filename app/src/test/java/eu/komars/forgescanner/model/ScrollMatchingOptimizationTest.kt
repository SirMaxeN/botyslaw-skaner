package eu.komars.forgescanner.model

import java.util.Random
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import org.junit.Assert.*
import org.junit.Test

/** The exhaustive matcher is the reference for this exact, work-reducing optimization. */
class ScrollMatchingOptimizationTest {
    @Test fun prunedSearchPreservesExhaustivePeaksTiesAndSubpixelFits() {
        val random = Random(14761)
        for (repeated in listOf(false, true)) for (width in listOf(40, 79, 160)) {
            val height = 119; val rowHeight = 32
            val row = ByteArray(width * rowHeight) { random.nextInt(256).toByte() }
            val document = ByteArray(width * (height + 160)) { index ->
                if (repeated) row[index % row.size] else random.nextInt(256).toByte()
            }
            fun frame(offset: Int, brightness: Int = 0, fraction: Double = 0.0): GrayScrollFrame =
                GrayScrollFrame(width, height, ByteArray(width * height) { index ->
                    val a = document[offset * width + index].toInt() and 255
                    val b = document[(offset + 1) * width + index].toInt() and 255
                    ((a * (1 - fraction) + b * fraction).toInt() + brightness).coerceIn(0, 255).toByte()
                })
            val first = frame(65)
            for (offset in listOf(17, 53, 64, 65, 66, 77, 113)) {
                for ((brightness, fraction) in listOf(0 to 0.0, 8 to .35)) {
                    val next = frame(offset, brightness, fraction)
                    for (expected in listOf(-44.0, -1.25, 0.0, 12.4, 43.0)) {
                        val oracle = exhaustive(first, next, rowHeight, expected)
                        val actual = ScrollSequenceAnalyzer().translation(first, next, rowHeight, expected)
                        assertEquals("repeated=$repeated width=$width offset=$offset expected=$expected", oracle, actual)
                    }
                }
            }
        }
    }

    @Test fun expectedMotionAvoidsMostImpossiblePixelComparisons() {
        val width = 160; val height = 133; val offset = 11
        val random = Random(391)
        val document = ByteArray(width * (height + offset)) { random.nextInt(256).toByte() }
        val first = GrayScrollFrame(width, height, document.copyOfRange(0, width * height))
        val next = GrayScrollFrame(width, height, document.copyOfRange(offset * width, document.size))
        val matcher = ScrollSequenceAnalyzer()
        assertEquals(exhaustive(first, next, 32, offset.toDouble()), matcher.translation(first, next, 32, offset.toDouble()))
        val allComparisons = (-height / 2..height / 2).sumOf { shift ->
            val start = max(0, -shift); val end = min(height, height - shift)
            ((end - start + 1) / 2).toLong() * ((width - 2) / 3)
        }
        assertTrue("${matcher.lastTranslationComparedPixels} comparisons versus $allComparisons exhaustive",
            matcher.lastTranslationComparedPixels < allComparisons / 4)
    }

    private fun exhaustive(old: GrayScrollFrame, next: GrayScrollFrame, rowHeight: Int,
                           expected: Double): ScrollSequenceAnalyzer.Motion? {
        if (abs(expected) < rowHeight * .5 && old.pixels.contentEquals(next.pixels))
            return ScrollSequenceAnalyzer.Motion(0.0, false)
        val limit = old.height / 2
        val scores = DoubleArray(limit * 2 + 1)
        for (shift in -limit..limit) {
            var sum = 0L; var count = 0
            for (y in max(0, -shift) until min(old.height, old.height - shift) step 2)
                for (x in 2 until old.width - 2 step 3) {
                    sum += abs(old.pixel(x, y + shift) - next.pixel(x, y)); count++
                }
            scores[shift + limit] = if (count > 0) sum.toDouble() / count else Double.POSITIVE_INFINITY
        }
        val bestIndex = scores.indices.minByOrNull { scores[it] } ?: return null
        val bestScore = scores[bestIndex]
        if (bestScore > 45.0) return null
        val peaks = scores.indices.filter { index ->
            scores[index] <= bestScore + 6.0 &&
                (index == 0 || scores[index] <= scores[index - 1]) &&
                (index == scores.lastIndex || scores[index] <= scores[index + 1])
        }
        val competing = peaks.any { abs(it - bestIndex) > max(4, rowHeight / 3) }
        val index = if (competing) peaks.minByOrNull {
            abs(it - limit - expected) / rowHeight + .01 * (scores[it] - bestScore)
        } ?: bestIndex else bestIndex
        val shift = index - limit
        if (competing && abs(shift - expected) >= rowHeight * .60) return null
        val correction = if (scores[index] > .001 && index > 0 && index < scores.lastIndex) {
            val a = scores[index - 1]; val b = scores[index]; val c = scores[index + 1]
            val denominator = a - 2 * b + c
            if (denominator > .001) (.5 * (a - c) / denominator).coerceIn(-.5, .5) else 0.0
        } else 0.0
        return ScrollSequenceAnalyzer.Motion(shift + correction, competing)
    }
}
