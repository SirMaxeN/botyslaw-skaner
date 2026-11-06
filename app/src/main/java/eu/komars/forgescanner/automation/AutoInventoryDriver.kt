package eu.komars.forgescanner.automation

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import eu.komars.forgescanner.capture.CaptureConnection
import eu.komars.forgescanner.capture.CaptureState
import eu.komars.forgescanner.model.GrayScrollFrame
import eu.komars.forgescanner.model.InventoryEndDetector
import eu.komars.forgescanner.model.InventoryRowAccumulator
import eu.komars.forgescanner.model.SavedCalibration
import eu.komars.forgescanner.model.ScanTarget
import eu.komars.forgescanner.vision.InventoryRecording
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** Closed-loop, opt-in list scrolling. Gestures move the list; only source images count it. */
class AutoInventoryDriver {
    private val handler = Handler(Looper.getMainLooper())
    private var generation = 0L
    private var active = false
    private var recorder: InventoryRecording? = null
    private var session: GestureSession? = null
    private var calibration: SavedCalibration? = null
    private var width = 0
    private var height = 0
    private var sampleHeight = 0
    private var sampledRow = 0
    private var planner: ControlledScrollPlanner? = null
    private var captureFreshness = InventoryCaptureFreshnessGate()
    private var strokeCapture = PhysicalStrokeCaptureGate()
    private var previousStrokeSourceTimestamp = 0L
    private var dispatchedStrokes = 0L
    private var waitingForStrokeCapture = false
    private var strokeCaptureRetryScheduled = false
    private var strokeCaptureWaitGeneration = 0L
    private var previous: GrayScrollFrame? = null
    private var pendingProbe = false
    private var completedPasses = 0L
    private val passStrokes = ArrayDeque<ScrollPassSegments.Stroke>()
    private var gestureInFlight = false
    private var gestureHeld = false
    private var repositioning = false
    private var fingerX = 0f
    private var fingerY = 0f
    private var currentCount = 0L
    private var waitingForEnd = false
    private var activeCheck: () -> Boolean = { false }
    private var progress: (String, Long) -> Unit = { _, _ -> }
    private var completion: ((Result<AutoReading>) -> Unit)? = null
    private val releaseCallbacks = mutableListOf<() -> Unit>()
    private var releaseInFlight = false
    private var endProbePaused = false
    private var initialViewportChecking = false
    private var endProbeCooldownUntil = 0L
    private var endProbeGeneration = 0L
    private var releaseError: Throwable? = null
    private var initialStartedAt = 0L
    private var initialShortList = ShortViewportInventoryDetector()

    /** Uses the same recorder as manual mode; gestures only expose later list rows. */
    fun start(first: Bitmap, target: ScanTarget, calibration: SavedCalibration, session: GestureSession,
              isActive: () -> Boolean, onProgress: (String, Long) -> Unit,
              onComplete: (Result<AutoReading>) -> Unit) {
        if (gestureInFlight || gestureHeld || repositioning || releaseInFlight) {
            first.recycle(); onComplete(Result.failure(IllegalStateException("Poczekaj na zwolnienie poprzedniego gestu."))); return
        }
        cancel()
        val token = ++generation
        val problem = runCatching {
            require(target.isInventory && calibration.region.left >= 0 && calibration.region.top >= 0 &&
                calibration.region.right <= first.width && calibration.region.bottom <= first.height &&
                calibration.region.width() >= 16 && calibration.region.height() >= calibration.rowHeightPx &&
                calibration.columns in 1..12 && calibration.rowHeightPx >= 8) { "Sprawdź obszar i odstęp rzędów listy." }
            require(session.width == first.width && session.height == first.height) { "Ekran zmienił rozmiar. Ustaw pola ponownie." }
            check(isActive() && GestureConnection.foregroundGame && CaptureConnection.state == CaptureState.READY) {
                "Gra lub przechwytywanie ekranu nie jest aktywne."
            }
        }.exceptionOrNull()
        if (problem != null) { first.recycle(); onComplete(Result.failure(problem)); return }
        this.calibration = calibration; this.session = session; width = first.width; height = first.height
        sampleHeight = (calibration.region.height().toDouble() * SAMPLE_WIDTH / calibration.region.width()).roundToInt().coerceIn(16, 600)
        sampledRow = (calibration.rowHeightPx.toDouble() / calibration.region.height() * sampleHeight).roundToInt().coerceAtLeast(8)
        captureFreshness = InventoryCaptureFreshnessGate()
        strokeCapture = PhysicalStrokeCaptureGate()
        previousStrokeSourceTimestamp = 0L; dispatchedStrokes = 0L
        clearStrokeCaptureWait()
        planner = ControlledScrollPlanner(calibration.rowHeightPx.toDouble(),
            calibration.region.height().toDouble(), height * .32)
        activeCheck = isActive; progress = onProgress; completion = onComplete
        active = true; pendingProbe = false; previous = null; waitingForEnd = false
        endProbePaused = false; initialViewportChecking = true; endProbeCooldownUntil = 0L
        gestureInFlight = false; gestureHeld = false; repositioning = false
        releaseError = null
        initialStartedAt = SystemClock.uptimeMillis(); initialShortList = ShortViewportInventoryDetector()
        completedPasses = 0; currentCount = 0; passStrokes.clear()
        fingerX = calibration.region.exactCenterX()
        fingerY = bottomFinger(calibration)
        recorder = InventoryRecording(calibration, width, height,
            progress = {},
            analyzing = {
                if (active && generation == token) {
                    waitingForEnd = true
                    passStrokes.clear(); clearStrokeCaptureWait()
                    stopFinger { }
                    emit("Koniec listy — analizuję zebrane klatki.")
                }
            },
            complete = { result ->
                if (active && generation == token) finish(result.mapCatching { analysis -> AutoInventoryResultPolicy.reading(target, analysis) }, token)
            },
            countProgress = { count ->
                if (active && generation == token) { currentCount = count; emit(if (waitingForEnd) END_HINT else "Przewijam i liczę listę.") }
            },
            endObservation = { status -> observeEnd(status, token) },
            sourceFrameTimestamp = { timestamp ->
                if (active && generation == token) {
                    planner?.observeSourceFrame(timestamp)
                    strokeCapture.observe(timestamp, SystemClock.uptimeMillis())
                    if (waitingForStrokeCapture && !gestureInFlight && !releaseInFlight && !waitingForEnd && !endProbePaused) {
                        dispatchNextStroke(token)
                    }
                }
            })
        try {
            initialShortList.observe(sampleBitmap(first, calibration), sampledRow, calibration.columns, initialStartedAt)
            recorder!!.start(first)
        }
        catch (error: Exception) { finish(Result.failure(error), token) }
        finally { first.recycle() }
        if (!active || generation != token) return
        // Full viewports need no initial stationary proof. Keep that proof for a
        // genuinely short list (e.g. one mount) instead of swiping it needlessly.
        if (initialShortList.fullViewportVisible) {
            initialViewportChecking = false
            probe(token)
        } else handler.postDelayed({ watchInitialViewport(target, token) }, 120)
    }

    private fun watchInitialViewport(target: ScanTarget, token: Long) {
        if (!running(token) || waitingForEnd) return
        val config = calibration ?: return
        CaptureConnection.requestGrayRegion(config.region, width, height, SAMPLE_WIDTH, sampleHeight) { frame, error ->
            if (!running(token) || waitingForEnd) return@requestGrayRegion
            if (frame == null) { fail(error ?: "Nie można sprawdzić początkowej listy.", token); return@requestGrayRegion }
            val now = SystemClock.uptimeMillis()
            val raw = initialShortList.observe(frame, sampledRow, config.columns, now)
            if (raw != null) {
                if (raw < target.baseCount) {
                    fail("Widać mniej pozycji niż bazowych. Ustaw obszar listy ponownie.", token)
                } else {
                    currentCount = raw
                    emit("Cała lista mieści się na ekranie — nie przewijam.")
                    finish(Result.success(AutoReading(raw - target.baseCount, approximate = false,
                        source = "Cała lista i puste miejsce poniżej były widoczne przez co najmniej 900 ms. Łącznie $raw; odjęto ${target.baseCount} bazowe.")), token)
                }
            } else if (initialShortList.fullViewportVisible || now - initialStartedAt >= 1150) {
                initialViewportChecking = false
                if (!endProbePaused) probe(token)
            } else handler.postDelayed({ watchInitialViewport(target, token) }, 120)
        }
    }

    private fun sampleBitmap(bitmap: Bitmap, config: SavedCalibration): GrayScrollFrame {
        val rect = config.region
        val crop = Bitmap.createBitmap(bitmap, rect.left, rect.top, rect.width(), rect.height())
        val scaled = Bitmap.createScaledBitmap(crop, SAMPLE_WIDTH, sampleHeight, true)
        val pixels = IntArray(SAMPLE_WIDTH * sampleHeight)
        scaled.getPixels(pixels, 0, SAMPLE_WIDTH, 0, 0, SAMPLE_WIDTH, sampleHeight)
        val gray = ByteArray(pixels.size) { index ->
            val color = pixels[index]
            ((Color.red(color) * 299 + Color.green(color) * 587 + Color.blue(color) * 114) / 1000).toByte()
        }
        if (scaled !== crop && scaled !== bitmap) scaled.recycle()
        if (crop !== bitmap) crop.recycle()
        return GrayScrollFrame(SAMPLE_WIDTH, sampleHeight, gray)
    }

    fun finishManually() {
        if (!active) return
        waitingForEnd = true
        passStrokes.clear(); clearStrokeCaptureWait()
        val token = generation
        stopFinger {
            if (active && generation == token) {
                emit("Kończę listę ręcznie. Wynik będzie wymagał sprawdzenia.")
                recorder?.stop()
            }
        }
    }

    /** Does not revoke another owner's token; the surrounding scan coordinator owns global STOP. */
    fun cancel() {
        active = false; generation++; passStrokes.clear(); clearStrokeCaptureWait(); planner?.endMotion(); handler.removeCallbacksAndMessages(null)
        recorder?.cancel(); recorder = null; completion = null
        stopFinger { }
    }

    /**
     * The native recorder owns frame timing, matching and final counting, exactly as
     * in manual mode. A second per-frame matcher used to reject harmless OCR/capture
     * jitter and repeated eggs before the recorder could finish. Here a settled
     * viewport only answers whether a completed fractional-row-phase gesture moved it.
     */
    private fun probe(token: Long) {
        if (!running(token) || waitingForEnd || endProbePaused || pendingProbe || repositioning ||
            gestureInFlight || releaseInFlight || initialViewportChecking) return
        val config = calibration ?: return
        pendingProbe = true
        CaptureConnection.requestGrayRegion(config.region, width, height, SAMPLE_WIDTH, sampleHeight) { frame, error ->
            if (!active || generation != token) return@requestGrayRegion
            pendingProbe = false
            if (!running(token) || waitingForEnd || endProbePaused) return@requestGrayRegion
            if (frame == null) { fail(error ?: "Nie można odczytać listy.", token); return@requestGrayRegion }
            when (captureFreshness.observe(frame.timestampNanos, SystemClock.uptimeMillis())) {
                InventoryCaptureFreshnessGate.Status.WAIT -> {
                    emit("Czekam na nowe klatki listy.")
                    scheduleProbe(token, 32)
                    return@requestGrayRegion
                }
                InventoryCaptureFreshnessGate.Status.STALLED -> {
                    fail("Przechwytywanie listy zatrzymało się. Nie mogę potwierdzić całego wyniku. Wróć na górę i ponów odczyt.", token)
                    return@requestGrayRegion
                }
                InventoryCaptureFreshnessGate.Status.FRESH -> Unit
            }
            val old = previous
            previous = frame
            val changed = old?.let { AutoInventoryScrollPolicy.viewportChanged(it, frame) }
            // This value paces gestures and detects a blocked bottom only. It is
            // never converted to rows or added to the inventory count.
            val movement = changed?.let { if (it) config.rowHeightPx * .45 else 0.0 }
            act(planner!!.next(frame.timestampNanos, movement,
                completedGestureId = completedPasses), token)
        }
    }

    private fun act(decision: ControlledScrollPlanner.Decision, token: Long) {
        if (!running(token) || waitingForEnd || endProbePaused) return
        when (decision.kind) {
            ControlledScrollPlanner.Kind.FAILED -> fail(decision.message, token)
            ControlledScrollPlanner.Kind.CHECK_END -> checkEnd(token)
            ControlledScrollPlanner.Kind.END_VISIBLE -> { waitingForEnd = true; stopFinger { if (running(token)) recorder?.stop() } }
            ControlledScrollPlanner.Kind.WAIT -> scheduleProbe(token)
            ControlledScrollPlanner.Kind.DRAG -> {
                val pacing = planner ?: return
                passStrokes.clear()
                val strokes = runCatching {
                    ScrollPassSegments.split(decision.distancePx, decision.durationMillis, pacing.maximumSegmentDistancePx)
                }.getOrElse { error ->
                    fail(error.message ?: "Nie można bezpiecznie przewinąć zaznaczonej listy. Popraw obszar listy.", token)
                    return
                }
                passStrokes.addAll(strokes)
                dispatchNextStroke(token)
            }
        }
    }

    private fun dispatchNextStroke(token: Long) {
        if (!running(token) || waitingForEnd || endProbePaused || gestureInFlight || releaseInFlight) return
        val stroke = passStrokes.firstOrNull() ?: return
        val config = calibration ?: return
        when (strokeCapture.check(SystemClock.uptimeMillis(), previousStrokeSourceTimestamp,
            allowInitialBootstrap = dispatchedStrokes == 0L)) {
            PhysicalStrokeCaptureGate.Status.WAIT -> {
                if (!waitingForStrokeCapture) {
                    waitingForStrokeCapture = true
                    strokeCaptureWaitGeneration++
                }
                val waitGeneration = strokeCaptureWaitGeneration
                emit("Czekam na płynne klatki listy.")
                if (gestureHeld) stopFinger {
                    if (running(token) && waitingForStrokeCapture && strokeCaptureWaitGeneration == waitGeneration &&
                        !waitingForEnd && !endProbePaused) dispatchNextStroke(token)
                }
                else scheduleStrokeCaptureRetry(token, waitGeneration)
                return
            }
            PhysicalStrokeCaptureGate.Status.STALLED -> {
                fail("Przechwytywanie listy zatrzymało się. Nie mogę kontynuować bez nowych klatek. Wróć na górę i ponów odczyt.", token)
                return
            }
            PhysicalStrokeCaptureGate.Status.READY -> clearStrokeCaptureWait()
        }
        val nextY = AutoInventoryScrollPolicy.upwardDestination(fingerY, stroke.distancePx.toFloat())
        val safeTop = config.region.top + max(8f, config.rowHeightPx * .15f)
        if (nextY <= safeTop && gestureHeld) {
            repositioning = true
            stopFinger {
                repositioning = false
                if (running(token) && !waitingForEnd && !endProbePaused) {
                    val error = releaseError
                    if (error != null) fail(error.message ?: "Nie udało się zakończyć gestu.", token)
                    else dispatchNextStroke(token)
                }
            }
            return
        }
        if (nextY <= safeTop) { fail("Obszar listy jest zbyt niski do bezpiecznego przewijania.", token); return }
        val grant = session ?: return
        passStrokes.removeFirst()
        gestureInFlight = true
        previousStrokeSourceTimestamp = maxOf(strokeCapture.latestTimestampNanos, previous?.timestampNanos ?: 0L)
        dispatchedStrokes++
        planner?.beginMotion()
        emit("Płynnie przewijam i liczę listę.")
        if (eu.komars.forgescanner.BuildConfig.DEBUG) android.util.Log.d("ScannerScroll",
            "strokePx=${stroke.distancePx.toInt()} rows=${stroke.distancePx / config.rowHeightPx} ms=${stroke.durationMillis} remaining=${passStrokes.size}")
        GestureConnection.drag(grant, fingerX, fingerY, fingerX, nextY,
            durationMs = stroke.durationMillis, hold = true) { result ->
            planner?.endMotion()
            gestureInFlight = false
            if (result.isSuccess) { gestureHeld = true; fingerY = nextY }
            else { gestureHeld = false; releaseError = result.exceptionOrNull() }
            if (releaseCallbacks.isNotEmpty()) { stopFinger { }; return@drag }
            if (!running(token)) { stopFinger { }; return@drag }
            if (result.isFailure) { fail(result.exceptionOrNull()?.message ?: "Gest został zatrzymany.", token); return@drag }
            if (passStrokes.isEmpty()) { completedPasses++; scheduleProbe(token) }
            else dispatchNextStroke(token)
        }
    }

    private fun observeEnd(status: InventoryEndDetector.Status, token: Long) {
        if (!active || generation != token || waitingForEnd) return
        when (status) {
            InventoryEndDetector.Status.CHECKING -> {
                if (endProbePaused || SystemClock.uptimeMillis() < endProbeCooldownUntil) return
                endProbePaused = true
                passStrokes.clear(); clearStrokeCaptureWait()
                val pauseGeneration = ++endProbeGeneration
                previous = null
                // A held/overscrolling finger can keep the footer bouncing forever.
                // Release it once, keep recording, and let stationary image evidence settle.
                stopFinger {
                    if (!running(token) || waitingForEnd) return@stopFinger
                    val error = releaseError
                    if (error != null) fail(error.message ?: "Nie udało się zatrzymać przewijania.", token)
                    else if (!endProbePaused && !initialViewportChecking) scheduleProbe(token)
                    else emit("Sprawdzam ostatni rząd listy.")
                }
                handler.postDelayed({
                    if (running(token) && !waitingForEnd && endProbePaused && endProbeGeneration == pauseGeneration) {
                        // Animated icons can prevent a stationary proof from settling.
                        // Resume bounded controlled probes; a timeout itself never
                        // declares the list complete or adds assumed rows.
                        endProbePaused = false
                        endProbeCooldownUntil = SystemClock.uptimeMillis() + 2000
                        previous = null
                        if (!initialViewportChecking) scheduleProbe(token)
                    }
                }, 3000)
            }
            InventoryEndDetector.Status.NOT_VISIBLE -> {
                if (!endProbePaused) return
                endProbePaused = false
                endProbeGeneration++
                previous = null
                if (!initialViewportChecking && !releaseInFlight && !gestureInFlight) scheduleProbe(token)
            }
            InventoryEndDetector.Status.VISIBLE_END -> {
                waitingForEnd = true
                passStrokes.clear(); clearStrokeCaptureWait()
                stopFinger { if (running(token)) recorder?.stop() }
            }
        }
    }

    private fun checkEnd(token: Long) {
        if (!running(token) || waitingForEnd) return
        waitingForEnd = true
        passStrokes.clear(); clearStrokeCaptureWait()
        stopFinger {
            if (running(token)) {
                val error = releaseError
                if (error != null) fail(error.message ?: "Nie udało się zatrzymać przewijania.", token)
                else {
                    emit("Lista nie przesuwa się dalej — podsumowuję. Sprawdź ostatni rząd.")
                    // Three successful upward probes with an unchanged viewport stop
                    // a full final row too. Without a visible footer the shared recorder
                    // retains its approximate/end-unconfirmed flag for user review.
                    recorder?.stop()
                }
            }
        }
    }
    private fun finish(result: Result<AutoReading>, token: Long) {
        if (!active || generation != token) return
        active = false; passStrokes.clear(); clearStrokeCaptureWait(); planner?.endMotion()
        handler.removeCallbacksAndMessages(null); recorder?.cancel(); recorder = null
        val done = completion; completion = null
        stopFinger {
            if (generation == token) done?.invoke(releaseError?.let { Result.failure(it) } ?: result)
        }
    }
    private fun fail(message: String, token: Long) = finish(Result.failure(IllegalStateException(message)), token)
    private fun running(token: Long): Boolean {
        if (!active || generation != token) return false
        if (!activeCheck()) { cancel(); return false }
        if (!GestureConnection.foregroundGame || CaptureConnection.state != CaptureState.READY) {
            fail("Gra lub przechwytywanie ekranu zostało zatrzymane.", token); return false
        }
        return true
    }
    private fun emit(message: String) { progress(message, currentCount) }
    private fun scheduleProbe(token: Long, delayMillis: Long = 8) {
        handler.postDelayed({ if (running(token) && !waitingForEnd && !endProbePaused) probe(token) }, delayMillis)
    }
    private fun clearStrokeCaptureWait() {
        waitingForStrokeCapture = false
        strokeCaptureRetryScheduled = false
        strokeCaptureWaitGeneration++
        strokeCapture.resetWait()
    }
    private fun scheduleStrokeCaptureRetry(token: Long, waitGeneration: Long) {
        if (strokeCaptureRetryScheduled) return
        strokeCaptureRetryScheduled = true
        handler.postDelayed({
            if (strokeCaptureWaitGeneration != waitGeneration) return@postDelayed
            strokeCaptureRetryScheduled = false
            if (running(token) && waitingForStrokeCapture && !waitingForEnd && !endProbePaused) dispatchNextStroke(token)
        }, 32)
    }
    private fun stopFinger(done: () -> Unit) {
        releaseCallbacks += done
        if (gestureInFlight || releaseInFlight) return
        val grant = session
        if (!gestureHeld || grant == null) { finishReleaseCallbacks(); return }
        gestureHeld = false
        releaseInFlight = true
        GestureConnection.endHold(grant) { result ->
            releaseInFlight = false
            releaseError = result.exceptionOrNull()
            if (result.isSuccess) calibration?.let { config ->
                fingerX = config.region.exactCenterX(); fingerY = bottomFinger(config)
            }
            finishReleaseCallbacks()
        }
    }
    private fun finishReleaseCallbacks() {
        val callbacks = releaseCallbacks.toList()
        releaseCallbacks.clear()
        callbacks.forEach { it() }
    }
    private fun bottomFinger(config: SavedCalibration) = config.region.bottom - max(12f, config.rowHeightPx * .25f)

    companion object {
        private const val SAMPLE_WIDTH = 96
        private const val END_HINT = "Sprawdź dół listy. Jeśli to koniec, wybierz Zakończ listę."

        /** Compatibility entry point. A screenshot cannot prove that a blind reverse swipe reached the top. */
        @Deprecated("Validate the saved top-row checkpoint or ask the user to move to the top manually.")
        fun seekTop(session: GestureSession, calibration: SavedCalibration, width: Int, height: Int,
                    isActive: () -> Boolean, complete: (Result<Unit>) -> Unit): () -> Unit {
            val handler = Handler(Looper.getMainLooper())
            var cancelled = false
            handler.post {
                if (!cancelled && isActive()) complete(Result.failure(IllegalStateException(
                    "Przewiń listę ręcznie na samą górę i ponów skan. Automat nie przewija w przeciwną stronę.")))
            }
            return { cancelled = true; handler.removeCallbacksAndMessages(null) }
        }
    }
}

/** Scanning always moves the finger UP, exposing later rows below; reverse seeking is not implicit. */
internal object AutoInventoryScrollPolicy {
    fun viewportChanged(previous: GrayScrollFrame, current: GrayScrollFrame): Boolean {
        require(previous.width == current.width && previous.height == current.height)
        var difference = 0L
        var samples = 0
        // Ignore modal borders. Identical egg rows remain distinguishable after
        // the deliberately fractional-row phase; no image-to-row matching here.
        val inset = max(2, current.width / 25)
        for (y in 2 until current.height - 2 step 2) {
            for (x in inset until current.width - inset step 2) {
                difference += abs(previous.pixel(x, y) - current.pixel(x, y))
                samples++
            }
        }
        return samples > 0 && difference.toDouble() / samples > 1.5
    }

    fun upwardDestination(fromY: Float, distance: Float): Float {
        require(fromY.isFinite() && distance.isFinite() && distance > 0f)
        return fromY - distance
    }
}

/**
 * Evidence for a list that fits on one viewport, before any gesture is injected.
 * Modal side borders are not inventory content. A stable image alone is insufficient:
 * require observed occupied rows followed by a substantial empty interior footer.
 */
internal class ShortViewportInventoryDetector {
    private var previous: GrayScrollFrame? = null
    private var stableSince = -1L
    private var stableSamples = 0
    private var previousCount: Long? = null
    var fullViewportVisible = false
        private set

    fun observe(frame: GrayScrollFrame, rowHeight: Int, columns: Int, nowMillis: Long): Long? {
        require(rowHeight >= 8 && columns in 1..12 && frame.width >= columns * 4)
        val edge = max(2, (frame.width * .035).roundToInt())
        val from = edge.coerceAtMost(frame.width / 3)
        val until = (frame.width - edge).coerceAtLeast(from + 1)
        val minimumDark = max(2, (until - from) / 70)
        var firstContent = -1
        var lastContent = -1
        for (y in 0 until frame.height) {
            var dark = 0
            for (x in from until until) if (frame.pixel(x, y) < 215) dark++
            if (dark >= minimumDark) { if (firstContent < 0) firstContent = y; lastContent = y }
        }
        val footer = frame.height - lastContent - 1
        val cells = InventoryRowAccumulator(columns, rowHeight)
        cells.observe(frame, 0.0)
        fullViewportVisible = cells.count > 0 && lastContent >= frame.height - rowHeight * .55
        val candidate = cells.count > 0 && cells.lastOccupiedRow >= 0 && cells.missingRowsBeforeLastOccupied() == 0 &&
            firstContent in 0..(rowHeight * .65).roundToInt() &&
            lastContent >= rowHeight * .35 && footer >= rowHeight * .70
        val old = previous
        previous = frame
        if (!candidate) {
            stableSince = -1; stableSamples = 0; previousCount = null; return null
        }
        var difference = 0L
        var samples = 0
        if (old != null && old.width == frame.width && old.height == frame.height) {
            for (y in 0 until frame.height step 2) for (x in from until until step 2) {
                difference += abs(old.pixel(x, y) - frame.pixel(x, y)); samples++
            }
        }
        val stable = samples > 0 && difference.toDouble() / samples <= 1.5 && previousCount == cells.count
        previousCount = cells.count
        if (!stable || stableSince < 0 || nowMillis < stableSince) {
            stableSince = nowMillis; stableSamples = 1
        } else stableSamples++
        return if (stableSamples >= 4 && nowMillis - stableSince >= 900) cells.count else null
    }
}
