package eu.komars.forgescanner.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.KeyguardManager
import android.graphics.Path
import android.os.PowerManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.util.Log
import eu.komars.forgescanner.BuildConfig
import kotlin.math.abs

/**
 * Opt-in gesture transport for a static, user-started scan script. It does not inspect
 * accessibility node contents, choose targets, start scans or perform game purchases.
 */
class GestureAccessService : AccessibilityService() {
    private val safety = GestureSafety()
    private var gameForeground = false
    private var lastWindowEventAt = -1L
    private var busy = false
    private var held: GestureDescription.StrokeDescription? = null
    private var endX = 0f
    private var endY = 0f
    private var cancelReason: String? = null
    private var pending: GestureCompletion? = null
    private var attached = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        attached = true; gameForeground = false; safety.revoke()
        debugWindow("service-connected; foreground=false")
        GestureConnection.attach(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val owner = event.packageName?.toString()
        val className = event.className?.toString().orEmpty()
        val keyboard = android.provider.Settings.Secure.getString(contentResolver, android.provider.Settings.Secure.DEFAULT_INPUT_METHOD)?.substringBefore('/')
        val decision = ForegroundWindowClassifier.classify(owner, className, packageName, GAME_PACKAGE, keyboard)
        val stale = event.eventTime < lastWindowEventAt
        debugWindow("owner=${owner?.take(160) ?: "<null>"} class=${className.take(160)} time=${event.eventTime} decision=${if (stale) "STALE" else decision.name} foreground=$gameForeground")
        if (stale || decision == ForegroundWindowClassifier.Decision.IGNORE) return
        lastWindowEventAt = event.eventTime
        gameForeground = decision == ForegroundWindowClassifier.Decision.GAME
        if (!gameForeground) cancelRun("Gra nie jest na pierwszym planie. Automatyczny skan zatrzymano.")
        GestureConnection.publish(this, gameForeground, if (gameForeground) null else "Otwórz Forge Master, aby rozpocząć automatyczny skan.")
    }

    private fun debugWindow(message: String) {
        if (BuildConfig.DEBUG) Log.d("ScannerGestureWindow", message)
    }

    internal fun arm(width: Int, height: Int): GestureSession {
        debugWindow("arm attached=$attached foreground=$gameForeground interactive=${interactive()} busy=$busy held=${held != null}")
        require(!busy && held == null) { "Poczekaj na zakończenie poprzedniego gestu." }
        checkGame()
        cancelReason = null
        return safety.arm(width, height, SystemClock.uptimeMillis())
    }

    internal fun tap(session: GestureSession, x: Float, y: Float, callback: GestureCompletion) {
        val validation = runCatching {
            checkGame(); safety.validate(session, SystemClock.uptimeMillis(), x, y)
            check(!busy && held == null && cancelReason == null) { "Najpierw zakończ przewijanie listy." }
        }
        if (validation.isFailure) { callback(Result.failure(validation.exceptionOrNull()!!)); return }
        val stroke = GestureDescription.StrokeDescription(Path().apply { moveTo(x, y) }, 0, 70)
        safety.actionStarted()
        dispatch(stroke, continuing = false, x = x, y = y, callback = callback, phase = "tap")
    }

    internal fun drag(session: GestureSession, fromX: Float, fromY: Float, toX: Float, toY: Float,
                      durationMs: Long, hold: Boolean, callback: GestureCompletion) {
        val validation = runCatching {
            checkGame(); safety.drag(session, fromX, fromY, toX, toY, durationMs, SystemClock.uptimeMillis())
            check(!busy && cancelReason == null) { "Poprzedni gest nie został jeszcze zakończony." }
            if (held != null) check(abs(fromX - endX) < 1f && abs(fromY - endY) < 1f) {
                "Kontynuacja przewijania musi zaczynać się w ostatnim punkcie dotyku."
            }
        }
        if (validation.isFailure) { callback(Result.failure(validation.exceptionOrNull()!!)); return }
        val path = Path().apply { moveTo(fromX, fromY); lineTo(toX, toY) }
        val stroke = held?.continueStroke(path, 0, durationMs, hold)
            ?: GestureDescription.StrokeDescription(path, 0, durationMs, hold)
        safety.actionStarted()
        dispatch(stroke, hold, toX, toY, callback, "move")
    }

    internal fun endHold(session: GestureSession, durationMs: Long = 180, callback: GestureCompletion) {
        val validation = runCatching {
            checkGame(); safety.finishHold(session, durationMs, SystemClock.uptimeMillis())
            check(!busy && cancelReason == null) { "Poczekaj na zakończenie bieżącego gestu." }
        }
        if (validation.isFailure) { callback(Result.failure(validation.exceptionOrNull()!!)); return }
        val stroke = held
        if (stroke == null) { callback(Result.success(Unit)); return }
        val finish = HeldStrokeRelease.create(stroke, endX, endY, durationMs)
        safety.actionStarted()
        dispatch(finish, false, endX, endY, callback, "release")
    }

    internal fun cancelRun(reason: String) {
        safety.revoke(); cancelReason = reason
        // dispatchGesture has no general cancel call. Do not synthesize a tap to cancel:
        // finish only the already-held finger, and never inject into a different app.
        if (!busy && held != null && gameForeground && interactive()) releaseHeld()
        else if (!gameForeground || !interactive()) held = null
    }

    private fun traceGesture(state:String,phase:String,x:Float,y:Float,duration:Long) {
        if(!BuildConfig.DEBUG)return
        val text="state=$state phase=$phase x=$x y=$y duration=$duration"
        Log.d("ScannerGestureFlow",text)
        runCatching {java.io.File(cacheDir,"gesture-last.txt").writeText(text)}
    }

    private fun dispatch(stroke: GestureDescription.StrokeDescription, continuing: Boolean,
                         x: Float, y: Float, callback: GestureCompletion, phase: String) {
        traceGesture("dispatch",phase,x,y,stroke.duration)
        busy = true; pending = callback
        val accepted = dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(),
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription) {
                    traceGesture("completed",phase,x,y,stroke.duration)
                    busy = false; held = if (continuing) stroke else null; endX = x; endY = y
                    val completion = pending; pending = null
                    val reason = cancelReason
                    if (reason != null) {
                        if (held != null && gameForeground && interactive()) releaseHeld()
                        else if (!gameForeground || !interactive()) held = null
                        completion?.invoke(Result.failure(IllegalStateException(reason)))
                    } else completion?.invoke(Result.success(Unit))
                }
                override fun onCancelled(gestureDescription: GestureDescription) {
                    traceGesture("cancelled",phase,x,y,stroke.duration)
                    busy = false; held = null; safety.revoke()
                    val completion = pending; pending = null
                    cancelReason = "Gest został przerwany. Automatyczny skan zatrzymano."
                    completion?.invoke(Result.failure(IllegalStateException(cancelReason)))
                }
            }, null)
        if (!accepted) {
            busy = false; held = null; safety.revoke(); pending = null
            callback(Result.failure(IllegalStateException("Android nie przyjął gestu. Sprawdź usługę dostępności.")))
        }
    }

    private fun releaseHeld() {
        val stroke = held ?: return
        held = null
        runCatching {
            val finish = stroke.continueStroke(Path().apply { moveTo(endX, endY) }, 0, 180, false)
            // Release keeps no callback capable of advancing the cancelled script.
            busy = true
            val accepted = dispatchGesture(GestureDescription.Builder().addStroke(finish).build(),
                object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription) { busy = false }
                    override fun onCancelled(gestureDescription: GestureDescription) { busy = false }
                }, null)
            if (!accepted) busy = false
        }.onFailure { busy = false }
    }

    private fun checkGame() {
        check(attached && gameForeground && interactive()) {
            "Przywróć Forge Master na pierwszy plan. Nie trzeba restartować gry."
        }
    }
    private fun interactive(): Boolean = getSystemService(PowerManager::class.java).isInteractive &&
        !getSystemService(KeyguardManager::class.java).isKeyguardLocked

    override fun onInterrupt() { cancelRun("Android przerwał sterowanie gestami.") }
    override fun onDestroy() {
        cancelRun("Sterowanie gestami zostało wyłączone.")
        attached = false; gameForeground = false
        GestureConnection.detach(this)
        super.onDestroy()
    }

    companion object { const val GAME_PACKAGE = "com.hariwn.legendofcivilizations" }
}
