package eu.komars.forgescanner.battle

import android.app.AlertDialog
import android.content.Context
import android.view.ContextThemeWrapper
import android.view.WindowManager
import android.widget.*
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import eu.komars.forgescanner.ui.ScannerAppearance as Style

/** Manual fallback presents only one unresolved person and their cached game text. */
internal class RosterRepairDialog(private val context:Context) {
 private var dialog:AlertDialog?=null
 fun hide(){dialog?.dismiss();dialog=null}
 fun show(index:Int,candidate:RosterReading,snapshot:RosterSnapshot?,save:(String,Double)->Boolean,complete:()->Unit) {
  hide();val themed=ContextThemeWrapper(context,eu.komars.forgescanner.R.style.ScannerDialogTheme)
  val body=LinearLayout(themed).apply {orientation=LinearLayout.VERTICAL;setPadding(Style.dp(context,16),Style.dp(context,8),Style.dp(context,16),Style.dp(context,8));setBackgroundColor(Style.BACKGROUND)}
  body.addView(TextView(themed).apply {text="Pozostałe postacie są zachowane. Uzupełnij tylko ten wpis na podstawie zapisanego fragmentu.";setTextColor(Style.MUTED);textSize=14f})
  val preview=snapshot?.let {Bitmap.createBitmap(it.pixels,it.width,it.slot*2,Bitmap.Config.ARGB_8888)}
  if(preview!=null)body.addView(ImageView(themed).apply {setImageBitmap(preview);adjustViewBounds=true;scaleType=ImageView.ScaleType.FIT_CENTER},LinearLayout.LayoutParams(-1,Style.dp(context,125)))
  fun input(label:String,value:String?,numeric:Boolean):EditText {
   body.addView(TextView(themed).apply {text=label;setTextColor(Style.FOREGROUND)})
   return EditText(themed).apply {
    setText(value.orEmpty());setSingleLine(true);setTextColor(Style.FOREGROUND);textSize=16f
    inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
    hint=if(numeric)"np. 1.23b lub 897m"else"Nick gracza";setHintTextColor(Style.MUTED)
    background=Style.rounded(context,Style.INPUT_BACKGROUND,8,if(value==null)0xFFFF7070.toInt()else Style.BORDER)
    setPadding(Style.dp(context,10),Style.dp(context,10),Style.dp(context,10),Style.dp(context,10));body.addView(this,LinearLayout.LayoutParams(-1,-2))
   }
  }
  val name=input("Nick",candidate.name,false)
  val power=input("Moc",candidate.power?.let {java.math.BigDecimal.valueOf(it).stripTrailingZeros().toPlainString()},true)
  val scroll=ScrollView(themed).apply {addView(body)}
  val alert=AlertDialog.Builder(themed).setTitle("Uzupełnij postać ${index+1}").setView(scroll).setNegativeButton("Wróć",null).setPositiveButton("Zapisz i kontynuuj",null).create()
  dialog=alert;alert.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);alert.show()
  alert.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);alert.window?.setBackgroundDrawable(ColorDrawable(Style.BACKGROUND))
  listOf(AlertDialog.BUTTON_POSITIVE,AlertDialog.BUTTON_NEGATIVE).forEach {alert.getButton(it).apply {setTextColor(Style.ACCENT);isAllCaps=false}}
  alert.setOnDismissListener {if(dialog===alert)dialog=null;if(preview!=null)android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({if(!preview.isRecycled)preview.recycle()},300)}
  alert.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
   val parsed=BattleText.number(power.text.toString().trim())
   if(name.text.isNullOrBlank()){name.error="Wpisz nick";return@setOnClickListener}
   if(parsed==null||parsed<=0){power.error="Wpisz poprawną moc";return@setOnClickListener}
   if(!save(name.text.toString().trim(),parsed)){name.error="Sprawdź nick — taki wpis jest już na liście";return@setOnClickListener}
   alert.window?.decorView?.windowToken?.let {token->context.getSystemService(android.view.inputmethod.InputMethodManager::class.java)?.hideSoftInputFromWindow(token,0)}
   alert.dismiss();android.os.Handler(android.os.Looper.getMainLooper()).post {complete()}
  }
 }
}
