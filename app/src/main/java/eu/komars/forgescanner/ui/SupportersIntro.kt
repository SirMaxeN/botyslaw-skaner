package eu.komars.forgescanner.ui

import android.animation.ValueAnimator
import android.app.Activity
import android.graphics.Typeface
import android.os.Build
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import eu.komars.forgescanner.sync.SupportersClient
import kotlin.math.ceil
import eu.komars.forgescanner.ui.ScannerAppearance as S

/**
 * Short thank-you screen over the home screen at app start: title, the supporters' names in as many columns as fit,
 * a slow automatic scroll through the whole list, then a fade to the home screen. A tap anywhere (or Back) skips it.
 * Added to the window decor, so the home screen may rebuild its content underneath without removing it.
 */
object SupportersIntro {
    private const val TAG = "supporters-intro"
    private val HEART = S.STEP_COLORS[5]

    /**
     * [full]: the first start or the first after an update / a list change: everything lasts [SupportersLayout.FULL]
     * times longer. [onClosed] runs once when the intro closes, by itself or skipped (it still counts as seen).
     */
    fun show(activity: Activity, supporters: SupportersClient.Supporters, full: Boolean = false, onClosed: () -> Unit = {}) {
        if (supporters.names.isEmpty()) return
        val decor = activity.window.decorView as ViewGroup
        if (decor.findViewWithTag<View>(TAG) != null) return
        fun dp(value: Int) = S.dp(activity, value)
        val density = activity.resources.displayMetrics.density
        val animators = mutableListOf<ValueAnimator>()
        var closing = false
        val root = FrameLayout(activity).apply { tag = TAG; setBackgroundColor(S.BACKGROUND); isClickable = true }

        fun dismiss() {
            if (closing) return
            closing = true
            onClosed()
            animators.forEach { it.cancel() }
            root.animate().alpha(0f).setDuration(320).withEndAction { decor.removeView(root) }.start()
        }
        root.setOnClickListener { dismiss() }

        val column = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        root.addView(column, FrameLayout.LayoutParams(-1, -1))
        fun pad(left: Int, top: Int, right: Int, bottom: Int) = column.setPadding(dp(20) + left, dp(36) + top, dp(20) + right, dp(20) + bottom)
        if (Build.VERSION.SDK_INT >= 30) root.setOnApplyWindowInsetsListener { _, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            pad(bars.left, bars.top, bars.right, bars.bottom); insets
        } else { pad(0, dp(24), 0, dp(16)) }

        val heart = TextView(activity).apply {
            text = "❤"; textSize = 44f; setTextColor(HEART); gravity = Gravity.CENTER; scaleX = 0f; scaleY = 0f
        }
        column.addView(heart, LinearLayout.LayoutParams(-2, -2))
        val titleText = supporters.title.replace("❤", "").replace("<3", "").trim()
        val title = TextView(activity).apply {
            text = titleText; textSize = 24f; setTextColor(S.FOREGROUND); setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER; alpha = 0f; translationY = dp(12).toFloat()
        }
        column.addView(title, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        val subtitle = TextView(activity).apply {
            text = SpannableString("Dzięki Wam Botysław może dalej działać").apply {
                setSpan(ForegroundColorSpan(S.MUTED), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            textSize = 13f; gravity = Gravity.CENTER; alpha = 0f
        }
        column.addView(subtitle, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4); bottomMargin = dp(18) })

        val scroll = object : ScrollView(activity) {
            // Touching the list skips the intro like any other tap; it is not a list to browse.
            override fun onInterceptTouchEvent(event: MotionEvent) = true
            override fun onTouchEvent(event: MotionEvent): Boolean {
                if (event.actionMasked == MotionEvent.ACTION_UP) dismiss()
                return true
            }
        }.apply {
            isVerticalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalFadingEdgeEnabled = true; setFadingEdgeLength(dp(36))
        }
        column.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val grid = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(8), 0, dp(8)) }
        scroll.addView(grid, FrameLayout.LayoutParams(-1, -2))

        val widthDp = (activity.resources.displayMetrics.widthPixels / density).toInt()
        val columns = SupportersLayout.columns(supporters.names.size, widthDp)
        val chips = mutableListOf<View>()
        supporters.names.chunked(columns).forEachIndexed { rowIndex, rowNames ->
            val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_HORIZONTAL }
            rowNames.forEachIndexed { index, name ->
                val color = S.stepColor((rowIndex * columns + index) % S.STEP_COLORS.size)
                val chip = TextView(activity).apply {
                    text = name; textSize = 15f; setTextColor(S.FOREGROUND); setTypeface(typeface, Typeface.BOLD)
                    gravity = Gravity.CENTER; maxLines = 1; ellipsize = TextUtils.TruncateAt.END
                    setPadding(dp(10), 0, dp(10), 0)
                    background = S.rounded(activity, S.INPUT_BACKGROUND, 12, color)
                    alpha = 0f; translationY = dp(10).toFloat()
                }
                val width = if (columns == 1) dp(240) else 0
                row.addView(chip, LinearLayout.LayoutParams(width, dp(44), if (columns == 1) 0f else 1f).apply {
                    setMargins(dp(4), dp(4), dp(4), dp(4))
                })
                chips += chip
            }
            // Keep the last, shorter row's chips the same width as the others.
            if (columns > 1) repeat(columns - rowNames.size) { row.addView(View(activity), LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }) }
            grid.addView(row, LinearLayout.LayoutParams(-1, -2))
        }

        val hint = TextView(activity).apply {
            text = "Tapnij, żeby kontynuować"; textSize = 13f; setTextColor(S.MUTED); gravity = Gravity.CENTER; alpha = 0f
        }
        column.addView(hint, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })

        decor.addView(root, FrameLayout.LayoutParams(-1, -1))
        if (!ValueAnimator.areAnimatorsEnabled()) {
            // Animations off in system settings: a still screen that closes by itself.
            listOf(heart, title, subtitle, hint).forEach { it.alpha = 1f; it.scaleX = 1f; it.scaleY = 1f; it.translationY = 0f }
            chips.forEach { it.alpha = 1f; it.translationY = 0f }
            root.postDelayed({ dismiss() }, if (full) 3000L * SupportersLayout.FULL else 3000L)
            return
        }

        heart.animate().scaleX(1f).scaleY(1f).setDuration(420).setInterpolator(OvershootInterpolator(2.4f)).start()
        title.animate().alpha(1f).translationY(0f).setStartDelay(120).setDuration(360).setInterpolator(DecelerateInterpolator()).start()
        subtitle.animate().alpha(1f).setStartDelay(260).setDuration(360).start()
        chips.forEachIndexed { index, chip ->
            chip.animate().alpha(1f).translationY(0f).setStartDelay(300L + minOf(index, 24) * 28L).setDuration(300)
                .setInterpolator(DecelerateInterpolator()).start()
        }
        hint.animate().alpha(1f).setStartDelay(700).setDuration(400).withEndAction {
            if (closing) return@withEndAction
            ValueAnimator.ofFloat(1f, .45f).apply {
                duration = 900; repeatMode = ValueAnimator.REVERSE; repeatCount = ValueAnimator.INFINITE
                addUpdateListener { hint.alpha = it.animatedValue as Float }
                animators += this; start()
            }
        }.start()
        // Heartbeat: a small double pulse every ~1.4 s.
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1400; startDelay = 600; repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                val t = it.animatedFraction
                val beat = when { t < .12f -> t / .12f; t < .24f -> 1 - (t - .12f) / .12f; t < .36f -> (t - .24f) / .12f * .6f; t < .5f -> .6f * (1 - (t - .36f) / .14f); else -> 0f }
                heart.scaleX = 1f + beat * .14f; heart.scaleY = heart.scaleX
            }
            animators += this; start()
        }

        scroll.post {
            if (closing) return@post
            val distance = (grid.height - scroll.height).coerceAtLeast(0)
            if (distance == 0) { root.postDelayed({ dismiss() }, if (full) 2600L * SupportersLayout.FULL else 2600L); return@post }
            ValueAnimator.ofInt(0, distance).apply {
                duration = SupportersLayout.scrollMillis(distance / density, full); startDelay = if (full) 2200 else 1100; interpolator = LinearInterpolator()
                addUpdateListener { scroll.scrollTo(0, it.animatedValue as Int) }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    private var cancelled = false
                    override fun onAnimationCancel(animation: android.animation.Animator) { cancelled = true }
                    override fun onAnimationEnd(animation: android.animation.Animator) { if (!cancelled) root.postDelayed({ dismiss() }, if (full) 900L * SupportersLayout.FULL else 900L) }
                })
                animators += this; start()
            }
        }
    }

    /** Back while the intro shows: close it instead of leaving the app. */
    fun dismissIfShowing(activity: Activity): Boolean {
        val view = (activity.window.decorView as ViewGroup).findViewWithTag<View>(TAG) ?: return false
        view.performClick(); return true
    }
}

/** Pure sizing rules of the intro (unit-tested without Android). */
object SupportersLayout {
    /** Columns for [count] names on a screen [widthDp] wide: one column for a handful, more as the list grows. */
    fun columns(count: Int, widthDp: Int): Int {
        val fit = ((widthDp - 40) / 118).coerceIn(1, 4)
        return ceil(count / 7.0).toInt().coerceIn(1, fit)
    }

    /** Auto-scroll time: unhurried for short lists, capped so a long list still passes quickly. */
    fun scrollMillis(distanceDp: Float, full: Boolean = false): Long =
        (distanceDp / 110f * 1000f).toLong().coerceIn(1200L, 9000L) * (if (full) FULL else 1)

    /** How many times longer the full intro (first start, after an update or a list change) lasts. */
    const val FULL = 3
}
