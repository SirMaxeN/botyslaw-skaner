package eu.komars.forgescanner.capture

import org.junit.Assert.*
import org.junit.Test

class GrayRegionFrameCacheTest {
    private val source = GrayRegionFrameCache.Source(1, 1000, 1080, 2340, 0, 0, 1080, 2340)
    private val region = GrayRegionFrameCache.Region(100, 200, 800, 900, 160, 180)

    @Test fun unchangedNativeFrameReusesConversionAndItsTrueSourceMetadata() {
        val cache = GrayRegionFrameCache()
        var conversions = 0
        fun read() = cache.read(source, region) {
            conversions++; ByteArray(region.outputWidth * region.outputHeight) { 71 }
        }
        val first = read()
        repeat(60) { assertSame(first, read()) }
        assertEquals(1, conversions)
        assertEquals(1000L, first.timestampNanos)
        assertEquals(1L, first.sourceFrameId)
        assertTrue(first.pixels.all { it == 71.toByte() })
    }

    @Test fun everyFreshSourceOrDisplayCropChangeRequiresAnotherConversion() {
        val cache = GrayRegionFrameCache()
        var conversions = 0
        val sources = listOf(source, source.copy(frameId = 2), source.copy(timestampNanos = 2000),
            source.copy(imageWidth = 1200), source.copy(imageHeight = 2400),
            source.copy(cropLeft = 10, cropWidth = 1070), source.copy(cropTop = 10, cropHeight = 2330),
            source.copy(cropWidth = 1000), source.copy(cropHeight = 2200))
        sources.forEach { native ->
            val result = cache.read(native, region) {
                conversions++; ByteArray(region.outputWidth * region.outputHeight)
            }
            assertEquals(native.frameId, result.sourceFrameId)
            assertEquals(native.timestampNanos, result.timestampNanos)
        }
        assertEquals(sources.size, conversions)
    }

    @Test fun regionPositionDimensionsAndOutputSizeAreDistinctAndOnlyTwoEntriesSurvive() {
        val cache = GrayRegionFrameCache()
        var conversions = 0
        fun read(key: GrayRegionFrameCache.Region) = cache.read(source, key) {
            conversions++; ByteArray(key.outputWidth * key.outputHeight)
        }
        val first = read(region)
        val probe = region.copy(outputWidth = 96, outputHeight = 108)
        val second = read(probe)
        assertSame(first, read(region))
        val moved = region.copy(left = 101)
        read(moved)
        assertSame(first, read(region))
        assertNotSame(second, read(probe)) // Least-recently-used entry was evicted.
        assertEquals(4, conversions)
        for (key in listOf(region.copy(top = 201), region.copy(width = 799), region.copy(height = 899))) {
            read(key)
        }
        assertEquals(7, conversions)
    }

    @Test fun clearAndConversionFailureNeverExposePreviousCachedPixels() {
        val cache = GrayRegionFrameCache()
        val first = cache.read(source, region) { ByteArray(region.outputWidth * region.outputHeight) }
        cache.clear()
        var failed = false
        try { cache.read(source, region) { error("conversion failed") } }
        catch (_: IllegalStateException) { failed = true }
        assertTrue(failed)
        val next = cache.read(source, region) { ByteArray(region.outputWidth * region.outputHeight) { 99 } }
        assertNotSame(first, next)
        assertTrue(next.pixels.all { it == 99.toByte() })
    }
}
