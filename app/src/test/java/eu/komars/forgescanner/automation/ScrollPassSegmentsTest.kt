package eu.komars.forgescanner.automation

import org.junit.Assert.*
import org.junit.Test

class ScrollPassSegmentsTest {
    @Test fun fiveRowsUseTwoLongStrokesWithoutAMillimeterRemainder() {
        val strokes = ScrollPassSegments.split(1030.0, 687, 630.0)
        assertEquals(2, strokes.size)
        assertTrue(strokes.all { it.distancePx >= 500 && it.distancePx <= 630 })
        assertEquals(1030.0, strokes.sumOf { it.distancePx }, .001)
        assertEquals(687L, strokes.sumOf { it.durationMillis })
    }
    @Test fun slowCaptureSplitsDurationWithoutLosingTravelOrTime() {
        val strokes = ScrollPassSegments.split(1030.0, 3001, 630.0)
        assertEquals(4, strokes.size)
        assertTrue(strokes.all { it.durationMillis in 80..1000 && it.distancePx <= 630 })
        assertEquals(3001L, strokes.sumOf { it.durationMillis })
        assertEquals(1030.0, strokes.sumOf { it.distancePx }, .001)
    }
    @Test fun shortTailKeepsOneControlledStroke() {
        assertEquals(listOf(ScrollPassSegments.Stroke(230.0, 250)), ScrollPassSegments.split(230.0, 250, 630.0))
    }
    @Test fun narrowViewportExtendsTimeInsteadOfCreatingInvalidShortStrokes() {
        val strokes = ScrollPassSegments.split(1030.0, 550, 70.0)
        assertEquals(15, strokes.size)
        assertTrue(strokes.all { it.durationMillis in 80..1000 && it.distancePx <= 70 })
        assertEquals(1200L, strokes.sumOf { it.durationMillis })
        assertEquals(1030.0, strokes.sumOf { it.distancePx }, .001)
    }
}
