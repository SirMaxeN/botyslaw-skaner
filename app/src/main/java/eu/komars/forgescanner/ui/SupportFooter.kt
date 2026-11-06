package eu.komars.forgescanner.ui
import android.content.*
import android.net.Uri
import android.widget.*
object SupportFooter {
 fun add(context:Context,body:LinearLayout) {
  fun text(value:String,size:Float) {body.addView(TextView(context).apply {text=value;textSize=size;setTextColor(ScannerAppearance.MUTED)},LinearLayout.LayoutParams(-1,-2).apply {topMargin=ScannerAppearance.dp(context,16)})}
  text("Tworzone przez SirMaxeN",12f)
  text("Tworzę aplikacje z pasji i udostępniam je całkowicie za darmo. Niestety utrzymanie serwerów i narzędzi trochę kosztuje. Jeśli doceniasz moją pracę i chcesz wesprzeć tworzenie kolejnych przydatnych rzeczy, możesz postawić mi kawę. Dzięki za każde wsparcie!",13f)
  body.addView(ScannerAppearance.button(context,"☕ Postaw mi kawę",quiet=true) {runCatching {context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://buymeacoffee.com/sirmaxen")))}})
 }
}
