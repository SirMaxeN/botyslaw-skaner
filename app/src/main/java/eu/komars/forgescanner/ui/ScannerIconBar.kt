package eu.komars.forgescanner.ui
import android.content.Context
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
/** One button implementation for resource and battle overlays. */
object ScannerIconBar {
 fun cell(context:Context,count:Int)=minOf(ScannerAppearance.dp(context,44),(context.resources.displayMetrics.widthPixels-ScannerAppearance.dp(context,40))/count).coerceAtLeast(1)
 fun width(context:Context,count:Int)=cell(context,count)*count+ScannerAppearance.dp(context,12)*2
 fun height(context:Context,count:Int=3)=cell(context,count)+ScannerAppearance.dp(context,8)*2
 fun button(context:Context,symbol:String,label:String,action:()->Unit)=Button(context).apply {
  text=symbol;contentDescription=label;tooltipText=label;isAllCaps=false;textSize=19f
  minWidth=0;minimumWidth=0;minHeight=0;minimumHeight=0;stateListAnimator=null;includeFontPadding=false
  gravity=Gravity.CENTER;setPadding(0,0,0,0);setTextColor(ScannerAppearance.ACCENT)
  background=ScannerAppearance.rounded(context,ScannerAppearance.INPUT_BACKGROUND,8,ScannerAppearance.BORDER)
  setOnClickListener {action()}
 }
 fun create(context:Context,actions:List<Triple<String,String,()->Unit>>)=LinearLayout(context).apply {
  orientation=LinearLayout.HORIZONTAL;isBaselineAligned=false;gravity=Gravity.CENTER
  setPadding(ScannerAppearance.dp(context,12),ScannerAppearance.dp(context,8),ScannerAppearance.dp(context,12),ScannerAppearance.dp(context,8))
  background=ScannerAppearance.overlayPanel(context)
  actions.forEach {(label,symbol,action)->addView(button(context,symbol,label,action),LinearLayout.LayoutParams(cell(context,actions.size),cell(context,actions.size)))}
 }
}
