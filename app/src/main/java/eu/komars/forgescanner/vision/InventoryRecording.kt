package eu.komars.forgescanner.vision

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import eu.komars.forgescanner.BuildConfig
import eu.komars.forgescanner.capture.CaptureConnection
import eu.komars.forgescanner.model.GrayScrollFrame
import eu.komars.forgescanner.model.InventoryEndDetector
import eu.komars.forgescanner.model.SavedCalibration
import eu.komars.forgescanner.model.ScrollAnalysis
import eu.komars.forgescanner.model.ScrollSequenceAnalyzer
import eu.komars.forgescanner.model.SourceFrameSequence
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
    private val countProgress: (Long) -> Unit = {},
    private val endObservation: (InventoryEndDetector.Status) -> Unit = {},
    private val sourceFrameTimestamp: (Long) -> Unit = {}
) {
    private val handler = Handler(Looper.getMainLooper())
    private val frames = mutableListOf<GrayScrollFrame>()
    private val sourceFrames = SourceFrameSequence()
    @Volatile private var active = false
    @Volatile private var cancelled = false
    private var nextTickAt = 0L
    private var lastAdviceAt = 0L
    private var adviceBusy = false
    private var lastEndCheckAt = 0L
    private var endCheckBusy = false
    private var endStatus = InventoryEndDetector.Status.NOT_VISIBLE
    private val endDetector = InventoryEndDetector()
    private var endDetected = false
    private var countedFrames = 0
    private val streaming by lazy { eu.komars.forgescanner.model.ScrollAnalysisSession(calibration.columns, sampledRowHeight, allowEstimation = true) }
    private var streamedFrames = 0
    @Volatile private var streamFailure: Throwable? = null
    private var startedAt = 0L
    private val captureTiming = if (BuildConfig.DEBUG) CaptureTiming() else null
    private val maximumBytes = 128L * 1024 * 1024
    private val sampleWidth = 160
    private val scale = sampleWidth.toDouble() / calibration.region.width()
    private val sampleHeight = (calibration.region.height() * scale).roundToInt().coerceIn(16, 600)
    private val sampledRowHeight = (calibration.rowHeightPx.toDouble() / calibration.region.height() * sampleHeight).roundToInt().coerceAtLeast(8)

    fun start(first: Bitmap) {
        check(!active && frames.isEmpty())
        // This calibration bitmap has no native source timestamp. Do not mix its
        // polling clock with Image.timestamp from the subsequent source frames.
        frames += sample(first).copy(timestampNanos = 0)
        active = true
        progress(frames.size)
        startedAt = SystemClock.uptimeMillis()
        nextTickAt = startedAt + 16
        handler.postAtTime(::tick, nextTickAt)
    }

    private fun tick() {
        if (!active) return
        val requestedAt = SystemClock.elapsedRealtimeNanos()
        val accept: (GrayScrollFrame?, String?) -> Unit = accept@{ frame, error ->
            if (!active) return@accept
            if (frame == null) {
                cancel()
                complete(Result.failure(IllegalStateException(error ?: "Odczyt został przerwany. Nagraj listę ponownie.")))
                return@accept
            }
            val fresh = sourceFrames.accept(frame)
            captureTiming?.observe(frame, fresh, requestedAt)
            if ((frames.size + if (fresh) 1L else 0L) * sampleWidth * sampleHeight > maximumBytes ||
                SystemClock.uptimeMillis() - startedAt > 90_000) {
                cancel()
                complete(Result.failure(IllegalStateException("Skanowanie trwało za długo. Wróć na górę i zeskanuj całą listę ponownie.")))
                return@accept
            }
            if (fresh) {
                frames += frame
                // Native acquisition cadence, not our faster cached polling cadence, limits safe scrolling.
                if (frameSource == null && frame.sourceFrameId > 0 && frame.timestampNanos > 0) {
                    sourceFrameTimestamp(frame.timestampNanos)
                }
            }
            // A static app can legitimately stop rendering new Images. Continue
            // checking its visible empty footer on wall-clock time, but do not
            // feed cached polls into motion estimation or the unique-frame count.
            checkEnd(frame)
            advise()
            // Rebuilding the overlay for every frame would slow capture and scrolling down.
            if (fresh && (frames.size % 10 == 0 || frameSource != null)) progress(frames.size)
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

    /** A footer check must never wait behind motion matching or replay analysis. */
    private fun checkEnd(observation: GrayScrollFrame) {
        val now = SystemClock.uptimeMillis()
        if (endCheckBusy || now - lastEndCheckAt < 100) return
        endCheckBusy = true; lastEndCheckAt = now
        END_WORKER.execute {
            val began = if (BuildConfig.DEBUG) SystemClock.elapsedRealtimeNanos() else 0L
            val status = endDetector.observe(observation, sampledRowHeight, now, calibration.columns)
            if (BuildConfig.DEBUG && SystemClock.elapsedRealtimeNanos() - began > 20_000_000L) {
                Log.d(PERF_TAG, "slowEndCheckMs=${(SystemClock.elapsedRealtimeNanos() - began) / 1_000_000}")
            }
            handler.post {
                endCheckBusy = false
                if (!active || cancelled) return@post
                endStatus = status
                // The driver can stop recording from its callback. Set confidence first.
                if (status == InventoryEndDetector.Status.VISIBLE_END) endDetected = true
                endObservation(status)
                if (!active || cancelled) return@post
                when (status) {
                    InventoryEndDetector.Status.CHECKING -> guidance("Lista mieści się? Nie przewijaj — sprawdzam.")
                    InventoryEndDetector.Status.VISIBLE_END -> {
                        guidance("Widoczny koniec listy — liczę.")
                        stop()
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun advise() {
        val now = SystemClock.uptimeMillis()
        if (adviceBusy || frames.isEmpty() || now - lastAdviceAt < 120) return
        adviceBusy = true; lastAdviceAt = now
        // One bounded job at a time. All distinct full-resolution frames remain in the
        // authoritative replay buffer; only the provisional counter uses smaller images.
        val fresh = frames.subList(countedFrames, frames.size).toList()
        countedFrames = frames.size
        LIVE_WORKER.execute {
            val began = if (BuildConfig.DEBUG) SystemClock.elapsedRealtimeNanos() else 0L
            for (frame in fresh) {
                if (cancelled) break
                if (streamFailure == null) {
                    try { streaming.offer(frame); streamedFrames++ }
                    catch (error: Exception) { streamFailure = error }
                }
            }
            if (BuildConfig.DEBUG && SystemClock.elapsedRealtimeNanos() - began > 80_000_000L) {
                Log.d(PERF_TAG, "slowAdvisoryMs=${(SystemClock.elapsedRealtimeNanos() - began) / 1_000_000}, batch=${fresh.size}")
            }
            val currentCount = streaming.count
            val hint = streaming.guidance
            handler.post {
                adviceBusy = false
                if (active && !cancelled) {
                    countProgress(currentCount)
                    if (endStatus == InventoryEndDetector.Status.NOT_VISIBLE) guidance(hint.label)
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
        val confirmedEnd = endDetected
        LIVE_WORKER.execute {
            if (cancelled) return@execute
            val result = runCatching {
                val began = if (BuildConfig.DEBUG) SystemClock.elapsedRealtimeNanos() else 0L
                streamFailure?.let { throw it }
                // The same worker owns the stream and its final flush; no duplicate full replay.
                captured.drop(streamedFrames).forEach { frame -> streaming.offer(frame); streamedFrames++ }
                val analysis = streaming.finish()
                if (BuildConfig.DEBUG) Log.d(PERF_TAG,
                    "replayMs=${(SystemClock.elapsedRealtimeNanos() - began) / 1_000_000}, frames=${captured.size}, uncertain=${analysis.uncertainFrames}")
                diagnostic(analysis)
                analysis.copy(approximate = analysis.approximate || !confirmedEnd,
                    message = if (analysis.approximate) analysis.message + if (confirmedEnd) " Koniec listy widoczny." else " Sprawdź również koniec listy."
                    else if (confirmedEnd) "Widoczny koniec listy. Sprawdź liczbę i ostatni rząd."
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
    /** DEBUG-only timing: no screenshots, OCR values, credentials or user data. */
    private class CaptureTiming {
        private var since = SystemClock.uptimeMillis()
        private var polls = 0
        private var distinct = 0
        private var maxRequestNanos = 0L
        private var lastSourceTimestamp = 0L
        private val sourceGaps = ArrayList<Long>()

        fun observe(frame: GrayScrollFrame, fresh: Boolean, requestedAt: Long) {
            polls++
            maxRequestNanos = maxOf(maxRequestNanos, SystemClock.elapsedRealtimeNanos() - requestedAt)
            if (fresh) {
                distinct++
                if (lastSourceTimestamp > 0 && frame.timestampNanos > lastSourceTimestamp) {
                    sourceGaps += frame.timestampNanos - lastSourceTimestamp
                }
                lastSourceTimestamp = frame.timestampNanos
            }
            val now = SystemClock.uptimeMillis()
            if (now - since < 1000) return
            val gaps = sourceGaps.sorted()
            fun percentile(fraction: Double) = if (gaps.isEmpty()) 0L
                else gaps[((gaps.lastIndex * fraction).roundToInt())] / 1_000_000
            Log.d(PERF_TAG, "windowMs=${now - since}, polls=$polls, fresh=$distinct, cached=${polls - distinct}, " +
                "sourceGapP50Ms=${percentile(.50)}, sourceGapP90Ms=${percentile(.90)}, sourceGapMaxMs=${percentile(1.0)}, " +
                "requestMaxMs=${maxRequestNanos / 1_000_000}")
            since = now; polls = 0; distinct = 0; maxRequestNanos = 0; sourceGaps.clear()
        }
    }

    companion object {
        private const val PERF_TAG = "InventoryCapture"
        private val LIVE_WORKER = Executors.newSingleThreadExecutor { task -> Thread(task, "InventoryProgress").apply { isDaemon = true } }
        private val END_WORKER = Executors.newSingleThreadExecutor { task -> Thread(task, "InventoryEnd").apply { isDaemon = true } }
        
    }
}
