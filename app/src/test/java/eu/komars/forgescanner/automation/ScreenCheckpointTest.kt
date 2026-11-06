package eu.komars.forgescanner.automation

import org.junit.Assert.*
import org.junit.Test

class ScreenCheckpointTest {
    private val pixels = List(ScreenCheckpoint.WIDTH * ScreenCheckpoint.HEIGHT) { i -> if (i % 12 < 3) 30 else 255 }
    @Test fun masksIgnoreDynamicInventoryButRetainTheVisibleLayout() {
        val masked = pixels.mapIndexed { index, value -> if (index in 100..900) -1 else value }
        val source = ScreenCheckpoint(.5f, masked)
        val changedInventory = ScreenCheckpoint(.5f, pixels.mapIndexed { i, v -> if (i in 100..900) 0 else v })
        assertTrue(source.matches(changedInventory))
        assertFalse(source.matches(ScreenCheckpoint(.5f, pixels.map { 255 - it })))
    }
    @Test fun plainWhiteFramesCorruptProfilesAndChangedAspectAreRejected() {
        val source = ScreenCheckpoint(.5f, pixels)
        assertFalse(ScreenCheckpoint(.5f, List(pixels.size) { 255 }).valid)
        assertFalse(source.matches(ScreenCheckpoint(.8f, pixels)))
        assertFalse(source.matches(ScreenCheckpoint(.5f, listOf(0))))
        assertFalse(ScreenCheckpoint(Float.NaN, pixels).valid)
    }
}
