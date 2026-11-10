package eu.komars.forgescanner.automation

import eu.komars.forgescanner.model.*
import kotlin.math.roundToInt

/** Player opens the top of the list, scrolls slowly, and signals its end by leaving the screen. */
internal class RecordingInventory(
    val kind: GameScreenKind, private val key: String, private val baseCount: Int,
    private val width: Int, private val height: Int, private val region: PixelRegion, private val rowHeight: Int
) {
    private val session = ScrollAnalysisSession(5, rowHeight)
    private val end = InventoryEndDetector()
    private var endVisible = false

    fun offer(w: Int, h: Int, pixels: IntArray, nowMillis: Long) {
        if (w != width || h != height) return
        val frame = GrayScrollFrame(region.width, region.height, ByteArray(region.width * region.height) { index ->
            gray(pixels[(region.top + index / region.width) * w + region.left + index % region.width])
        }, timestampNanos = nowMillis * 1_000_000)
        session.offer(frame)
        endVisible = end.observe(frame, rowHeight, nowMillis) == InventoryEndDetector.Status.VISIBLE_END
    }

    fun finish(): RecordingValue? {
        val result = session.finish()
        if (result.failureFrame != null || result.uncertainFrames > 0 || result.count < baseCount) return null
        return RecordingValue(key, result.count - baseCount, !endVisible)
    }

    companion object {
        private fun gray(color: Int): Byte {
            val red = color ushr 16 and 255
            val green = color ushr 8 and 255
            val blue = color and 255
            return ((red * 299 + green * 587 + blue * 114) / 1000).toByte()
        }

        fun begin(kind: GameScreenKind, detector: GameUiGeometryDetector, width: Int, height: Int, pixels: IntArray): RecordingInventory? {
            val target = when (kind) {
                GameScreenKind.MOUNTS -> Triple(AutoScanStep.MOUNT_LIST, "mounts", 1)
                GameScreenKind.PETS -> Triple(AutoScanStep.PET_LIST, "pets", 3)
                else -> return null
            }
            val rough = detector.detect(target.first)?.region ?: return null
            val frame = GrayScrollFrame(width, height, ByteArray(pixels.size) { gray(pixels[it]) })
            val grid = GridGeometryDetector().detect(frame, rough) ?: return null
            val region = PixelRegion(grid.region.left, rough.top, grid.region.right, minOf(rough.bottom, grid.region.bottom))
            if (region.width < 20 || region.height < 16) return null
            return RecordingInventory(kind, target.second, target.third, width, height, region, grid.rowHeight.roundToInt().coerceAtLeast(8))
        }
    }
}
