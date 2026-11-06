package eu.komars.forgescanner.ui

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import eu.komars.forgescanner.model.PixelRegion
import eu.komars.forgescanner.model.TouchShieldGeometry

/** Blocks accidental touches outside STOP and a briefly opened injected-gesture corridor.
 * Android system bars remain accessible. Physical and injected touches inside the corridor
 * cannot be distinguished, so this is intentionally not a device-wide touch lock.
 */
class AutomationTouchShield(private val context: Context, private val onBlockedTouch: (() -> Unit)? = null) {
    private val manager = context.getSystemService(WindowManager::class.java)
    private var windows = emptyList<View>()
    private var display = Rect()
    private var panel: Rect? = null
    private var corridor: Rect? = null
    private var pinnedCorridor: Rect? = null
    private var windowsChangedAt = 0L

    val isShowing: Boolean get() = windows.isNotEmpty()

    fun show(panelBounds: Rect): Boolean {
        if (!mainThread()) return false
        val bounds = screenBounds()
        if (bounds.isEmpty || panelBounds.isEmpty || !bounds.contains(panelBounds)) return false
        if (windows.isNotEmpty() && bounds == display && panelBounds == panel) return true
        val previousDisplay = Rect(display); val previousPanel = panel?.let(::Rect)
        display = bounds; panel = Rect(panelBounds)
        if (rebuild()) return true
        display = previousDisplay; panel = previousPanel
        return false
    }

    /** Keep only the narrow drag strip open for a complete list; no window churn between strokes. */
    fun pinScrollCorridor(rect: Rect): Boolean {
        if (!openGesture(rect)) return false
        pinnedCorridor = corridor?.let(::Rect)
        return pinnedCorridor != null
    }

    fun releaseScrollCorridor() {
        pinnedCorridor = null
        closeGesture()
    }

    fun inputSettleMillis(): Long = (64L - (android.os.SystemClock.uptimeMillis() - windowsChangedAt)).coerceIn(0, 64)

    fun openGesture(rect: Rect): Boolean {
        if (!mainThread() || panel == null || rect.isEmpty) return false
        pinnedCorridor?.let { return it.contains(rect) }
        fun pixels(value: Rect) = PixelRegion(value.left, value.top, value.right, value.bottom)
        val merged = TouchShieldGeometry.gestureCorridor(pixels(display), pixels(requireNotNull(panel)),
            corridor?.let(::pixels), pixels(rect)) ?: return false
        val clipped = Rect(merged.left, merged.top, merged.right, merged.bottom)
        if (clipped == corridor) return true
        val previous = corridor?.let(::Rect); corridor = clipped
        if (rebuild()) return true
        corridor = previous
        return false
    }

    fun closeGesture() {
        if (!mainThread() || corridor == null || pinnedCorridor != null) return
        corridor = null
        if (panel != null && !rebuild()) {
            // A failed rebuild must stop the owning run; keep existing shields until hide().
            runCatching { onBlockedTouch?.invoke() }
        }
    }

    fun hide() {
        if (!mainThread()) return
        val previous = windows; windows = emptyList(); panel = null; corridor = null; pinnedCorridor = null
        previous.forEach { view -> runCatching { manager.removeViewImmediate(view) } }
    }

    private fun rebuild(): Boolean {
        val stop = panel ?: return false
        val holes = listOfNotNull(stop, corridor).map { PixelRegion(it.left, it.top, it.right, it.bottom) }
        val pieces = TouchShieldGeometry.partition(PixelRegion(display.left, display.top, display.right, display.bottom), holes)
        if (pieces.isEmpty()) return false
        val next = mutableListOf<View>()
        try {
            for (part in pieces) {
                val view = View(context).apply {
                    setBackgroundColor(Color.TRANSPARENT)
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                    isClickable = true
                    setOnTouchListener { _, event ->
                        if (event.actionMasked == MotionEvent.ACTION_DOWN) runCatching { onBlockedTouch?.invoke() }
                        true
                    }
                }
                val params = WindowManager.LayoutParams(part.width, part.height,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT).apply {
                    gravity = Gravity.TOP or Gravity.START
                    x = part.left; y = part.top
                    if (Build.VERSION.SDK_INT >= 30) { setFitInsetsTypes(0); layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS }
                    else if (Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
                manager.addView(view, params); next += view
            }
        } catch (_: Exception) {
            next.forEach { view -> runCatching { manager.removeViewImmediate(view) } }
            return false
        }
        // Add protection first, then retire old windows. STOP is excluded from both sets.
        val previous = windows; windows = next
        previous.forEach { view -> runCatching { manager.removeViewImmediate(view) } }
        windowsChangedAt = android.os.SystemClock.uptimeMillis()
        return true
    }

    private fun screenBounds(): Rect = if (Build.VERSION.SDK_INT >= 30) Rect(manager.maximumWindowMetrics.bounds) else {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        manager.defaultDisplay.getRealMetrics(metrics)
        Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
    }
    private fun mainThread() = Looper.myLooper() == Looper.getMainLooper()
}
