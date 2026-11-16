package eu.komars.forgescanner.vision

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import eu.komars.forgescanner.capture.CaptureConnection
import eu.komars.forgescanner.capture.FrameCallback
import eu.komars.forgescanner.model.ScanTarget

/** Frames and game-font votes of earlier bursts for the same counters; a retry adds to them instead of starting over. */
typealias CounterBurstHistory = Map<ScanTarget, Pair<List<OcrAnalysis>, List<Long?>>>

/** Counts/timing only; resource values and screenshot pixels are deliberately excluded. */
data class CounterReadMetrics(val fields: Int, val sourceFrames: Int, val ocrPasses: Int, val elapsedMillis: Long)

/** Owns three fresh screenshots. Multiple calibrated counters share one OCR strip per image variant. */
class CounterBurstReader(
    private val ocr: OcrAnalyzer,
    private val freshFrame: (FrameCallback) -> Unit = { CaptureConnection.requestFreshFrame(it) },
    private val schedule: (() -> Unit) -> Unit = { Handler(Looper.getMainLooper()).postDelayed(it, 32) },
    private val diagnostics: (CounterReadMetrics) -> Unit = {},
    private val cancelFreshFrame: (FrameCallback) -> Unit = { CaptureConnection.cancelFrameRequest(it) },
    private val font: eu.komars.forgescanner.battle.GameDigitMemory? = eu.komars.forgescanner.battle.GameDigitMemory.shared
) {
    fun read(first: Bitmap, target: ScanTarget, region: Rect, isActive: () -> Boolean,
        complete: (OcrAnalysis) -> Unit) =
        readMany(first, linkedMapOf(target to region), isActive) { complete(it.getValue(target)) }

    /** Takes ownership of first. Returned analyses retain independent per-field source-frame votes. */
    fun readMany(first: Bitmap, regions: Map<ScanTarget, Rect>, isActive: () -> Boolean,
        prior: CounterBurstHistory = emptyMap(), collected: (CounterBurstHistory) -> Unit = {},
        complete: (Map<ScanTarget, OcrAnalysis>) -> Unit) {
        val width = first.width; val height = first.height
        val crops = regions.mapValues { Rect(it.value) }
        val readings = crops.keys.associateWith { mutableListOf<OcrAnalysis>() }
        val fontVotes = crops.keys.associateWith { mutableListOf<Long?>() }
        val started = SystemClock.elapsedRealtime()
        var passes = 0
        var sourceFrames = 0
        var finished = false
        var activePrefetch: CounterFramePrefetch<Bitmap>? = null
        fun release(bitmap: Bitmap) { if (!bitmap.isRecycled) bitmap.recycle() }
        fun finish(error: String? = null) {
            if (finished) return
            finished = true
            activePrefetch?.close(); activePrefetch = null
            if (isActive()) {
                runCatching { diagnostics(CounterReadMetrics(crops.size, sourceFrames,
                    passes, SystemClock.elapsedRealtime() - started)) }
                // Earlier bursts of the same counter count too: more frames, more certainty.
                val all = crops.keys.associateWith { target ->
                    val before = prior[target]
                    (before?.first.orEmpty() + readings.getValue(target)) to (before?.second.orEmpty() + fontVotes.getValue(target))
                }
                if (error == null) runCatching { collected(all) }
                complete(crops.keys.associateWith { target ->
                    if (error != null) OcrAnalysis(emptyList(), emptyList(), error, true)
                    else all.getValue(target).let { (frames, votes) -> CounterOcrConsensus.combine(frames, target, votes) }
                })
            }
        }
        if (crops.isEmpty() || crops.keys.any { it.isInventory }) {
            release(first)
            finish("Wybierz od jednego do pięciu liczników; listy wymagają skanowania ikon.")
            return
        }
        fun next(bitmap: Bitmap) {
            if (finished || !isActive()) { release(bitmap); return }
            if (bitmap.width != width || bitmap.height != height) {
                release(bitmap)
                finish("Rozmiar ekranu się zmienił. Ustaw obszar ponownie.")
                return
            }
            val prefetched = if (sourceFrames + 1 < CounterOcrConsensus.FRAME_COUNT)
                CounterFramePrefetch<Bitmap>(::release, cancelFreshFrame).also { activePrefetch = it }
            else null
            try {
                // Independent game-font vote per source frame, read before the OCR strip takes the pixels.
                for ((target, rect) in crops) {
                    val r = Rect(rect); if (!r.intersect(0, 0, bitmap.width, bitmap.height) || r.width() < 8 || r.height() < 6) { fontVotes.getValue(target) += null; continue }
                    val pixels = IntArray(r.width() * r.height()); bitmap.getPixels(pixels, 0, r.width(), r.left, r.top, r.width(), r.height())
                    font?.rememberCounterCrop(target.field.name, r.width(), r.height(), pixels)
                    fontVotes.getValue(target) += runCatching { font?.readCounter(r.width(), r.height(), pixels) }.getOrNull()
                }
                val verify = crops.keys.all { CounterOcrPassPolicy.canVerify(readings.getValue(it), it) }
                ocr.analyzeMany(bitmap, crops, verification = verify, onPass = { passes++ }) { result ->
                    if (!finished && isActive()) {
                        sourceFrames++
                        for (target in crops.keys) readings.getValue(target) += result[target] ?:
                            OcrAnalysis(emptyList(), emptyList(), "Brak odczytu tego licznika.", true)
                        if (readings.values.first().size == CounterOcrConsensus.FRAME_COUNT) finish()
                        else prefetched!!.take { frame, error ->
                            activePrefetch = null
                            if (finished || !isActive()) frame?.let(::release)
                            else if (frame != null) next(frame)
                            else {
                                for (target in crops.keys) readings.getValue(target) +=
                                    OcrAnalysis(emptyList(), emptyList(), error ?: "Brak nowej klatki.", true)
                                finish()
                            }
                        }
                    } else prefetched?.close()
                }
                // The strip owns copied pixels now. Capture the next independent source
                // while ML Kit works instead of adding a capture wait after each OCR pass.
                if (prefetched != null) {
                    fun guard() {
                        if (finished || !isActive()) prefetched.close()
                        else if (!prefetched.finished) schedule { guard() }
                    }
                    schedule {
                        if (finished || !isActive()) prefetched.close()
                        else {
                            prefetched.start(freshFrame)
                            guard()
                        }
                    }
                }
            } catch (_: Exception) {
                finish("Nie można odczytać klatki.")
            } finally {
                // OcrAnalyzer synchronously copies the selected regions into its own strip.
                release(bitmap)
            }
        }
        next(first)
    }
}
