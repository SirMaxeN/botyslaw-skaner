package eu.komars.forgescanner.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.max
import kotlin.math.min

/** A temporary in-memory screenshot. No screenshot or calibration is written to storage. */
class ScreenSelectionWindow(
    private val context: Context,
    private val bitmap: Bitmap,
    private val grid: Boolean,
    private val onResult: (Rect, Int, Int) -> Unit,
    private val onCancel: () -> Unit,
    private val titleText: String? = null
) {
    private val manager = context.getSystemService(WindowManager::class.java)
    private var root: LinearLayout? = null
    private var columns = 5
    private var rowPercent = 100
    private var active = true
    private lateinit var selection: SelectionView

    fun show() {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(16, 23, 39))
        }
        val title = TextView(context).apply {
            text = titleText ?: if (grid) "Zaznacz siatkę pozycji" else "Zaznacz licznik do odczytu"
            textSize = 18f
            setTextColor(Color.WHITE)
            setPadding(dp(16), dp(12), dp(16), dp(4))
        }
        container.addView(title)
        container.addView(TextView(context).apply {
            text = if (grid) "Zaznacz z grubsza obszar z ikonami. Sam dopasuję siatkę i odstępy między rzędami. Nie musisz trafiać idealnie w krawędzie."
            else "Przeciągnij palcem prostokąt wokół liczby. Zatwierdź, aby rozpoznać tylko ten fragment."
            textSize = 13f
            setTextColor(Color.LTGRAY)
            setPadding(dp(16), 0, dp(16), dp(8))
        })
        selection = SelectionView(context, bitmap)
        container.addView(selection, LinearLayout.LayoutParams(-1, 0, 1f))
        val actions = LinearLayout(context)
        actions.addView(button("Anuluj") { close(); onCancel() }, LinearLayout.LayoutParams(0, -2, 1f))
        actions.addView(button("Zatwierdź") {
            val rect = selection.bitmapSelection()
            if (rect == null || rect.width() < max(32, columns * 8) || rect.height() < 32) {
                title.text = "Zaznacz większy prostokąt palcem"
            } else {
                val rowHeight = max(8, rect.width() * rowPercent / columns / 100)
                close()
                onResult(rect, columns, rowHeight)
            }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        container.addView(actions)
        root = container
        manager.addView(container, WindowManager.LayoutParams(
            -1, -1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START })
    }

    fun close() {
        // removeView is deferred; a queued draw can still run after the owner recycles its frame.
        active = false
        root?.let { runCatching { manager.removeView(it) } }
        root = null
        // Owner releases bitmap after the result/cancel callback has finished using it.
    }

    private fun button(label: String, action: () -> Unit) = Button(context).apply {
        text = label; textSize = 12f; isAllCaps = false
        minWidth = dp(52); minimumWidth = dp(52)
        setOnClickListener { action() }
    }
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()

    private inner class SelectionView(context: Context, private val image: Bitmap) : View(context) {
        private val destination = RectF()
        private var selected: RectF? = null
        private var startX = 0f
        private var startY = 0f
        var columns = if (grid) 5 else 1
        var rowPercent = 100
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (!active || image.isRecycled) return
            val scale = min(width.toFloat() / image.width, height.toFloat() / image.height)
            val w = image.width * scale; val h = image.height * scale
            destination.set((width - w) / 2, (height - h) / 2, (width + w) / 2, (height + h) / 2)
            paint.color = Color.WHITE; paint.style = Paint.Style.FILL; paint.alpha = 255
            canvas.drawBitmap(image, null, destination, paint)
            val rect = selected ?: return
            paint.color = 0x4461D6AC; canvas.drawRect(rect, paint)
            paint.color = 0xFF61D6AC.toInt(); paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(2).toFloat()
            canvas.drawRect(rect, paint)

        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val x = event.x.coerceIn(destination.left, destination.right)
            val y = event.y.coerceIn(destination.top, destination.bottom)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { startX = x; startY = y; selected = RectF(x, y, x, y) }
                MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                    selected = RectF(min(startX, x), min(startY, y), max(startX, x), max(startY, y))
                    if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
                }
                else -> return false
            }
            invalidate()
            return true
        }

        override fun performClick(): Boolean { super.performClick(); return true }

        fun bitmapSelection(): Rect? = selected?.let { rect ->
            if (destination.width() <= 0 || destination.height() <= 0) return null
            val scale = image.width / destination.width()
            Rect(
                ((rect.left - destination.left) * scale).toInt().coerceIn(0, image.width - 1),
                ((rect.top - destination.top) * scale).toInt().coerceIn(0, image.height - 1),
                ((rect.right - destination.left) * scale).toInt().coerceIn(1, image.width),
                ((rect.bottom - destination.top) * scale).toInt().coerceIn(1, image.height)
            )
        }
    }
}
