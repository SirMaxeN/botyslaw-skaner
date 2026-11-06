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
    private val titleText: String? = null,
    private val accentColor: Int = ScannerAppearance.ACCENT,
    private val instructionText: String? = null,
    private val initialSelection: Rect? = null,
    private val minimumSelectionSize: Int = 32,
    private val fields: Map<String, Pair<String, Rect?>> = emptyMap(),
    private val onFields: ((Map<String, Rect>) -> Unit)? = null
) {
    private val manager = context.getSystemService(WindowManager::class.java)
    private var root: LinearLayout? = null
    private var columns = if (grid) 5 else 1
    private var rowPercent = 100
    private var active = true
    private lateinit var selection: SelectionView

    fun show() {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(16, 23, 39))
        }
        if (android.os.Build.VERSION.SDK_INT >= 30) container.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsetsIgnoringVisibility(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        val title = TextView(context).apply {
            text = titleText ?: if (grid) "Zaznacz siatkę pozycji" else "Zaznacz licznik do odczytu"
            textSize = 18f
            setTextColor(accentColor)
            setPadding(dp(16), dp(12), dp(16), dp(4))
        }
        container.addView(title)
        container.addView(TextView(context).apply {
            text = instructionText ?: if (grid) "Zaznacz z grubsza obszar z ikonami. Sam dopasuję siatkę i odstępy między rzędami. Nie musisz trafiać idealnie w krawędzie."
            else "Przeciągnij palcem prostokąt wokół liczby. Zatwierdź, aby rozpoznać tylko ten fragment."
            textSize = 13f
            setTextColor(Color.LTGRAY)
            setPadding(dp(16), 0, dp(16), dp(8))
        })
        selection = SelectionView(context, bitmap)
        val middle = LinearLayout(context)
        middle.addView(selection, LinearLayout.LayoutParams(0, -1, 1f))
        if (fields.isNotEmpty()) {
            val choices = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            val scroll = android.widget.ScrollView(context).apply { addView(choices) }
            fields.entries.forEachIndexed { i, (key, value) ->
                choices.addView(FieldButton(context).apply {
                    text=value.first;contentDescription=value.first;isAllCaps=false;textSize=11f;gravity=Gravity.CENTER
                    setTextColor(colors[i % colors.size]);backgroundTintList=null
                    background=ScannerAppearance.rounded(context,ScannerAppearance.INPUT_BACKGROUND,8,colors[i % colors.size])
                    minHeight=dp(48);minimumHeight=dp(48);setPadding(dp(6),dp(8),dp(6),dp(8))
                    setOnClickListener {selection.activate(key);title.text="${titleText.orEmpty()}\n${value.first}"}
                }, LinearLayout.LayoutParams(-1, -2).apply {setMargins(dp(3),dp(3),dp(3),dp(3))})
            }
            middle.addView(scroll, LinearLayout.LayoutParams(dp(112), -1))
        }
        container.addView(middle, LinearLayout.LayoutParams(-1, 0, 1f))
        val actions = ScannerAppearance.actionRow(context)
        actions.addView(button("Anuluj") { if (active) { close(); onCancel() } }, ScannerAppearance.rowItem(context))
        actions.addView(button("Zatwierdź") {
            if (!active) return@button
            if (onFields != null) {
                val values = selection.allSelections()
                if (fields.keys.any { key -> values[key]?.let { it.width() >= minimumSelectionSize && it.height() >= minimumSelectionSize } != true }) {
                    title.text = "Zaznacz wszystkie pola z listy po prawej"; return@button
                }
                close(); onFields.invoke(values); return@button
            }
            val rect = selection.bitmapSelection()
            if (rect == null || rect.width() < max(minimumSelectionSize, columns * 8) || rect.height() < minimumSelectionSize) {
                title.text = "Zaznacz większy prostokąt palcem"
            } else {
                val rowHeight = max(8, rect.width() * rowPercent / columns / 100)
                close()
                onResult(rect, columns, rowHeight)
            }
        }, ScannerAppearance.rowItem(context))
        container.addView(actions)
        root = container
        manager.addView(container, WindowManager.LayoutParams(
            -1, -1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            if (android.os.Build.VERSION.SDK_INT >= 30) { setFitInsetsTypes(0); layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS }
            gravity = Gravity.TOP or Gravity.START
        })
    }

    fun close() {
        // removeView is deferred; a queued draw can still run after the owner recycles its frame.
        active = false
        root?.let { runCatching { manager.removeView(it) } }
        root = null
        // Owner releases bitmap after the result/cancel callback has finished using it.
    }

    private fun button(label: String, action: () -> Unit) = ScannerAppearance.overlayAction(context, label) { action() }.apply {
        textSize = 13f; minWidth = dp(52); minimumWidth = dp(52)
    }
    /** Draw the same wrapped text layout as the native button, with a dark contour first. */
    // The overlay uses the platform Theme.Material, like all adjacent native buttons.
    @android.annotation.SuppressLint("AppCompatCustomView")
    private class FieldButton(context:Context):Button(context) {
        private val original=Paint()
        override fun onDraw(canvas:Canvas) {
            layout?.let { lines ->
                val saved=canvas.save();original.set(paint)
                val room=height-extendedPaddingTop-extendedPaddingBottom
                canvas.translate(compoundPaddingLeft.toFloat(),(extendedPaddingTop+maxOf(0,(room-lines.height)/2)).toFloat())
                paint.color=0xFF02060D.toInt();paint.style=Paint.Style.STROKE
                paint.strokeWidth=resources.displayMetrics.density*2f;paint.strokeJoin=Paint.Join.ROUND
                lines.draw(canvas);paint.set(original);canvas.restoreToCount(saved)
            }
            super.onDraw(canvas)
        }
    }
    private val colors = intArrayOf(ScannerAppearance.ACCENT,0xFFFFD166.toInt(),0xFF66C7FF.toInt(),0xFFEF8DDF.toInt(),0xFFFF9278.toInt(),0xFFB5A0FF.toInt(),0xFFA5E478.toInt(),0xFFFFAEC9.toInt(),0xFF65E0DF.toInt())
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()

    private inner class SelectionView(context: Context, private val image: Bitmap) : View(context) {
        private val selections = linkedMapOf<String, Rect>()
        private var activeKey = fields.keys.firstOrNull()
        fun activate(key: String) { activeKey = key; selected = null; initialApplied = true; invalidate() }
        fun allSelections(): Map<String, Rect> = selections.mapValues { Rect(it.value) }
        private val destination = RectF()
        private var selected: RectF? = null
        private var initialApplied = false
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
            if (!initialApplied) {
                initialApplied = true
                fields.forEach { (key, value) -> value.second?.let { selections[key] = Rect(it) } }
                initialSelection?.let { original ->
                    val clipped = Rect(original)
                    if (clipped.intersect(Rect(0, 0, image.width, image.height))) {
                        selected = RectF(destination.left + clipped.left * scale, destination.top + clipped.top * scale,
                            destination.left + clipped.right * scale, destination.top + clipped.bottom * scale)
                    }
                }
            }
            fun drawRegion(rect:RectF,color:Int,chosen:Boolean) {
                paint.style=Paint.Style.FILL;paint.color=(color and 0x00FFFFFF) or 0x33000000
                canvas.drawRect(rect,paint)
                paint.style=Paint.Style.STROKE;paint.color=color;paint.strokeWidth=dp(if(chosen)3 else 1).toFloat()
                canvas.drawRect(rect,paint)
            }
            fields.keys.forEachIndexed {i,key ->selections[key]?.let {q ->
                if(key!=activeKey || selected==null)drawRegion(RectF(destination.left+q.left*scale,destination.top+q.top*scale,destination.left+q.right*scale,destination.top+q.bottom*scale),colors[i%colors.size],key==activeKey)
            }}
            selected?.let {rect ->
                val index=fields.keys.indexOf(activeKey)
                drawRegion(rect,if(index>=0)colors[index%colors.size]else accentColor,true)
            }

        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val x = event.x.coerceIn(destination.left, destination.right)
            val y = event.y.coerceIn(destination.top, destination.bottom)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { startX = x; startY = y; selected = RectF(x, y, x, y) }
                MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                    selected = RectF(min(startX, x), min(startY, y), max(startX, x), max(startY, y))
                    if (event.actionMasked == MotionEvent.ACTION_UP) {
                        activeKey?.let { key -> bitmapSelection()?.let { selections[key] = it } }; performClick()
                    }
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
