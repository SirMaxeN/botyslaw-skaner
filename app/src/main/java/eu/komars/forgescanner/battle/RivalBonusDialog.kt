package eu.komars.forgescanner.battle

import android.app.AlertDialog
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.view.ContextThemeWrapper
import android.view.WindowManager
import android.widget.*
import eu.komars.forgescanner.ui.ScannerAppearance as Style

/** Manual fallback for the rival's war bonus: two numbers, typed with or without "%". */
internal class RivalBonusDialog(private val context:Context) {
 private var dialog:AlertDialog?=null
 fun hide(){dialog?.dismiss();dialog=null}

 companion object {
  /** "0", "0%", "8700 %", "8.7k%", "+12,5%" → value in percent; null when not a number. */
  fun parse(value:String):Double?=BattleText.number(value.trim().removeSuffix("%").trim().replace(" ",""))?.takeIf {it in 0.0..100000.0}
 }

 fun show(read:String,save:(Double,Double)->Unit,cancel:()->Unit) {
  hide();val themed=ContextThemeWrapper(context,eu.komars.forgescanner.R.style.ScannerDialogTheme)
  val body=LinearLayout(themed).apply {orientation=LinearLayout.VERTICAL;setPadding(Style.dp(context,16),Style.dp(context,8),Style.dp(context,16),Style.dp(context,8));setBackgroundColor(Style.BACKGROUND)}
  body.addView(TextView(themed).apply {text="Nie odczytano premii rywala. Przepisz obie liczby z okna gry (np. 0, 0% albo 8.7k%).${if(read.isNotBlank())"\nOdczytano: „${read.replace('\n',' ').take(120)}”" else ""}";setTextColor(Style.MUTED);textSize=14f})
  fun input(label:String):EditText {
   body.addView(TextView(themed).apply {text=label;setTextColor(Style.FOREGROUND)})
   return EditText(themed).apply {
    setSingleLine(true);setTextColor(Style.FOREGROUND);textSize=16f
    inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
    hint="np. 0%";setHintTextColor(Style.MUTED)
    background=Style.rounded(context,Style.INPUT_BACKGROUND,8,Style.BORDER)
    setPadding(Style.dp(context,10),Style.dp(context,10),Style.dp(context,10),Style.dp(context,10));body.addView(this,LinearLayout.LayoutParams(-1,-2))
   }
  }
  val damage=input("Obrażenia rywala (%)")
  val health=input("Zdrowie rywala (%)")
  val alert=AlertDialog.Builder(themed).setTitle("Premia rywala").setView(ScrollView(themed).apply {addView(body)})
   .setNegativeButton("Wróć",null).setPositiveButton("Zapisz i kontynuuj",null).create()
  dialog=alert;alert.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);alert.show()
  alert.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);alert.window?.setBackgroundDrawable(ColorDrawable(Style.BACKGROUND))
  listOf(AlertDialog.BUTTON_POSITIVE,AlertDialog.BUTTON_NEGATIVE).forEach {alert.getButton(it).apply {setTextColor(Style.ACCENT);isAllCaps=false}}
  alert.setOnDismissListener {if(dialog===alert)dialog=null}
  alert.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {alert.dismiss();android.os.Handler(android.os.Looper.getMainLooper()).post {cancel()}}
  alert.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
   val d=parse(damage.text.toString());val h=parse(health.text.toString())
   if(d==null){damage.error="Wpisz liczbę, np. 0 lub 0%";return@setOnClickListener}
   if(h==null){health.error="Wpisz liczbę, np. 0 lub 0%";return@setOnClickListener}
   alert.window?.decorView?.windowToken?.let {token->context.getSystemService(android.view.inputmethod.InputMethodManager::class.java)?.hideSoftInputFromWindow(token,0)}
   alert.dismiss();android.os.Handler(android.os.Looper.getMainLooper()).post {save(d,h)}
  }
 }
}
