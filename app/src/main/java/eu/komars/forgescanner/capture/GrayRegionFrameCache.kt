package eu.komars.forgescanner.capture

import eu.komars.forgescanner.model.GrayScrollFrame

/** Capture-worker-only cache. Shared grayscale arrays are read-only after conversion. */
internal class GrayRegionFrameCache {
    data class Source(val frameId: Long, val timestampNanos: Long, val imageWidth: Int, val imageHeight: Int,
        val cropLeft: Int, val cropTop: Int, val cropWidth: Int, val cropHeight: Int)
    data class Region(val left: Int, val top: Int, val width: Int, val height: Int,
        val outputWidth: Int, val outputHeight: Int)

    private var source: Source? = null
    private val entries = LinkedHashMap<Region, GrayScrollFrame>(2, .75f, true)

    fun read(source: Source, region: Region, convert: () -> ByteArray): GrayScrollFrame {
        require(source.frameId > 0 && source.imageWidth > 0 && source.imageHeight > 0)
        require(source.cropLeft >= 0 && source.cropTop >= 0 && source.cropWidth > 0 && source.cropHeight > 0)
        require(source.cropLeft.toLong() + source.cropWidth <= source.imageWidth &&
            source.cropTop.toLong() + source.cropHeight <= source.imageHeight)
        require(region.left >= 0 && region.top >= 0 && region.width > 0 && region.height > 0)
        require(region.left.toLong() + region.width <= source.cropWidth &&
            region.top.toLong() + region.height <= source.cropHeight)
        require(region.outputWidth in 16..320 && region.outputHeight in 16..1200)
        if (this.source != source) {
            entries.clear()
            this.source = source
        }
        entries[region]?.let { return it }
        val result = GrayScrollFrame(region.outputWidth, region.outputHeight, convert(),
            source.timestampNanos, source.frameId)
        entries[region] = result
        if (entries.size > 2) entries.remove(entries.keys.first())
        return result
    }

    fun clear() {
        source = null
        entries.clear()
    }
}
