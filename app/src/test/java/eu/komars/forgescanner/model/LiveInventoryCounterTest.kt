package eu.komars.forgescanner.model

import org.junit.Assert.*
import org.junit.Test
import java.util.Random

class LiveInventoryCounterTest {
    private fun document(rows: Int, lastRowItems: Int = 5, firstRowItems: Int = 5): ByteArray {
        val random = Random(71)
        return ByteArray(80 * rows * 40) { index ->
            val row = index / 80 / 40
            val column = index % 80 / 16
            if (row == rows-1 && column >= lastRowItems || row == 0 && column >= firstRowItems)
                255.toByte() else random.nextInt(256).toByte()
        }
    }

    private fun replay(pixels: ByteArray, offsets: List<Int>, height: Int = 120): List<GrayScrollFrame> =
        offsets.mapIndexed { index, offset ->
            GrayScrollFrame(80, height, pixels.copyOfRange(offset*80, (offset+height)*80),
                1L+index*16_000_000L)
        }

    @Test fun halfRowScrollDoesNotPermanentlyInventAWholeRow() {
        val frames = replay(document(8), (0..24 step 4).toList())
        val counter = LiveInventoryCounter(5, 40)
        frames.forEach(counter::observe)
        assertEquals("The next row's icon is still clipped", 15L, counter.count)
        assertEquals(ScrollSequenceAnalyzer().analyze(frames, 5, 40).count, counter.count)
        // Returning to the top must not preserve a rounded-up historical maximum.
        replay(document(8), listOf(20, 16, 12, 8, 4, 0)).forEach(counter::observe)
        assertEquals(15L, counter.count)
    }

    @Test fun singleTailItemIsCountedWithoutAddingFourEmptySlots() {
        val frames = replay(document(6, lastRowItems = 1), (0..120 step 4).toList(), height = 119)
        val counter = LiveInventoryCounter(5, 40)
        frames.forEach(counter::observe)
        assertEquals(26L, counter.count)
        assertEquals(ScrollSequenceAnalyzer().analyze(frames, 5, 40).count, counter.count)
        frames.reversed().forEach(counter::observe)
        assertEquals(26L, counter.count)
    }

    @Test fun countsObservedCellsRatherThanAssumingPastRowsWereFull() {
        val frames = replay(document(5, lastRowItems = 3, firstRowItems = 1), (0..80 step 4).toList())
        val counter = LiveInventoryCounter(5, 40)
        frames.forEach(counter::observe)
        assertEquals(19L, counter.count)
    }

    @Test fun singleMountStaysOneThroughRepeatedCaptures() {
        val pixels = ByteArray(80*120) { 255.toByte() }
        val icon = document(1, lastRowItems = 1)
        icon.copyInto(pixels)
        val counter = LiveInventoryCounter(5, 40)
        replay(pixels, List(100) { 0 }).forEach(counter::observe)
        assertEquals(1L, counter.count)
    }

    @Test fun repeatedEggRowsAndDuplicatedDisplayBuffersRemainSeparateItems() {
        val row = document(1)
        val pixels = ByteArray(80*40*20) { row[it % row.size] }
        // Last row contains one egg, not five.
        for (y in 19*40 until 20*40) for (x in 16 until 80) pixels[y*80+x] = 255.toByte()
        val offsets = (0..680 step 4).flatMap { listOf(it, it) }
        val frames = replay(pixels, offsets)
        val counter = LiveInventoryCounter(5, 40)
        frames.forEach(counter::observe)
        assertEquals(96L, counter.count)
    }

    @Test fun progressIncreasesWhenNewRowsEnterAndDoesNotDuplicateStationaryFrames() {
        val random = Random(71)
        val pixels = ByteArray(80*320) { random.nextInt(256).toByte() }
        val counter = LiveInventoryCounter(5, 40)
        var time = 1L
        for (offset in 0..200 step 4) {
            val frame = GrayScrollFrame(80,120,pixels.copyOfRange(offset*80,(offset+120)*80),time)
            counter.observe(frame); time+=16_000_000
        }
        assertEquals(40L,counter.count)
        val last = GrayScrollFrame(80,120,pixels.copyOfRange(200*80,320*80),time)
        repeat(10) { counter.observe(last.copy(timestampNanos=time+it*16_000_000)) }
        assertEquals(40L,counter.count)
    }
}
