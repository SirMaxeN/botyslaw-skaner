package eu.komars.forgescanner.vision

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import eu.komars.forgescanner.capture.CaptureConnection
import eu.komars.forgescanner.model.GrayScrollFrame
import eu.komars.forgescanner.model.InventoryEndDetector
import eu.komars.forgescanner.model.SavedCalibration
import eu.komars.forgescanner.model.ScrollAnalysis
import eu.komars.forgescanner.model.ScrollSequenceAnalyzer
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/** Bounded in-memory video sampling. No movie file, microphone or image upload. */
class InventoryRecording(
    private val calibration: SavedCalibration,
    private val width: Int,
    private val height: Int,
    private val progress: (Int) -> Unit,
    private val analyzing: () -> Unit,
    private val complete: (Result<ScrollAnalysis>) -> Unit,
    private val frameSource: (((Bitmap?, String?) -> Unit) -> Unit)? = null,
    private val guidance: (String) -> Unit = {},
    private val diagnostic: (ScrollAnalysis) -> Unit = {},
    private val countProgress: (Long) -> Unit = {}
) {
    private val handler = Handler(Looper.getMainLooper())
    private val frames = mutableListOf<GrayScrollFrame>()
    private var active = false
    private var cancelled = false
    private var nextTickAt = 0L
    private var lastAdviceAt = 0L
    private var adviceBusy = false
    private val endDetector = InventoryEndDetector()
    private var endDetected = false
    private var countedFrames = 0
    private val liveCounter by lazy { eu.komars.forgescanner.model.LiveInventoryCounter(calibration.columns, sampledRowHeight) }
    private var startedAt = 0L
    private val maximumBytes = 64L * 1024 * 1024
    private val sampleWidth = 160
    private val scale = sampleWidth.toDouble() / calibration.region.width()
    private val sampleHeight = (calibration.region.height() * scale).roundToInt().coerceIn(16, 600)
    private val sampledRowHeight = (calibration.rowHeightPx.toDouble() / calibration.region.height() * sampleHeight).roundToInt().coerceAtLeast(8)

    fun start(first: Bitmap) {
        check(!active && frames.isEmpty())
        frames += sample(first)
        active = true
        progress(frames.size)
        startedAt = SystemClock.uptimeMillis()
        nextTickAt = startedAt + 16
        handler.postAtTime(::tick, nextTickAt)
    }

    private fun tick() {
        if (!active) return
        val accept: (GrayScrollFrame?, String?) -> Unit = accept@{ frame, error ->
            if (!active) return@accept
            if (frame == null) {
                cancel()
                complete(Result.failure(IllegalStateException(error ?: "Odczyt został przerwany. Nagraj listę ponownie.")))
                return@accept
            }
            if ((frames.size + 1L) * sampleWidth * sampleHeight > maximumBytes ||
                SystemClock.uptimeMillis() - startedAt > 90_000) {
                cancel()
                complete(Result.failure(IllegalStateException("Skanowanie trwało za długo. Wróć na górę i zeskanuj całą listę ponownie.")))
                return@accept
            }
            frames += frame
            advise()
            // Rebuilding the overlay for every frame would slow capture and scrolling down.
            if (frames.size % 10 == 0 || frameSource != null) progress(frames.size)
            if (!active) return@accept
            nextTickAt = maxOf(nextTickAt + 16, SystemClock.uptimeMillis() + 1)
            handler.postAtTime(::tick, nextTickAt)
        }
        val injected = frameSource
        if (injected == null) {
            CaptureConnection.requestGrayRegion(calibration.region, width, height, sampleWidth, sampleHeight, accept)
        } else injected { bitmap, error ->
            try {
                if (bitmap == null || bitmap.width != width || bitmap.height != height) accept(null, error)
                else accept(sample(bitmap), null)
            } catch (_: Exception) { accept(null, "Nie udało się odczytać obszaru listy.") }
            finally { bitmap?.recycle() }
        }
    }

    private fun advise() {
        val now = SystemClock.uptimeMillis()
        if (adviceBusy || frames.size < 2 || now - lastAdviceAt < 120) return
        adviceBusy = true; lastAdviceAt = now
        val previous = frames[frames.lastIndex - 1]
        val current = frames.last()
        val fresh = frames.subList(countedFrames, frames.size).toList()
        countedFrames = frames.size
        WORKER.execute {
            fresh.forEach(liveCounter::observe)
            val currentCount = liveCounter.count
            val hint = ScrollSequenceAnalyzer().guidance(previous, current, sampledRowHeight)
            val end = endDetector.observe(current, sampledRowHeight, now, calibration.columns)
            handler.post {
                adviceBusy = false
                if (active && !cancelled) {
                    countProgress(currentCount)
                    guidance(when (end) {
                        InventoryEndDetector.Status.CHECKING -> "Lista mieści się? Nie przewijaj — sprawdzam."
                        InventoryEndDetector.Status.VISIBLE_END -> "Widoczny koniec listy — liczę."
                        else -> hint.label
                    })
                    if (end == InventoryEndDetector.Status.VISIBLE_END) { endDetected = true; stop() }
                }
            }
        }
    }

    fun stop() {
        if (!active) return
        active = false
        handler.removeCallbacksAndMessages(null)
        analyzing()
        val captured = frames.toList(); frames.clear()
        WORKER.execute {
            val result = runCatching {
                val analysis = ScrollSequenceAnalyzer().analyze(captured, calibration.columns, sampledRowHeight, allowEstimation = true)
                diagnostic(analysis)
                analysis.copy(approximate = analysis.approximate || !endDetected,
                    message = if (analysis.approximate) analysis.message + if (endDetected) " Koniec listy widoczny." else " Sprawdź również koniec listy."
                    else if (endDetected) "Widoczny koniec listy. Sprawdź liczbę i ostatni rząd."
                    else "≈ ESTYMACJA: koniec listy niepotwierdzony. Sprawdź, czy zeskanowano ją w całości.")
            }
            handler.post { if (!cancelled) complete(result) }
        }
    }

    fun cancel() { cancelled = true; active = false; frames.clear(); handler.removeCallbacksAndMessages(null) }

    private fun sample(bitmap: Bitmap): GrayScrollFrame {
        val rect = calibration.region
        val crop = Bitmap.createBitmap(bitmap, rect.left, rect.top, rect.width(), rect.height())
        val scaled = Bitmap.createScaledBitmap(crop, sampleWidth, sampleHeight, true)
        val pixels = IntArray(sampleWidth * sampleHeight)
        scaled.getPixels(pixels, 0, sampleWidth, 0, 0, sampleWidth, sampleHeight)
        val gray = ByteArray(pixels.size) { index ->
            val value = pixels[index]
            ((Color.red(value) * 299 + Color.green(value) * 587 + Color.blue(value) * 114) / 1000).toByte()
        }
        if (scaled !== crop && scaled !== bitmap) scaled.recycle()
        if (crop !== bitmap) crop.recycle()
        return GrayScrollFrame(sampleWidth, sampleHeight, gray, SystemClock.elapsedRealtimeNanos())
    }
    companion object {
        private val WORKER = Executors.newSingleThreadExecutor { task -> Thread(task, "ScrollAnalysis").apply { isDaemon = true } }
    }
}
