package eu.komars.forgescanner.model

import org.junit.Assert.*
import org.junit.Test

class SourceFrameSequenceTest {
    private fun frame(id: Long, timestamp: Long = id * 16_000_000L, value: Byte = 10) =
        GrayScrollFrame(16, 16, ByteArray(256) { value }, timestamp, id)

    @Test fun cachedPollsAreNotFreshEvenIfTheirCopiedPixelsDiffer() {
        val sequence = SourceFrameSequence()
        assertTrue(sequence.accept(frame(1)))
        assertFalse(sequence.accept(frame(1, value = 20)))
        assertFalse(sequence.accept(frame(1, timestamp = 90_000_000L)))
        assertTrue(sequence.accept(frame(2)))
    }

    @Test fun identicalPixelsFromDifferentNativeImagesRemainFresh() {
        val sequence = SourceFrameSequence()
        assertTrue(sequence.accept(frame(1)))
        assertTrue(sequence.accept(frame(2)))
        assertTrue(sequence.accept(frame(3)))
        assertFalse(sequence.accept(frame(2)))
    }

    @Test fun removingCachedPollsPreservesTheGenuineSourceGap() {
        val sequence = SourceFrameSequence()
        val first = frame(1, timestamp = 1)
        val next = frame(2, timestamp = 300_000_001)
        val accepted = (listOf(first) + List(20) { first } + next).filter(sequence::accept)
        assertEquals(2, accepted.size)
        assertEquals(300_000_000L, accepted[1].timestampNanos - accepted[0].timestampNanos)
        assertEquals(1, ScrollSequenceAnalyzer().analyze(accepted, 1, 8).uncertainFrames)
    }

    @Test fun staticVisibleEndCanUseWallTimeWithoutAddingMotionFrames() {
        val pixels = ByteArray(16 * 40) { index ->
            val x = index % 16; val y = index / 16
            if (y in 1..10 && x in 3..12) 50 else 255.toByte()
        }
        val static = GrayScrollFrame(16, 40, pixels, 1, 1)
        val sequence = SourceFrameSequence()
        val end = InventoryEndDetector()
        val statuses = (0L..1_000L step 125).map { time ->
            sequence.accept(static)
            end.observe(static, 16, time, columns = 1)
        }
        assertEquals(InventoryEndDetector.Status.VISIBLE_END, statuses.last())
        assertFalse(sequence.accept(static))
    }

    @Test fun cachedFullViewportNeverMeansEndJustBecauseItIsStable() {
        val pixels = ByteArray(16 * 40) { index -> if (index % 2 == 0) 20 else 255.toByte() }
        val static = GrayScrollFrame(16, 40, pixels, 1, 1)
        val end = InventoryEndDetector()
        for (time in 0L..2_000L step 125) {
            assertEquals(InventoryEndDetector.Status.NOT_VISIBLE, end.observe(static, 16, time, columns = 1))
        }
    }

    @Test fun untaggedReplayFramesAreNotDeduplicatedByPixelContent() {
        val sequence = SourceFrameSequence()
        val fixture = frame(0)
        repeat(3) { assertTrue(sequence.accept(fixture)) }
    }
}
