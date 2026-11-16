package eu.komars.forgescanner.model

import org.junit.Assert.*
import org.junit.Test
import java.util.Random

class FingerOdometerTest {
    private val row = 32.0

    @Test fun ambiguousStretchesTakeTheRowCountFromTheFinger() {
        val intervals = listOf(
            FingerOdometer.Interval(110.0, 1, 100.0, ambiguous = false, blocked = false), // distinct pets: slop 10 px
            FingerOdometer.Interval(108.0, 0, 108.0, ambiguous = false, blocked = false), // ratio 1
            FingerOdometer.Interval(108.0, 0, 76.0, ambiguous = true, blocked = false),   // eggs: image lost a row
            FingerOdometer.Interval(108.0, 0, 140.0, ambiguous = true, blocked = false),  // eggs: image added a row
            FingerOdometer.Interval(118.0, 1, 44.0, ambiguous = true, blocked = false),   // eggs: lost two rows after a press
            FingerOdometer.Interval(108.0, 0, 20.0, ambiguous = true, blocked = true))    // pushed against the end
        val fusion = FingerOdometer.fuse(intervals, row)
        assertEquals(1.0, fusion.ratio, 1e-9)
        assertEquals(10.0, fusion.slopPx, 1e-9)
        assertEquals(1 - 1 + 2, fusion.rowCorrection)
        assertEquals(3, fusion.corrected)
        assertEquals(0, fusion.unsure)
    }

    @Test fun trustedImageIsNeverOverruledByTheFinger() {
        val fusion = FingerOdometer.fuse(listOf(FingerOdometer.Interval(108.0, 0, 60.0, ambiguous = false, blocked = false)), row)
        assertEquals(0, fusion.rowCorrection)
    }

    /**
     * Diagnostics XlawoK 1.6.5 mounts: the list ended inside the third stretch, the end was not detected and 16 more
     * strokes moved nothing (clear image, 0 px). The finger must not add those 16 x 3.6 rows (it read 336, image 91).
     */
    @Test fun stuckListAtTheEndAddsNoFingerRows() {
        val slop = row * .3
        var phase = 5.0
        fun interval(fingerRows: Double, presses: Int, trueRows: Double, imageRows: Double, ambiguous: Boolean): FingerOdometer.Interval {
            val from = phase
            phase = ((phase - trueRows * row) % row + row) % row
            return FingerOdometer.Interval(fingerRows * row, presses, imageRows * row, ambiguous, blocked = false, from, phase)
        }
        val stretches = mutableListOf(
            interval(7.18, 2, 7.18 - .6, 6.7, ambiguous = true),   // image off by a fraction
            interval(7.18, 2, 7.18 - .6, 5.58, ambiguous = true),  // image lost a whole row
            interval(3.59, 1, 1.34, 1.34, ambiguous = false))      // list ends mid-stroke
        repeat(16) { stretches += interval(3.59, 1, 0.0, 0.0, ambiguous = false) }
        val fusion = FingerOdometer.fuse(stretches, row)
        assertEquals(fusion.describe(), 1, fusion.correction)
        assertEquals(6.58 + 6.58 + 1.34, fusion.phaseRows!!, .05)
        assertEquals((6.58 - 6.7 + 1.0) * row, fusion.phaseErrorPx!!, 1.0) // true minus image travel
    }

    /** Identical eggs, fast strokes held still at the end of each pass: the finger keeps the whole-row count. */
    @Test fun sessionWithFingerMarksCountsIdenticalRowsDespiteFastStrokes() {
        // 9 px = the planner's 0.28 row per frame; 14 px is faster than the app ever scrolls.
        listOf(9, 14).map { step -> step to run(step) }.forEach { (step, result) ->
            assertEquals("step $step: image ${result.imageOnlyCount}, ${result.fingerDetail}", 505L, result.count) }
        // 0.6 row per frame: the image loses the list entirely (alone it reads 20). Finger + still-frame grid phase
        // recover every stretch except the last one, pushed against the end (no finger evidence, image only).
        // The finger reads 0.3 row too far on every stretch (unknown slop): alone it would add ~8 rows over
        // the list; the grid phase of each still frame keeps the count the same.
        val biased = run(19, fingerBias = 32 * .3)
        val lost = run(19)
        assertEquals("biased finger: ${biased.fingerDetail}", lost.count, biased.count)
        assertTrue("${lost.count} ${lost.fingerDetail}", lost.count in 495L..505L && lost.imageOnlyCount < 100)
        assertTrue(lost.approximate)
    }

    private fun run(step: Int, fingerBias: Double = 0.0): ScrollAnalysis {
        val width = 160; val r = 32; val height = 134; val rowCount = 101
        val random = Random(77)
        val egg = ByteArray(width * r) { index ->
            val x = index % width % 32; val y = index / width
            if (x in 6..25 && y in 4..26) random.nextInt(200).toByte() else 255.toByte()
        }
        val document = ByteArray(egg.size * rowCount) { egg[it % egg.size] }
        val end = rowCount * r - height
        val session = ScrollAnalysisSession(5, r, allowEstimation = true)
        var time = 1L; var id = 0L; var position = 0; var finger = 0.0
        fun frame(offset: Int) { session.offer(GrayScrollFrame(width, height,
            document.copyOfRange(offset * width, (offset + height) * width), time, ++id)); time += 16_666_667L }
        repeat(4) { frame(0) }
        while (position < end) {
            val pass = 109
            val target = position + pass
            // Fast stroke: 0.6 row per frame, more than the half-row the image can resolve on identical rows.
            var at = position
            while (at < target) { at = minOf(target, at + step); frame(minOf(at, end)) }
            finger += pass + fingerBias
            val blocked = target > end
            position = minOf(target, end)
            session.fingerMark(FingerMark(time, finger, 0, blocked))
            repeat(4) { frame(position) }
        }
        return session.finish()
    }
}
