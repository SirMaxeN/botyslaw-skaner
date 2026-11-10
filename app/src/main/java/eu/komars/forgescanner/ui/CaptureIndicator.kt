package eu.komars.forgescanner.ui
import android.content.Context
import android.graphics.*
import android.view.*
import android.widget.TextView
/**
 * A reserved, non-interactive "Bot skanuje" label while the capture session is active: as high as possible in
 * the top-left corner (also over the camera cut-out strip). While a read field would sit under it, it jumps to the
 * opposite top corner and comes back afterwards ([avoid]).
 */
class CaptureIndicator(private val context:Context) {
 private val wm=context.getSystemService(WindowManager::class.java)
 private var view:TextView?=null
 private var params:WindowManager.LayoutParams?=null
 fun show() {
  if(view!=null)return
  val size=ScannerAppearance.dp(context,100)
  val v=TextView(context).apply {text="Bot skanuje";textSize=11f;gravity=Gravity.CENTER;setTextColor(ScannerAppearance.ACCENT);background=ScannerAppearance.rounded(context,ScannerAppearance.BACKGROUND,6,ScannerAppearance.ACCENT)}
  val lp=WindowManager.LayoutParams(size,ScannerAppearance.dp(context,20),WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,PixelFormat.TRANSLUCENT).apply {
   gravity=Gravity.TOP or Gravity.LEFT;x=0;y=0
   if(android.os.Build.VERSION.SDK_INT>=30){setFitInsetsTypes(0);layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS}
   else if(android.os.Build.VERSION.SDK_INT>=28)layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
  }
  runCatching {wm.addView(v,lp);view=v;params=lp;active=this
   v.viewTreeObserver.addOnGlobalLayoutListener {
    if(view===v) {val at=IntArray(2);v.getLocationOnScreen(at)
     val metrics=android.util.DisplayMetrics()
     @Suppress("DEPRECATION")
     wm.defaultDisplay.getRealMetrics(metrics)
     ignoredBounds=floatArrayOf(at[0].toFloat()/metrics.widthPixels,at[1].toFloat()/metrics.heightPixels,(at[0]+v.width).toFloat()/metrics.widthPixels,(at[1]+v.height).toFloat()/metrics.heightPixels)
     screen=Rect(at[0],at[1],at[0]+v.width,at[1]+v.height)
    }
   }}
 }
 fun hide() {view?.let {runCatching {wm.removeViewImmediate(it)}};view=null;params=null;ignoredBounds=null;screen=null;if(active===this)active=null}
 private fun moveTo(right:Boolean) {
  val v=view?:return;val lp=params?:return
  val gravity=Gravity.TOP or (if(right)Gravity.RIGHT else Gravity.LEFT)
  if(lp.gravity==gravity)return
  lp.gravity=gravity;runCatching {wm.updateViewLayout(v,lp)}
 }
 companion object {
  @Volatile private var ignoredBounds:FloatArray?=null
  @Volatile private var screen:Rect?=null
  private var active:CaptureIndicator?=null
  /** [region] in screen pixels about to be read; null = back to the home corner. */
  fun avoid(region:Rect?) {
   val indicator=active?:return
   val main=android.os.Handler(android.os.Looper.getMainLooper())
   main.post {
    // Stateless: home corner (left) unless the field to read overlaps it; then the right corner.
    val v=indicator.view?:return@post
    val top=screen?.top?:0
    val left=Rect(0,top,v.width,top+v.height)
    indicator.moveTo(region!=null&&Rect.intersects(left,Rect(region).apply {inset(-8,-8)}))
   }
  }
  fun maskPixels(pixels:IntArray,width:Int,height:Int) {
   val r=ignoredBounds?:return
   if(pixels.size!=width*height)return
   val l=(r[0]*width-2).toInt().coerceIn(0,width);val t=(r[1]*height-2).toInt().coerceIn(0,height)
   val right=kotlin.math.ceil(r[2]*width+2).toInt().coerceIn(l,width);val bottom=kotlin.math.ceil(r[3]*height+2).toInt().coerceIn(t,height)
   for(y in t until bottom)for(x in l until right)pixels[y*width+x]=Color.BLACK
  }
 }
}
