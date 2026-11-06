package eu.komars.forgescanner.model

import org.junit.Assert.*
import org.junit.Test
import java.util.Random

class LargeInventoryReplayTest {
    @Test fun sevenHundredRepeatedEggsKeepThePartialTailThroughDenseCapture() {
        val width = 160; val height = 134; val rowHeight = 32
        val rowCount = 141
        val random = Random(717)
        val row = ByteArray(width * rowHeight) { index ->
            val x = index % width % 32
            val y = index / width
            if (x in 6..25 && y in 4..26) random.nextInt(200).toByte() else 255.toByte()
        }
        val document = ByteArray(row.size * rowCount) { row[it % row.size] }
        // 140 complete rows plus two final eggs: 702, not a rounded-up 705.
        for (y in (rowCount - 1) * rowHeight until rowCount * rowHeight) {
            for (x in 64 until width) document[y * width + x] = 255.toByte()
        }
        val lastOffset = rowCount * rowHeight - height
        val offsets = (0..lastOffset step 8).toMutableList().apply {
            if (last() != lastOffset) add(lastOffset)
        }
        val frames = offsets.mapIndexed { index, offset ->
            GrayScrollFrame(width, height, document.copyOfRange(offset * width, (offset + height) * width),
                1L + index * 33_333_333L, sourceFrameId = index + 1L)
        }
        // Cached polls must not become extra captured observations or affect motion.
        val source = SourceFrameSequence()
        val accepted = frames.flatMap { listOf(it, it, it) }.filter(source::accept)
        val started = System.nanoTime()
        val analysis = ScrollSequenceAnalyzer().analyze(accepted, 5, rowHeight, allowEstimation = true)
        val elapsedMillis = (System.nanoTime() - started) / 1_000_000
        println("Large inventory replay: count=${analysis.count}, frames=${analysis.frames}, analysisMs=$elapsedMillis, " +
            "captureMs=${(frames.last().timestampNanos - frames.first().timestampNanos) / 1_000_000}, " +
            "uncertain=${analysis.uncertainFrames}, approximate=${analysis.approximate}")
        assertEquals(702L, analysis.count)
        assertEquals(frames.size, analysis.frames)
        assertEquals(0, analysis.uncertainFrames)
        assertFalse(analysis.approximate)
        assertFalse(analysis.lowConfidence)
    }
}
