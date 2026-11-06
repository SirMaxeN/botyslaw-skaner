package eu.komars.forgescanner.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.Button
import android.widget.TextView

/**
 * The single visual system of Botysław: app screens, setup, resource/battle/war/ranking overlays,
 * minimized bars and dialogs. Screens must not define their own colors, button looks or step palettes.
 */
object ScannerAppearance {
    val BACKGROUND=Color.rgb(11,17,29)
    val INPUT_BACKGROUND=Color.rgb(18,28,45)
    val BORDER=Color.rgb(43,58,80)
    val FOREGROUND=Color.rgb(237,242,251)
    val MUTED=Color.rgb(158,176,200)
    val ACCENT=Color.rgb(114,216,181)
    val PRIMARY=Color.rgb(26,107,92)
    /** Floating overlay panel and minimized icon bar. */
    val PANEL=Color.rgb(16,33,43)
    /** Secondary control surface inside overlays. */
    val CONTROL=Color.rgb(36,54,75)
    val ON_ACCENT=Color.rgb(16,42,38)
    val WARNING=Color.rgb(255,219,118)
    val DANGER=Color.rgb(255,112,112)
    val DANGER_SURFACE=Color.rgb(62,38,53)
    val DANGER_TEXT=Color.rgb(255,168,175)
    val SUCCESS=Color.rgb(112,221,171)
    /** One step palette for every automatic/setup flow, so step N has the same color everywhere. */
    val STEP_COLORS=intArrayOf(
        Color.rgb(255,207,102),Color.rgb(103,216,244),Color.rgb(132,175,255),Color.rgb(189,160,255),
        Color.rgb(92,225,189),Color.rgb(255,145,166),Color.rgb(255,170,103),Color.rgb(150,157,255),
        Color.rgb(155,225,113),Color.rgb(230,209,106),Color.rgb(255,173,189),Color.rgb(229,154,247),Color.rgb(238,139,109))
    fun stepColor(index:Int)=STEP_COLORS[index.coerceIn(0,STEP_COLORS.lastIndex)]

    fun dp(context:Context,value:Int)=(value*context.resources.displayMetrics.density+.5f).toInt()
    fun rounded(context:Context,color:Int,radius:Int,stroke:Int)=GradientDrawable().apply {
        setColor(color);cornerRadius=dp(context,radius).toFloat();setStroke(dp(context,1),stroke)
    }
    /** Background of every floating panel; the stroke follows the current step color. */
    fun overlayPanel(context:Context,stroke:Int=ACCENT)=GradientDrawable().apply {
        setColor(PANEL);cornerRadius=dp(context,16).toFloat();setStroke(dp(context,2),stroke)
    }
    fun title(context:Context,text:String,color:Int=ACCENT)=TextView(context).apply {
        this.text=text;textSize=15f;setTextColor(color);setTypeface(typeface,Typeface.BOLD);setPadding(0,dp(context,2),0,dp(context,4))
    }
    fun body(context:Context,text:String)=TextView(context).apply {
        this.text=text;textSize=12f;setTextColor(FOREGROUND);setPadding(0,dp(context,2),0,dp(context,6))
    }
    /** The hands-off warning shown by every automatic scan. */
    fun handsOffWarning(context:Context)=TextView(context).apply {
        text="NIE DOTYKAJ EKRANU\nTRWA AUTOMATYCZNY ODCZYT";textSize=12f;gravity=Gravity.CENTER;setTextColor(WARNING);setTypeface(typeface,Typeface.BOLD)
    }
    /** Same compact control for battle, research, war, ranking and automatic-resource overlays. */
    fun overlayAction(context:Context,label:String,primary:Boolean=false,action:()->Unit)=Button(context).apply {
        text=label;contentDescription=label;isAllCaps=false;textSize=12f;gravity=Gravity.CENTER;maxLines=2
        // Compact overlay controls: the game stays visible; 40 dp keeps a comfortable touch target.
        minHeight=dp(context,40);minimumHeight=dp(context,40);minWidth=0;minimumWidth=0
        setPadding(dp(context,8),dp(context,3),dp(context,8),dp(context,3))
        stateListAnimator=null
        setTextColor(if(primary) ON_ACCENT else FOREGROUND)
        background=android.graphics.drawable.InsetDrawable(rounded(context,if(primary) ACCENT else CONTROL,10,if(primary) ACCENT else BORDER),0,dp(context,4),0,dp(context,4))
        setOnClickListener {action()}
    }
    /**
     * Horizontal row of overlay buttons: no baseline alignment (a two-line label must not push its
     * neighbour down), equal heights and a fixed gap. Add children with [rowItem].
     */
    fun actionRow(context:Context)=android.widget.LinearLayout(context).apply {
        orientation=android.widget.LinearLayout.HORIZONTAL;isBaselineAligned=false;gravity=Gravity.CENTER_VERTICAL
    }
    fun rowItem(context:Context,height:Int=44)=android.widget.LinearLayout.LayoutParams(0,dp(context,height),1f).apply {
        setMargins(dp(context,3),0,dp(context,3),0)
    }
    fun button(context:Context,label:String,primary:Boolean=false,quiet:Boolean=false,action:()->Unit)=Button(context).apply {
        text=label;contentDescription=label;textSize=if(quiet) 13f else 15f;isAllCaps=false;gravity=Gravity.CENTER
        minHeight=dp(context,48);minimumHeight=dp(context,48);setPadding(dp(context,12),dp(context,12),dp(context,12),dp(context,12))
        stateListAnimator=null
        setTextColor(if(primary) Color.WHITE else if(quiet) MUTED else FOREGROUND)
        background=rounded(context,if(primary) PRIMARY else if(quiet) BACKGROUND else INPUT_BACKGROUND,12,if(primary) PRIMARY else if(quiet) BACKGROUND else BORDER)
        setOnClickListener { action() }
    }
}
