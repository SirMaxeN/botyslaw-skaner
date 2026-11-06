package eu.komars.forgescanner.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.Button

/** Shared palette and controls from the original Botyslaw scanner. */
object ScannerAppearance {
    val BACKGROUND=Color.rgb(11,17,29)
    val INPUT_BACKGROUND=Color.rgb(18,28,45)
    val BORDER=Color.rgb(43,58,80)
    val FOREGROUND=Color.rgb(237,242,251)
    val MUTED=Color.rgb(158,176,200)
    val ACCENT=Color.rgb(114,216,181)
    val PRIMARY=Color.rgb(26,107,92)
    fun dp(context:Context,value:Int)=(value*context.resources.displayMetrics.density+.5f).toInt()
    fun rounded(context:Context,color:Int,radius:Int,stroke:Int)=GradientDrawable().apply {
        setColor(color);cornerRadius=dp(context,radius).toFloat();setStroke(dp(context,1),stroke)
    }
    /** Same compact native control for the battle, research and war configuration overlays. */
    fun overlayAction(context:Context,label:String,action:()->Unit)=Button(context).apply {
        text=label;contentDescription=label;isAllCaps=false;textSize=14f
        minHeight=dp(context,48);minimumHeight=dp(context,48)
        setOnClickListener {action()}
    }
    fun button(context:Context,label:String,primary:Boolean=false,quiet:Boolean=false,action:()->Unit)=Button(context).apply {
        text=label;contentDescription=label;textSize=if(quiet) 13f else 15f;isAllCaps=false;gravity=Gravity.CENTER
        minHeight=dp(context,48);minimumHeight=dp(context,48);setPadding(dp(context,12),dp(context,12),dp(context,12),dp(context,12))
        setTextColor(if(primary) Color.WHITE else if(quiet) MUTED else FOREGROUND)
        background=rounded(context,if(primary) PRIMARY else if(quiet) BACKGROUND else INPUT_BACKGROUND,12,if(primary) PRIMARY else if(quiet) BACKGROUND else BORDER)
        setOnClickListener { action() }
    }
}
