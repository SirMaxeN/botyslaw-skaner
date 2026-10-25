package eu.komars.forgescanner.ui

import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.LinearLayout
import kotlin.math.abs

/** A tap belongs to the child button; a drag anywhere cancels that tap and moves the panel. */
class DraggableOverlayLayout(context: Context, private val moveBy: (Float, Float) -> Unit) : LinearLayout(context) {
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var dragging = false

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            downX = event.rawX; downY = event.rawY; lastX = downX; lastY = downY; dragging = false
        } else if (event.actionMasked == MotionEvent.ACTION_MOVE && !dragging &&
            (abs(event.rawX - downX) > slop || abs(event.rawY - downY) > slop)) {
            dragging = true
            // The first intercepted MOVE cancels the child; it may not reach onTouchEvent.
            moveBy(event.rawX - lastX, event.rawY - lastY)
            lastX = event.rawX; lastY = event.rawY
        }
        return dragging
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                if (!dragging && (abs(event.rawX - downX) > slop || abs(event.rawY - downY) > slop)) dragging = true
                if (dragging) { moveBy(event.rawX - lastX, event.rawY - lastY); lastX = event.rawX; lastY = event.rawY }
            }
            MotionEvent.ACTION_UP -> { if (!dragging) performClick(); dragging = false }
            MotionEvent.ACTION_CANCEL -> dragging = false
        }
        return true
    }
    override fun performClick(): Boolean = super.performClick()
}
