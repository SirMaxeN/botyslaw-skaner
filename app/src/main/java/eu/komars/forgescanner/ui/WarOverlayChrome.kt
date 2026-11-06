package eu.komars.forgescanner.ui

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.*
import android.widget.*

/** War panel uses the same square icon controls as resources and the battle simulator. */
internal class WarOverlayChrome(private val context:Context,private val stop:()->Unit,private val boundary:()->Unit,
 private val title:String="Przygotowanie wojenne",private val pause:((Boolean)->Unit)?=null,private val interaction:(Boolean)->Unit) {
 private var paused=false
 private val pauseButton=ScannerAppearance.overlayAction(context,"⏸ Pauza") {togglePause()}
 private val pauseIcon=ScannerIconBar.button(context,"⏸","Pauza / wznów skan") {togglePause()}
 private fun togglePause() {
  paused=!paused
  pauseButton.text=if(paused)"▶ Wznów" else "⏸ Pauza";pauseButton.contentDescription=pauseButton.text
  (pauseIcon as? TextView)?.text=if(paused)"▶" else "⏸"
  pause?.invoke(paused)
 }
 /** Resets the toggle when the scan itself ends or restarts. */
 fun resetPause() {paused=false;pauseButton.text="⏸ Pauza";(pauseIcon as? TextView)?.text="⏸"}
 private val wm=context.getSystemService(WindowManager::class.java)
 private fun dp(n:Int)=ScannerAppearance.dp(context,n)
 private val screen get()=if(Build.VERSION.SDK_INT>=30)wm.maximumWindowMetrics.bounds else android.graphics.Rect(0,0,context.resources.displayMetrics.widthPixels,context.resources.displayMetrics.heightPixels)
 val root=LinearLayout(context).apply {orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(8),dp(12),dp(8));background=ScannerAppearance.overlayPanel(context)}
 val content=LinearLayout(context).apply {orientation=LinearLayout.VERTICAL}
 val recovery=LinearLayout(context).apply {orientation=LinearLayout.VERTICAL}
 private val fullHeader=LinearLayout(context).apply {gravity=Gravity.CENTER_VERTICAL}
 private val compactRow=LinearLayout(context).apply {gravity=Gravity.CENTER;isBaselineAligned=false}
 private val controls=ScannerAppearance.actionRow(context)
 private val scroll=object:ScrollView(context) {
  override fun onMeasure(w:Int,h:Int) {super.onMeasure(w,View.MeasureSpec.makeMeasureSpec(minOf(dp(360),(screen.height()*.60).toInt()),View.MeasureSpec.AT_MOST))}
 }.apply {isFillViewport=false;addView(content)}
 private val boundaryIcon=ScannerIconBar.button(context,"↕","Zatrzymaj listę / lista jest na górze",boundary)
 var dragging=false;private set
 var compact=false;private set
 private var startX=0;private var startY=0;private var touchX=0f;private var touchY=0f
 private val drag=View.OnTouchListener {_,event ->
  val lp=root.layoutParams as? WindowManager.LayoutParams
  if(lp==null)false else when(event.actionMasked) {
   MotionEvent.ACTION_DOWN->{dragging=true;startX=lp.x;startY=lp.y;touchX=event.rawX;touchY=event.rawY;interaction(true);true}
   MotionEvent.ACTION_MOVE->{lp.x=(startX+event.rawX-touchX).toInt();lp.y=(startY+event.rawY-touchY).toInt();clamp(lp);wm.updateViewLayout(root,lp);true}
   MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL->{dragging=false;interaction(false);true}
   else->false
  }
 }
 private fun handle()=TextView(context).apply {text="⠿";gravity=Gravity.CENTER;textSize=22f;setTextColor(ScannerAppearance.ACCENT);contentDescription="Przesuń nakładkę";setOnTouchListener(drag)}
 init {
  fullHeader.addView(handle(),LinearLayout.LayoutParams(dp(28),dp(44)))
  fullHeader.addView(TextView(context).apply {text=title;setTextColor(ScannerAppearance.ACCENT);textSize=13f;setOnTouchListener(drag)},LinearLayout.LayoutParams(0,dp(44),1f))
  fullHeader.addView(ScannerIconBar.button(context,"▁","Zwiń do ikon") {setCompact(true)},LinearLayout.LayoutParams(dp(44),dp(44)))
  compactRow.addView(handle(),LinearLayout.LayoutParams(dp(24),ScannerIconBar.cell(context,3)))
  for(button in listOf(ScannerIconBar.button(context,"⛶","Rozwiń nakładkę") {setCompact(false)},boundaryIcon,ScannerIconBar.button(context,"✕","Zakończ odczyt ekranu",stop)))compactRow.addView(button,LinearLayout.LayoutParams(ScannerIconBar.cell(context,3),ScannerIconBar.cell(context,3)))
  root.addView(fullHeader);root.addView(compactRow);root.addView(scroll)
  if(pause!=null)controls.addView(pauseButton,ScannerAppearance.rowItem(context))
  controls.addView(ScannerAppearance.overlayAction(context,"STOP / Zakończ",action=stop),ScannerAppearance.rowItem(context));root.addView(controls)
  if(pause!=null)compactRow.addView(pauseIcon,1,LinearLayout.LayoutParams(ScannerIconBar.cell(context,3),ScannerIconBar.cell(context,3)))
  compactRow.visibility=View.GONE;boundaryIcon.visibility=View.GONE
 }
 fun addListControl(view:View){controls.addView(view,ScannerAppearance.rowItem(context))}
 fun listMode(enabled:Boolean,rewinding:Boolean) {val visibility=if(enabled)View.VISIBLE else View.GONE;val changed=boundaryIcon.visibility!=visibility;boundaryIcon.visibility=visibility;boundaryIcon.contentDescription=if(rewinding)"Lista jest na górze"else"Zatrzymaj listę";if(changed)resize()}
 fun show() {
  wm.addView(root,WindowManager.LayoutParams(minOf(dp(250),screen.width()-dp(16)),-2,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT).apply {
   gravity=Gravity.TOP or Gravity.START;x=dp(8);y=dp(28)
   if(Build.VERSION.SDK_INT>=30){setFitInsetsTypes(0);layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS}
  })
 }
 fun setCompact(value:Boolean) {interaction(true);compact=value;fullHeader.visibility=if(value)View.GONE else View.VISIBLE;compactRow.visibility=if(value)View.VISIBLE else View.GONE;scroll.visibility=if(value)View.GONE else View.VISIBLE;controls.visibility=if(value)View.GONE else View.VISIBLE;resize();root.post {interaction(false)}}
 private fun clamp(lp:WindowManager.LayoutParams) {lp.x=lp.x.coerceIn(0,(screen.width()-lp.width).coerceAtLeast(0));lp.y=lp.y.coerceIn(dp(24),(screen.height()-root.measuredHeight-dp(28)).coerceAtLeast(dp(24)))}
 private fun resize() {
  val lp=root.layoutParams as? WindowManager.LayoutParams?:return
  // Every visible icon (expand, pause, list boundary, close) gets a full cell, so none is clipped.
  val icons=(1 until compactRow.childCount).count {compactRow.getChildAt(it).visibility==View.VISIBLE}
  lp.width=if(compact)dp(48)+ScannerIconBar.cell(context,3)*icons else minOf(dp(250),screen.width()-dp(16))
  root.measure(View.MeasureSpec.makeMeasureSpec(lp.width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));clamp(lp);wm.updateViewLayout(root,lp)
 }
}
