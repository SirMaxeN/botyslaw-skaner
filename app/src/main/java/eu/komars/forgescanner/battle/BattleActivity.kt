package eu.komars.forgescanner.battle

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.*
import eu.komars.forgescanner.ScannerApplication
import eu.komars.forgescanner.MainActivity
import eu.komars.forgescanner.automation.GestureConnection
import eu.komars.forgescanner.capture.CaptureConnection
import eu.komars.forgescanner.capture.CaptureState
import eu.komars.forgescanner.capture.FrameCaptureService
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date
import eu.komars.forgescanner.ui.ScannerAppearance as Style
import android.view.Gravity
import android.graphics.Typeface
import android.view.WindowInsets
import android.os.Build

class BattleActivity: Activity() {
    private val app get()=application as ScannerApplication
    private lateinit var body:LinearLayout
    private var pending:String?=null
    private var requested=false
    private val listener:(CaptureState,String?)->Unit = { state,detail ->
        if(state==CaptureState.READY && requested) {
            requested=false
            val side=pending ?: "self"
            if(side=="clan-ranking")app.warOverlay.offer(false,full=true,ranking=true)else if(side=="war-full-test")app.warOverlay.offer(true,test=true,full=true)else if(side=="war-test") app.warOverlay.offer(true,test=true) else if(side=="war-prep" || side=="war-list") app.warOverlay.offer(side=="war-list") else app.battleOverlay.show(side)
            packageManager.getLaunchIntentForPackage("com.hariwn.legendofcivilizations")?.let { startActivity(it) }
                ?: Toast.makeText(this,"Otwórz Forge Master ręcznie",Toast.LENGTH_LONG).show()
        } else if(state==CaptureState.ERROR) { requested=false; message(detail ?: "Nie udało się uruchomić odczytu") }
    }
    override fun onCreate(state:Bundle?) {
        super.onCreate(state)
        pending=state?.getString("pending"); requested=state?.getBoolean("requested") ?: false
        app.battleMode=true
        app.overlay.hide(); app.autoOverlay.hide()
        home()
    }
    override fun onDestroy() {
        if (isFinishing && !isChangingConfigurations) app.closeScanner()
        super.onDestroy()
    }
    override fun onStart() { super.onStart(); CaptureConnection.addStateListener(listener) }
    override fun onResume() { super.onResume(); home() }
    override fun onStop() { CaptureConnection.removeStateListener(listener); super.onStop() }
    override fun onSaveInstanceState(out:Bundle) { out.putString("pending",pending); out.putBoolean("requested",requested); super.onSaveInstanceState(out) }
    override fun onNewIntent(intent:Intent) {
        super.onNewIntent(intent); setIntent(intent); home()
    }
    private fun dp(value:Int)=Style.dp(this,value)
    private fun page(title:String) {
        window.statusBarColor=Style.BACKGROUND; window.navigationBarColor=Style.BACKGROUND
        val scroll=ScrollView(this).apply {
            setBackgroundColor(Style.BACKGROUND);isFillViewport=true;clipToPadding=false;setPadding(dp(24),dp(36),dp(24),dp(28))
        }
        if(Build.VERSION.SDK_INT>=30) scroll.setOnApplyWindowInsetsListener { view,insets ->
            val bars=insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            view.setPadding(dp(24)+bars.left,dp(24)+bars.top,dp(24)+bars.right,dp(28)+bars.bottom);insets
        } else scroll.fitsSystemWindows=true
        body=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        scroll.addView(body);setContentView(scroll)
        if(title=="Symulator walki" || title=="Walki") body.addView(ImageView(this).apply {
            setImageResource(eu.komars.forgescanner.R.drawable.menu_battles);contentDescription="Botysław Skaner"
        },LinearLayout.LayoutParams(dp(76),dp(76)).apply { bottomMargin=dp(18) })
        label(title,28);label("Botysław Skaner · wersja testowa",13)
    }
    private fun label(text:String,size:Int=15) { body.addView(TextView(this).apply {
        this.text=text;textSize=size.toFloat();setTextColor(if(size<=13) Style.MUTED else Style.FOREGROUND)
        setLineSpacing(dp(3).toFloat(),1f);setPadding(0,dp(10),0,dp(6))
        if(size>=20) setTypeface(typeface,Typeface.BOLD)
    }) }
    private fun button(text:String,enabled:Boolean=true,primary:Boolean=false,action:()->Unit):Button {
        val control=Style.button(this,text,primary,quiet=text.startsWith("Wróć"),action=action).apply {
            isEnabled=enabled;layoutParams=LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(12) }
        }
        body.addView(control)
        return control
    }
    private fun input(title:String,value:String=""):EditText {
        label(title,14)
        return EditText(this).apply {
            setText(value);setTextColor(Style.FOREGROUND);setHintTextColor(Style.MUTED);textSize=18f
            inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;isSingleLine=true
            setPadding(dp(14),dp(11),dp(14),dp(11));background=Style.rounded(this@BattleActivity,Style.INPUT_BACKGROUND,10,Style.BORDER)
            setSelectAllOnFocus(true);contentDescription=title
            body.addView(this,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(7) })
        }
    }
    private fun message(text:String) { if(!isFinishing) AlertDialog.Builder(this).setMessage(text).setPositiveButton("OK",null).show() }
    private fun home() {
        page("Walki")
        label("Przygotuj postać i sprawdź szanse w starciu",16)
        label("Odczyt ekranu gry",16)
        label("Po zgodzie Androida analizujemy obraz Forge Master na telefonie. Obrazy nie trafiają do serwera. STOP lub Wróć do aplikacji kończy udostępnianie.",13)
        if(!app.battleOverlay.isConfigured()) button("Konfiguracja",primary=true) { prepare("setup") }
        else {
            button("Symulator walki",primary=true) { prepare("menu") }
        }
        val open=WarPreparationModel.day()!=null
        if(app.battleOverlay.isConfigured()) {
            val listButton=button("Lista wojenna",enabled=open && app.mobile.paired) { prepare("war-list") }
            if(open && app.mobile.paired) {
                listButton.isEnabled=false
                listButton.text="Lista wojenna — sprawdzam dostępność…"
                app.mobile.warStatus { result ->
                    if(!isFinishing && !isDestroyed && listButton.parent===body) {
                        result.map { JSONObject(it).getBoolean("locked") }.fold({ locked ->
                            listButton.isEnabled=!locked && WarPreparationModel.day()!=null
                            listButton.text=if(locked) "Lista wojenna — już utworzona" else "Lista wojenna"
                            listButton.contentDescription=if(locked) "Lista na tę wojnę została już utworzona na tym serwerze Discorda. Nie nadpisujemy jej." else "Lista wojenna"
                        },{
                            listButton.text="Lista wojenna"
                            listButton.isEnabled=WarPreparationModel.day()!=null
                        })
                    }
                }
            }
        }
        if(eu.komars.forgescanner.BuildConfig.DEBUG && packageName.endsWith(".autotest") && app.battleOverlay.isConfigured()) button("Test listy wojennej",enabled=app.mobile.paired) {prepare("war-test")}
        if(eu.komars.forgescanner.BuildConfig.DEBUG && packageName.endsWith(".autotest") && app.battleOverlay.isConfigured()) button("Pełne profile klanów + symulacje (TEST)",enabled=app.mobile.paired) {prepare("war-full-test")}
        if(eu.komars.forgescanner.BuildConfig.DEBUG && packageName.endsWith(".autotest") && app.battleOverlay.isConfigured()) button("Ranking klanu",enabled=app.mobile.paired) {prepare("clan-ranking")}
        if(!open) label("Lista wojenna: niedziela 01:00 – poniedziałek 01:00 (czas polski).",13)
        if(!app.mobile.paired) label("Przygotowanie wojenne i lista wymagają połączenia z Discordem w menu głównym.",13)
        button("Menu główne") { app.closeScanner();startActivity(Intent(this,eu.komars.forgescanner.HomeActivity::class.java)) }
        eu.komars.forgescanner.ui.SupportFooter.add(this,body)
    }
    private fun prepare(side:String) {
        pending=side
        if(!Settings.canDrawOverlays(this)) {
            message("Zezwól tej testowej aplikacji na wyświetlanie nad grą, następnie wróć i wybierz skan ponownie.")
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName"))); return
        }
        if(!GestureConnection.isConnected) {
            AlertDialog.Builder(this).setTitle("Sterowanie skanowaniem")
                .setMessage("Botysław odczytuje obraz wybranej gry na telefonie i wykonuje zatwierdzone kliknięcia oraz przewijanie. Włącz usługę dostępności dla wersji testowej i wróć do aplikacji. Skan zaczyna się dopiero po naciśnięciu przycisku. STOP zatrzymuje działania; skaner nie wykonuje walk, zakupów ani zmian wyposażenia.")
                .setPositiveButton("Włącz sterowanie") { _,_ -> startActivity(GestureConnection.settingsIntent()) }
                .setNegativeButton("Anuluj",null).show(); return
        }
        app.battleMode=true; requested=true
        if(CaptureConnection.state==CaptureState.READY) listener(CaptureState.READY,null)
        else startActivityForResult(getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent(),810)
    }
    @Deprecated("Activity result API")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode==810) {
            if(resultCode!=RESULT_OK || data==null) { requested=false; return }
            try { FrameCaptureService.start(this,resultCode,data) }
            catch (_: Exception) { requested=false; message("Nie udało się rozpocząć udostępniania. Spróbuj ponownie.") }
        }
    }
}
