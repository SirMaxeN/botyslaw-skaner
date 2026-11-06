package eu.komars.forgescanner.automation

import android.content.Intent
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.provider.Settings

typealias GestureCompletion = (Result<Unit>) -> Unit
typealias GestureStateListener = (connected: Boolean, foregroundGame: Boolean, detail: String?) -> Unit

/** In-process bridge. Enabling the service never arms or starts a scan by itself. */
object GestureConnection {
    private val handler = Handler(Looper.getMainLooper())
    private var service: GestureAccessService? = null
    private val listeners = linkedSetOf<GestureStateListener>()
    private var inFlight = false
    private var heldCorridor = false
    private var shieldGeneration = 0L
    /** Optional UI-owned shield hooks. Absence preserves existing manual gesture behavior. */
    var beforeGesture: ((Rect) -> Boolean)? = null
    var afterGesture: (() -> Unit)? = null
    var inputSettleMillis: (() -> Long)? = null
    @Volatile var isConnected = false
        private set
    @Volatile var foregroundGame = false
        private set
    @Volatile var detail: String? = null
        private set

    fun settingsIntent() = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun addStateListener(listener: GestureStateListener) = onMain {
        listeners += listener; listener(isConnected, foregroundGame, detail)
    }
    fun removeStateListener(listener: GestureStateListener) = onMain { listeners -= listener }

    fun requestArm(width: Int, height: Int, callback: (Result<GestureSession>) -> Unit) = onMain {
        val current = service
        if (current == null) callback(Result.failure(IllegalStateException("Włącz sterowanie gestami w ustawieniach dostępności, a potem ponownie otwórz grę.")))
        else callback(runCatching { current.arm(width, height) })
    }

    fun tap(session: GestureSession, x: Float, y: Float, callback: GestureCompletion) = onMain {
        if (heldCorridor) callback(Result.failure(IllegalStateException("Najpierw zakończ przewijanie listy.")))
        else gesture(session, floatArrayOf(x, y), padding = 32, hold = false, callback) { current, completion ->
            current.tap(session, x, y, completion)
        }
    }

    /** hold=true leaves the same virtual finger down between fresh-frame observations. */
    fun drag(session: GestureSession, fromX: Float, fromY: Float, toX: Float, toY: Float,
             durationMs: Long, hold: Boolean = false, callback: GestureCompletion) = onMain {
        gesture(session, floatArrayOf(fromX, fromY, toX, toY), padding = 20, hold = hold, callback) { current, completion ->
            current.drag(session, fromX, fromY, toX, toY, durationMs, hold, completion)
        }
    }

    /** End a continued drag with a short stationary hold rather than a fling. */
    fun endHold(session: GestureSession, durationMs: Long = 180, callback: GestureCompletion) = onMain {
        connected(callback) { current ->
            if (inFlight) callback(Result.failure(IllegalStateException("Poczekaj na zakończenie bieżącego gestu.")))
            else dispatchShielded(hold = false, callback) { completion -> current.endHold(session, durationMs, completion) }
        }
    }

    fun cancel(reason: String = "Automatyczny skan został zatrzymany.") = onMain {
        val restore = afterGesture
        runCatching { service?.cancelRun(reason) }
        if (!inFlight) {
            val generation = shieldGeneration
            if (heldCorridor) {
                heldCorridor = false
                // The service releases an already-held finger using a 180 ms stationary stroke.
                handler.postDelayed({
                    if (generation == shieldGeneration && !inFlight) runCatching { restore?.invoke() }
                }, 220)
            } else runCatching { restore?.invoke() }
        }
    }

    internal fun attach(owner: GestureAccessService) = onMain {
        service = owner; isConnected = true; foregroundGame = false
        detail = "Ponownie otwórz grę, aby potwierdzić ekran automatycznego skanu."; notifyListeners()
    }
    internal fun publish(owner: GestureAccessService, foreground: Boolean, message: String?) = onMain {
        if (service !== owner) return@onMain
        foregroundGame = foreground; detail = message; notifyListeners()
    }
    internal fun detach(owner: GestureAccessService) = onMain {
        if (service !== owner) return@onMain
        service = null; isConnected = false; foregroundGame = false
        inFlight = false; heldCorridor = false; shieldGeneration++
        runCatching { afterGesture?.invoke() }
        detail = "Sterowanie gestami zostało wyłączone."; notifyListeners()
    }

    private fun connected(callback: GestureCompletion, action: (GestureAccessService) -> Unit) {
        val current = service
        if (current == null) callback(Result.failure(IllegalStateException("Sterowanie gestami jest wyłączone.")))
        else action(current)
    }
    private fun gesture(session: GestureSession, coordinates: FloatArray, padding: Int, hold: Boolean,
        callback: GestureCompletion, action: (GestureAccessService, GestureCompletion) -> Unit) {
        connected(callback) { current ->
            if (inFlight) { callback(Result.failure(IllegalStateException("Poczekaj na zakończenie bieżącego gestu."))); return@connected }
            if (coordinates.isEmpty() || coordinates.size % 2 != 0 || !coordinates.all(Float::isFinite)) {
                callback(Result.failure(IllegalArgumentException("Nieprawidłowy punkt gestu."))); return@connected
            }
            val points = coordinates.toList().chunked(2)
            if (points.any { (x, y) -> x < 1 || x >= session.width - 1 || y < 1 || y >= session.height - 1 }) {
                callback(Result.failure(IllegalArgumentException("Punkt gestu wychodzi poza ekran."))); return@connected
            }
            val corridor = Rect(
                (kotlin.math.floor(points.minOf { it[0] }.toDouble()).toInt() - padding).coerceAtLeast(0),
                (kotlin.math.floor(points.minOf { it[1] }.toDouble()).toInt() - padding).coerceAtLeast(0),
                (kotlin.math.ceil(points.maxOf { it[0] }.toDouble()).toInt() + padding + 1).coerceAtMost(session.width),
                (kotlin.math.ceil(points.maxOf { it[1] }.toDouble()).toInt() + padding + 1).coerceAtMost(session.height))
            val permitted = runCatching { beforeGesture?.invoke(corridor) ?: true }.getOrDefault(false)
            if (!permitted) {
                callback(Result.failure(IllegalStateException("Nie można bezpiecznie otworzyć obszaru gestu. Skan zatrzymano.")))
                return@connected
            }
            val settleDelay = if (beforeGesture != null && !heldCorridor)
                (inputSettleMillis?.invoke() ?: 64L).coerceIn(0, 64) else 0L
            dispatchShielded(hold, callback) { completion ->
                // WindowManager commits new touchable regions asynchronously. A
                // fresh DOWN sent immediately after rebuilding the shield can hit
                // its old window instead of the game, despite a successful gesture.
                // Continuing the same held pointer does not need a new hit test.
                if (settleDelay > 0L) handler.postDelayed({
                    try { action(current, completion) }
                    catch (error: Exception) { completion(Result.failure(error)) }
                }, settleDelay)
                else action(current, completion)
            }
        }
    }
    private fun dispatchShielded(hold: Boolean, callback: GestureCompletion,
        action: (GestureCompletion) -> Unit) {
        val wasHeld = heldCorridor
        inFlight = true; shieldGeneration++
        val generation = shieldGeneration
        val restore = afterGesture
        var completed = false
        val completion: GestureCompletion = completion@{ result ->
            if (completed) return@completion
            completed = true
            if (generation == shieldGeneration) {
                inFlight = false; heldCorridor = hold && result.isSuccess
                if (!heldCorridor) {
                    if (result.isFailure && (wasHeld || hold)) {
                        // Cancellation can still have the service's 180 ms held-finger release in flight.
                        handler.postDelayed({
                            if (generation == shieldGeneration && !inFlight) runCatching { restore?.invoke() }
                        }, 220)
                    } else runCatching { restore?.invoke() }
                }
            }
            callback(result)
        }
        try { action(completion) }
        catch (error: Exception) { if (!completed) completion(Result.failure(error)) }
    }
    private fun notifyListeners() { listeners.toList().forEach { runCatching { it(isConnected, foregroundGame, detail) } } }
    private inline fun onMain(crossinline action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else handler.post { action() }
    }
}
