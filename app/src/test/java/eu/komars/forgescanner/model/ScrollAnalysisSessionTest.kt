package eu.komars.forgescanner.model

import org.junit.Assert.*
import org.junit.Test
import java.util.Random

class ScrollAnalysisSessionTest {
    private fun sequence(step: Int = 8): List<GrayScrollFrame> {
        val width = 160; val row = 32; val height = 134; val rowCount = 101
        val random = Random(1701)
        val repeated = ByteArray(width * row) { index ->
            val x = index % width % 32; val y = index / width
            if (x in 6..25 && y in 4..26) random.nextInt(200).toByte() else 255.toByte()
        }
        val document = ByteArray(repeated.size * rowCount) { repeated[it % repeated.size] }
        val end = rowCount * row - height
        val offsets = (0..end step step).toMutableList().apply { if (last() != end) add(end) }
        return offsets.mapIndexed { index, offset -> GrayScrollFrame(width, height,
            document.copyOfRange(offset * width, (offset + height) * width),
            1L + index * 16_666_667L, index + 1L) }
    }

    @Test fun progressUsesFullDetailAndDoesNotRemainAtThreeHundredForFiveHundredItems() {
        val frames = sequence()
        val session = ScrollAnalysisSession(5, 32, allowEstimation = true)
        frames.forEach(session::offer)
        assertTrue("Progress should be within one viewport of the visible end", session.count >= 480)
        val final = session.finish()
        assertEquals(505L, final.count)
        assertEquals(final.count, session.count)
        assertEquals(0, final.uncertainFrames)
        assertFalse(final.approximate)
        assertEquals(final, session.finish())
    }

    @Test fun captureGapsRemainExplicitInTheSharedFinalResult() {
        val session = ScrollAnalysisSession(5, 32, allowEstimation = true)
        sequence().forEachIndexed { index, frame ->
            session.offer(frame.copy(timestampNanos = frame.timestampNanos + if (index >= 20) 300_000_000L else 0L))
        }
        val final = session.finish()
        assertTrue(final.uncertainFrames > 0)
        assertTrue(final.approximate)
        assertTrue(final.largestGapMillis >= 300)
    }
    @Test fun fasterRepeatedRowsRetainAllFiveHundredAndFiveItems() {
        val session = ScrollAnalysisSession(5, 32, allowEstimation = true)
        sequence(step = 9).forEach(session::offer)
        val final = session.finish()
        assertEquals(505L, final.count)
        assertEquals(0, final.uncertainFrames)
        assertFalse(final.approximate)
    }
}
