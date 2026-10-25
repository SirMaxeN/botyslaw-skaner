package eu.komars.forgescanner.model

import java.util.Random
import org.junit.Assert.*
import org.junit.Test

class ScrollSequenceAnalyzerTest {
    @Test fun anIdenticalOlderKeyframeCanBeOneWholeRowBehind() {
        val random = Random(480)
        val row = ByteArray(80 * 40) { random.nextInt(256).toByte() }
        val frame = GrayScrollFrame(80, 120, ByteArray(80 * 120) { row[it % row.size] })
        val analyzer = ScrollSequenceAnalyzer()
        assertEquals(0.0, analyzer.translation(frame, frame, 40, 0.0)!!.pixels, .01)
        assertEquals(40.0, analyzer.translation(frame, frame, 40, 40.0)!!.pixels, .01)
        assertEquals(-40.0, analyzer.translation(frame, frame, 40, -40.0)!!.pixels, .01)
    }

    @Test fun optionalNaturalRecordingRetainsItsVisibleTwoItemTail() {
        val path = System.getenv("SCANNER_PARTIAL_TAIL_FIXTURE")
        org.junit.Assume.assumeTrue(path != null)
        val (frames, row) = readRecording(path!!)
        val result = ScrollSequenceAnalyzer().analyze(frames, 5, row, allowEstimation = true)
        println("TWO_ITEM_TAIL: $result")
        // User confirmed 372 raw items / 369 after deducting the three base pets.
        assertEquals(372L, result.count)
        assertEquals(0, result.uncertainFrames)
    }

    @Test fun mixedPetsAndRepeatedEggsSurviveChangingSpeedPausesAndReversal() {
        val random = Random(218)
        val repeated = ByteArray(80 * 40) { random.nextInt(256).toByte() }
        val image = ByteArray(80 * 40 * 30) { index ->
            val row = index / (80 * 40)
            when {
                row == 29 && index % 80 >= 48 -> 255.toByte()
                row in 6..23 -> repeated[index % repeated.size]
                else -> random.nextInt(256).toByte()
            }
        }
        val positions = mutableListOf(0)
        val speeds = listOf(1, 2, 4, 8, 12, 16, 12, 8, 4, 2)
        var offset = 0
        var step = 0
        while (offset < 1080) {
            offset = minOf(1080, offset + speeds[step++ % speeds.size])
            positions += offset
            if (step % 19 == 0) repeat(4) { positions += offset }
        }
        positions += (1076 downTo 960 step 4).toList()
        positions += (964..1080 step 4).toList()
        val frames = positions.mapIndexed { index, y ->
            GrayScrollFrame(80, 120, image.copyOfRange(y * 80, (y + 120) * 80),
                1L + index * 16_000_000L)
        }
        val result = ScrollSequenceAnalyzer().analyze(frames, 5, 40, allowEstimation = true)
        assertEquals(148L, result.count)
        assertEquals(0, result.uncertainFrames)
    }

    private fun frames(): List<GrayScrollFrame> {
        val random = Random(123)
        val image = ByteArray(80 * 320) { random.nextInt(256).toByte() }
        return (0..200 step 20).map { offset -> GrayScrollFrame(80, 120, image.copyOfRange(offset * 80, (offset + 120) * 80)) }
    }
    @Test fun countsContinuousScrollingAcrossOffsetsThatAreNotRowBoundaries() {
        val result = ScrollSequenceAnalyzer().analyze(frames(), 5, 40)
        assertEquals(40L, result.count)
        assertEquals(0, result.uncertainFrames)
    }
    @Test fun stationaryFramesAreNotCountedTwice() {
        val frame = frames().first()
        assertEquals(15L, ScrollSequenceAnalyzer().analyze(List(10) { frame }, 5, 40).count)
    }
    @Test fun unmatchableJumpIsFlaggedRatherThanAdded() {
        val other = GrayScrollFrame(80, 120, ByteArray(80 * 120) { 127 })
        val result = ScrollSequenceAnalyzer().analyze(listOf(frames().first(), other), 5, 40)
        assertEquals(15L, result.count)
        assertEquals(1, result.uncertainFrames)
    }
    @Test fun countsPartiallyFilledLastRowWithoutAddingBlankSlots() {
        val random = Random(9)
        val image = ByteArray(80 * 240) { index ->
            if (index / 80 >= 200 && index % 80 >= 48) 127.toByte() else random.nextInt(256).toByte()
        }
        val frames = (0..120 step 20).map { offset -> GrayScrollFrame(80, 120, image.copyOfRange(offset * 80, (offset + 120) * 80)) }
        assertEquals(28L, ScrollSequenceAnalyzer().analyze(frames, 5, 40).count)
    }
    @Test fun tracksIdenticalRowsWithoutDiscardingThemAsDuplicates() {
        val random = Random(49)
        val row = ByteArray(80 * 40) { random.nextInt(256).toByte() }
        val image = ByteArray(80 * 800) { row[it % row.size] }
        // Twenty rows, with two blank slots in the final row.
        for (y in 760 until 800) for (x in 48 until 80) image[y * 80 + x] = 127.toByte()
        val frames = (0..680 step 4).map { offset ->
            GrayScrollFrame(80, 120, image.copyOfRange(offset * 80, (offset + 120) * 80))
        }
        val result = ScrollSequenceAnalyzer().analyze(frames, 5, 40)
        assertEquals(98L, result.count)
        assertEquals(0, result.uncertainFrames)
    }
    @Test fun reversingAndPausingDoesNotCountRowsAgain() {
        val source = frames()
        val sequence = source + List(5) { source.last() } + source.reversed() + source
        val result = ScrollSequenceAnalyzer().analyze(sequence, 5, 40)
        assertEquals(40L, result.count)
        assertEquals(0, result.uncertainFrames)
    }
    @Test fun refusesToBridgeCaptureGapEvenIfTheImagesLookIdentical() {
        val first = frames().first()
        val result = ScrollSequenceAnalyzer().analyze(listOf(
            first.copy(timestampNanos = 1), first.copy(timestampNanos = 300_000_001)), 5, 40)
        assertEquals(1, result.uncertainFrames)
    }
    @Test fun estimateRecoversAfterATimingGapAndStillCountsTheTail() {
        val source = frames().mapIndexed { index, frame ->
            frame.copy(timestampNanos = 1L + index * 20_000_000L + if (index >= 5) 200_000_000L else 0)
        }
        val result = ScrollSequenceAnalyzer().analyze(source, 5, 40, allowEstimation = true)
        assertTrue(result.approximate)
        assertEquals(40L, result.count)
        assertTrue(result.message.contains("ESTYMACJA"))
    }
    @Test fun estimateDoesNotInventRowsDuringAStationaryCaptureGap() {
        val first = frames().first()
        val result = ScrollSequenceAnalyzer().analyze(listOf(first.copy(timestampNanos = 1),
            first.copy(timestampNanos = 300_000_001)), 5, 40, allowEstimation = true)
        assertTrue(result.approximate)
        assertEquals(15L, result.count)
    }
    @Test fun subpixelMotionDoesNotLoseOrAddWholeRows() {
        val random = Random(87)
        val image = ByteArray(80 * 261) { random.nextInt(256).toByte() }
        val frames = (0..112).map { step ->
            val offset = step * 1.25
            val pixels = ByteArray(80 * 120) { index ->
                val y = index / 80 + offset.toInt(); val x = index % 80
                val fraction = offset - offset.toInt()
                val a = image[y * 80 + x].toInt() and 255
                val b = image[(y + 1) * 80 + x].toInt() and 255
                (a * (1 - fraction) + b * fraction).toInt().toByte()
            }
            GrayScrollFrame(80, 120, pixels)
        }
        val result = ScrollSequenceAnalyzer().analyze(frames, 5, 20)
        assertEquals(65L, result.count)
        assertEquals(0, result.uncertainFrames)
    }
    @Test fun lastSingleEggCountsWhenOnlyTheBottomMarginIsClipped() {
        val random = Random(9)
        val image = ByteArray(80 * 240) { index ->
            if (index / 80 >= 200 && index % 80 >= 16) 255.toByte() else random.nextInt(256).toByte()
        }
        val frames = (0..120 step 4).map { offset ->
            GrayScrollFrame(80, 119, image.copyOfRange(offset * 80, (offset + 119) * 80))
        }
        assertEquals(26L, ScrollSequenceAnalyzer().analyze(frames, 5, 40).count)
    }
    @Test fun liveGuidanceWarnsBeforeLargeMovementAndOnLostMatches() {
        val random = Random(123)
        val image = ByteArray(80 * 240) { random.nextInt(256).toByte() }
        fun frame(offset: Int) = GrayScrollFrame(80, 120, image.copyOfRange(offset * 80, (offset + 120) * 80))
        val analyzer = ScrollSequenceAnalyzer()
        assertEquals(ScrollGuidance.SCROLL, analyzer.guidance(frame(0), frame(3), 40))
        assertEquals(ScrollGuidance.SLOW, analyzer.guidance(frame(0), frame(9), 40))
        assertEquals(ScrollGuidance.PAUSE, analyzer.guidance(frame(0), frame(18), 40))
        assertEquals(ScrollGuidance.PAUSE, analyzer.guidance(frame(0),
            GrayScrollFrame(80, 120, ByteArray(80 * 120) { 127 }), 40))
    }
    private fun readRecording(path: String): Pair<List<GrayScrollFrame>, Int> =
        java.io.DataInputStream(java.io.File(path).inputStream().buffered()).use { input ->
            val width = input.readInt(); val height = input.readInt(); val row = input.readInt()
            val frames = List(input.readInt()) {
                val time = input.readLong()
                GrayScrollFrame(width, height, ByteArray(width * height).also { input.readFully(it) }, time)
            }
            frames to row
        }
    @Test fun analyzesOptionalFullRealRecording() {
        val path = System.getenv("SCANNER_VIDEO_FIXTURE")
        org.junit.Assume.assumeTrue(path != null)
        val (frames, row) = readRecording(path!!)
        val result = ScrollSequenceAnalyzer().analyze(frames, 5, row)
        println("REAL_RECORDING: $result")
        assertEquals(0, result.uncertainFrames)
        assertEquals(336L, result.count) // User counted 67 complete rows plus one occupied slot.
        val detector = InventoryEndDetector()
        var adviceAt = 0L
        val stopAt = frames.indices.firstOrNull { index ->
            val now = frames[index].timestampNanos / 1_000_000
            if (now < adviceAt) false else {
                adviceAt = now + 120
                detector.observe(frames[index], row, now) == InventoryEndDetector.Status.VISIBLE_END
            }
        }
        assertNotNull("The final single egg should establish a visible end", stopAt)
        val stopped = ScrollSequenceAnalyzer().analyze(frames.take(stopAt!! + 1), 5, row)
        assertEquals("Automatic stopping must include the last egg", 336L, stopped.count)
    }
    @Test fun tracksOptionalLatestRecordingWithActualPhoneCalibration() {
        val path = System.getenv("SCANNER_LATEST_FIXTURE")
        org.junit.Assume.assumeTrue(path != null)
        val (frames, row) = readRecording(path!!)
        val result = ScrollSequenceAnalyzer().analyze(frames, 5, row)
        println("LATEST_RECORDING: $result")
        assertEquals(0, result.uncertainFrames)
        assertEquals(336L, result.count)
    }
    @Test fun optionalFastRecordingKeepsAnExplicitEstimateAndAnalyzesTheTail() {
        val path = System.getenv("SCANNER_FAST_FIXTURE")
        org.junit.Assume.assumeTrue(path != null)
        val (frames, row) = readRecording(path!!)
        val result = ScrollSequenceAnalyzer().analyze(frames, 5, row)
        val visited = mutableListOf<Int>()
        val estimate = ScrollSequenceAnalyzer { index, _, _ -> visited += index }.analyze(frames, 5, row, allowEstimation = true)
        println("FAST_ESTIMATE: $estimate")
        assertTrue(estimate.approximate)
        assertTrue("The tail must be analyzed even when stronger matching avoids a lost frame",
            visited.any { it > frames.size * .9 })
        result.failureFrame?.let { failed -> assertTrue(visited.any { it > failed }) }
    }
    @Test fun estimatesOptionalControlledFastRecordingFromTopToBottom() {
        val path = System.getenv("SCANNER_CONTROLLED_FIXTURE")
        org.junit.Assume.assumeTrue(path != null)
        val (frames, row) = readRecording(path!!)
        val trace = StringBuilder()
        val result = ScrollSequenceAnalyzer { i, offset, velocity -> trace.appendLine("$i,$offset,$velocity") }.analyze(frames, 5, row, allowEstimation = true)
        System.getenv("SCANNER_TRACE")?.let { java.io.File(it).writeText(trace.toString()) }
        println("CONTROLLED_FAST: $result")
        assertEquals("Dense rapid replay must retain all 67 rows and the last egg", 336L, result.count)
        assertTrue(result.approximate)
    }
    @Test fun warnsOfLowConfidenceForOptionalSparseFastReplay() {
        val path = System.getenv("SCANNER_SPARSE_FIXTURE")
        org.junit.Assume.assumeTrue(path != null)
        val (frames, row) = readRecording(path!!)
        val result = ScrollSequenceAnalyzer().analyze(frames, 5, row, allowEstimation = true)
        println("SPARSE_FAST_LIMITATION: $result")
        // This is a known accuracy limit, not a ground-truth accuracy assertion.
        assertTrue(result.approximate)
        assertTrue(result.lowConfidence)
        assertTrue(result.message.contains("niska pewność"))
    }
}
