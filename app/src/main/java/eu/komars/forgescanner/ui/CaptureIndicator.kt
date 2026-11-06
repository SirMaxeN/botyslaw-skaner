package eu.komars.forgescanner.ui
import android.content.Context
import android.graphics.*
import android.view.*
import android.widget.TextView
/** A reserved, non-interactive corner stays visible while the capture session is active. */
class CaptureIndicator(private val context:Context) {
 private val wm=context.getSystemService(WindowManager::class.java)
 private var view:TextView?=null
 fun show() {
  if(view!=null)return
  val size=ScannerAppearance.dp(context,100)
  val v=TextView(context).apply {text="Bot skanuje";textSize=12f;gravity=Gravity.CENTER;setTextColor(ScannerAppearance.ACCENT);background=ScannerAppearance.rounded(context,ScannerAppearance.BACKGROUND,6,ScannerAppearance.ACCENT)}
  val lp=WindowManager.LayoutParams(size,ScannerAppearance.dp(context,24),WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT).apply {gravity=Gravity.TOP or Gravity.RIGHT
   if(android.os.Build.VERSION.SDK_INT>=30)setFitInsetsTypes(0)
  }
  runCatching {wm.addView(v,lp);view=v
   v.viewTreeObserver.addOnGlobalLayoutListener {
    if(view===v) {val at=IntArray(2);v.getLocationOnScreen(at)
     val metrics=android.util.DisplayMetrics()
     @Suppress("DEPRECATION")
     wm.defaultDisplay.getRealMetrics(metrics)
     ignoredBounds=floatArrayOf(at[0].toFloat()/metrics.widthPixels,at[1].toFloat()/metrics.heightPixels,(at[0]+v.width).toFloat()/metrics.widthPixels,(at[1]+v.height).toFloat()/metrics.heightPixels)
    }
   }}
 }
 fun hide() {view?.let {runCatching {wm.removeViewImmediate(it)}};view=null;ignoredBounds=null}
 companion object {
  @Volatile private var ignoredBounds:FloatArray?=null
  fun maskPixels(pixels:IntArray,width:Int,height:Int) {
   val r=ignoredBounds?:return
   if(pixels.size!=width*height)return
   val l=(r[0]*width-2).toInt().coerceIn(0,width);val t=(r[1]*height-2).toInt().coerceIn(0,height)
   val right=kotlin.math.ceil(r[2]*width+2).toInt().coerceIn(l,width);val bottom=kotlin.math.ceil(r[3]*height+2).toInt().coerceIn(t,height)
   for(y in t until bottom)for(x in l until right)pixels[y*width+x]=Color.BLACK
  }
 }
}
