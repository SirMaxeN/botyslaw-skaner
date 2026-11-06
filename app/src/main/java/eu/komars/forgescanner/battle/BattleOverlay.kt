package eu.komars.forgescanner.battle

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import eu.komars.forgescanner.capture.CaptureConnection
import eu.komars.forgescanner.capture.CaptureState
import eu.komars.forgescanner.automation.GestureConnection
import eu.komars.forgescanner.automation.GestureSession
import eu.komars.forgescanner.ui.ScreenSelectionWindow
import eu.komars.forgescanner.ui.AutomationTouchShield
import org.json.JSONObject

/** Local-only, bounded scan. No screenshot is persisted; only geometry and reviewed text. */
class BattleOverlay(private val context: Context) {
    private val prefs=context.getSharedPreferences("scan_calibrations_v1",0)
    private val drafts=context.getSharedPreferences("battle_profiles_v1",0)
    private val wm=context.getSystemService(WindowManager::class.java)
    private val dialogs=BattleOverlayDialogs(context,onMinimized={ minimized -> panel?.visibility=if(minimized) android.view.View.GONE else android.view.View.VISIBLE },onSimulationCompleted={refreshChrome()})
    private val handler=Handler(Looper.getMainLooper())
    private val recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val capturedText=CapturedTextReader(recognizer)
    private var skillGate=CaptureAnalysisGate()
    private val fairyProgressions=FairyProgressionCatalog(context)
    private var fairyProgressionEvidence=FairyProgressionEvidence()
    private val fairyMemory=FairyLevelMemory(context)
    private var fairyGlyph:FairyLevelGlyph.Signature?=null
    private var fairySnapshot:Bitmap?=null
    private var fairyHint:String?=null
    private var fairyAbsent=false
    private var weaponAttempts=0
    private var weaponReadable=false
    private var headerPending=0
    private var headerReadable=false
    private var afterHeader:(()->Unit)?=null
    private val visualMemory=SkillVisualMemory(context)
    private val detector=BattleFieldDetector(recognizer)
    private val shield=AutomationTouchShield(context) { fail("Skan zatrzymany po dotknięciu ekranu.") }
    private var panel: LinearLayout?=null
    private var batchComplete:((Result<JSONObject>)->Unit)?=null
    private var borrowedPanel=false
    private var batchDragging:(()->Boolean)?=null
    private var batchResume:JSONObject?=null
    private var batchCheckpoint:((JSONObject)->Unit)?=null
    private var batchNavigation:((Rect)->Unit)?=null
    private var batchLastCheckpoint:String?=null
    private var batchLayout:BattleSetupGeometry.Layout?=null
    private var batchSections=JSONObject()
    private var batchExpectedName:String?=null
    private var batchIdentityVerified=false
    private val quickSkills=mutableMapOf<Int,JSONObject>()
    private var quickSkillPending=0
    private var afterQuickSkills:(()->Unit)?=null
    private fun saveBatch(section:String?=null) {
        if(!borrowedPanel || !batchIdentityVerified)return
        if(section!=null)batchSections.put(section,true)
        val draft=draftSnapshot().put("_sections",JSONObject(batchSections.toString()))
        val stable=JSONObject(draft.toString()).apply {remove("scannedAt")}.toString()
        if(stable!=batchLastCheckpoint){batchCheckpoint?.invoke(draft);batchLastCheckpoint=stable}
    }
    private var batchPets:Int?=null;private var batchMounted:Boolean?=null
    fun cancelBatch() {if(borrowedPanel)hide()}
    fun batchInteraction(touching:Boolean) {if(borrowedPanel&&touching)shield.hide()}
    fun scanBatch(box:LinearLayout,progress:TextView?,dragging:()->Boolean,resume:JSONObject?=null,checkpoint:((JSONObject)->Unit)?=null,navigation:((Rect)->Unit)?=null,expectedName:String,done:(Result<JSONObject>)->Unit) {
        hide()
        name="";damage="";health="";weapon="";damageRaw="";healthRaw="";headerRaw="";texts.clear();skillScans=org.json.JSONArray();fairyScan=JSONObject();skillEvidence.clear()
        borrowedPanel=true;panel=box;status=progress;stageLabel=null;warning=null;runRow=null;setupRow=null;compactRow=null;expandButton=null
        batchExpectedName=expectedName;batchIdentityVerified=false
        batchComplete=done;batchDragging=dragging;batchResume=resume;batchCheckpoint=checkpoint;batchLayout=null;batchNavigation=navigation;batchLastCheckpoint=null;batchSections=JSONObject()
        if(resume!=null){batchSections=JSONObject(resume.optJSONObject("_sections")?.toString()?:"{}");if(!resume.has("_sections")){if(resume.optString("raw").isNotBlank())batchSections.put("bonuses",true);if(BattleText.weapon(resume.optString("weapon"))!=null)batchSections.put("weapon",true)}}
        CaptureConnection.addStateListener(captureListener)
        if(!ready("opponent")){deliverBatch(Result.failure(IllegalStateException("Najpierw skonfiguruj pola profilu.")));return}
        beginSide("opponent")
    }
    private fun deliverBatch(result:Result<JSONObject>) {
        val callback=batchComplete?:return;hide();handler.post {callback(result)}
    }
    private var compact=false
    private val repeatButtons=mutableListOf<Button>()
    private var recheckOpponent=false
    private var opponentReprepared=false
    private var ownHomeKnown=false
    private var compactRow:LinearLayout?=null
    private var expandButton:Button?=null
    private var setupIcon:Button?=null
    private var status: TextView?=null
    private var stageLabel:TextView?=null
    private var warning:TextView?=null
    private var runRow:LinearLayout?=null
    private val idleRows=mutableListOf<LinearLayout>()
    private var runtimeKey="open"
    private var setupRow:LinearLayout?=null
    private val scanButtons=mutableListOf<Pair<Button,String>>()
    private var selection: ScreenSelectionWindow?=null
        set(value) {field=value;updateIndicator()}
    private var selectionFrame: Bitmap?=null
    private var generation=0
    private var active=false
        set(value) {field=value;updateIndicator()}
    private var side="self"
    private var setup=false
    private var index=0
    private var session: GestureSession?=null
    private var width=0; private var height=0
    private var fields=JSONObject()
    private var name=""; private var damage=""; private var health=""; private var weapon=""
    private var skillScans=org.json.JSONArray()
    private val skillEvidence=mutableMapOf<Int,SkillReadEvidence>()
    private val texts=linkedSetOf<String>()
    private var previous=""; private var repeats=0; private var passes=0
    private var listMoved=false
    private var listAttempt=0
    private var scanRecoveries=0
    private var damageRaw=""; private var healthRaw=""; private var headerRaw=""
    private val seenBonusKeys=linkedSetOf<Bonus>()
    private var listPrompt:AlertDialog?=null
    private val captureListener: (CaptureState,String?)->Unit = { s,_ ->
        if(s==CaptureState.STOPPED || s==CaptureState.ERROR) {dialogs.resetOpponentSession();if(active)fail("Udostępnianie ekranu zakończone.")}
    }
    private val steps get() = (if(side=="self") listOf("open") else emptyList()) +
        listOf("name","header","bonuses","weapon","title","skill1","skill2","skill3","fairy") +
        if(side=="self") listOf("close") else emptyList()
    private val labels=mapOf("open" to "Małe i — otwórz informacje postaci", "name" to "Nazwa gracza",
        "skill1" to "Pierwsza umiejętność — skrajna lewa ikona", "skill2" to "Druga umiejętność", "skill3" to "Trzecia umiejętność — przed zwierzakami", "fairy" to "Wróżka — cała ikona i poziom pod nią", "skills" to "Trzy założone umiejętności", "header" to "Łączne obrażenia i zdrowie — cały prawy górny blok", "damage" to "Łączne obrażenia — tylko liczba z jednostką", "health" to "Łączne zdrowie — tylko liczba z jednostką",
        "bonuses" to "Widoczne pole przewijania bonusów — do miejsca ucięcia", "weapon" to "Pole założonej broni",
        "title" to "Okno broni — nazwa i obrażenia z typem",
        "back" to "Wyjście z broni — punkt POZA oknem broni", "close" to "Zamknij informacje postaci — czerwony przycisk")

    fun isConfigured()=ready("self") && (context.applicationContext as eu.komars.forgescanner.ScannerApplication).warOverlay.configurationReady()
    fun startInitialScan() {show("self");ownHomeKnown=true;side="self";scan()}
    private fun continueSetup() {
        if(ready("self")) (context.applicationContext as eu.komars.forgescanner.ScannerApplication).warOverlay.start(false,true)
        else beginSide("self",true)
    }
    private fun partialSetup(which:String)=prefs.contains("battle_setup_$which") && !ready(which)
    private fun restartSetup(which:String) {
        val edit=prefs.edit().remove("battle_setup_$which").remove("battle_$which")
        if(which=="self") prefs.all.keys.filter {it.startsWith("war_")}.forEach {edit.remove(it)}
        edit.apply()
        beginSide(which,true)
    }
    fun show(which: String) {
        hide();compact=false; side=if(which=="opponent") "opponent" else "self"
        CaptureConnection.addStateListener(captureListener)
        panel=LinearLayout(context).apply {
            orientation=LinearLayout.VERTICAL; setPadding(dp(12),dp(8),dp(12),dp(8))
            background=android.graphics.drawable.GradientDrawable().apply { setColor(0xFF10212B.toInt());setStroke(dp(2),0xFF61D6AC.toInt());cornerRadius=dp(16).toFloat() }
        }
        status=TextView(context).apply { setTextColor(eu.komars.forgescanner.ui.ScannerAppearance.ACCENT); textSize=14f; text="Symulator walki\nWybierz skan. Profil przeciwnika otwórz w grze." }
        stageLabel=TextView(context).apply { textSize=14f;setTypeface(null,android.graphics.Typeface.BOLD) };panel!!.addView(stageLabel)
        warning=TextView(context).apply { text="NIE DOTYKAJ EKRANU\nTRWA AUTOMATYCZNY ODCZYT";textSize=15f;gravity=Gravity.CENTER;setTextColor(0xFFFFDB76.toInt());setTypeface(null,android.graphics.Typeface.BOLD) };panel!!.addView(warning)
        panel!!.addView(status)
        var downX=0f;var downY=0f;var startX=0;var startY=0
        val drag=android.view.View.OnTouchListener { _,event ->
            val box=panel
            val lp=box?.layoutParams as? WindowManager.LayoutParams
            if(active || box==null || lp==null) false else when(event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> { downX=event.rawX;downY=event.rawY;startX=lp.x;startY=lp.y;true }
                android.view.MotionEvent.ACTION_MOVE -> {
                    lp.x=(startX+event.rawX-downX).toInt().coerceIn(0,(context.resources.displayMetrics.widthPixels-box.width).coerceAtLeast(0))
                    lp.y=(startY+event.rawY-downY).toInt().coerceIn(0,(context.resources.displayMetrics.heightPixels-box.height).coerceAtLeast(0))
                    wm.updateViewLayout(box,lp);true
                }
                android.view.MotionEvent.ACTION_UP,android.view.MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }
        stageLabel!!.setOnTouchListener(drag);status!!.setOnTouchListener(drag)
        compactRow=LinearLayout(context).also { mini ->
            mini.isBaselineAligned=false;mini.gravity=Gravity.CENTER;mini.clipChildren=false;mini.clipToPadding=false
            mini.addView(TextView(context).apply { text="⠿";textSize=22f;gravity=Gravity.CENTER;setTextColor(eu.komars.forgescanner.ui.ScannerAppearance.ACCENT);contentDescription="Przesuń nakładkę";setOnTouchListener(drag) },LinearLayout.LayoutParams(dp(24),dp(44)))
            fun icon(symbol:String,description:String,action:()->Unit):Button {
                val item=eu.komars.forgescanner.ui.ScannerIconBar.button(context,symbol,description,action)
                mini.addView(item,LinearLayout.LayoutParams(compactCell(),compactCell()));return item
            }
            setupIcon=icon("⌖","Ustaw pole") { if(!active && !isConfigured()) continueSetup() }
            scanButtons+=icon("♙","Skanuj moją postać") { if(!active && ready("self")) beginSide("self") } to "self"
            scanButtons+=icon("⚔","Skanuj przeciwnika") { if(!active && ready("opponent")) beginSide("opponent") } to "opponent"
            repeatButtons+=icon("↻","Powtórz walkę z ostatnim przeciwnikiem") { if(!active && dialogs.canRepeat()) dialogs.chooseBattle() }
            expandButton=icon("⛶","Rozwiń nakładkę") { if(!active) { compact=false;refreshChrome() } }
            icon("✕","Zakończ odczyt ekranu") { (context.applicationContext as eu.komars.forgescanner.ScannerApplication).closeScanner() }
            panel!!.addView(mini,0)
        }
        val row=LinearLayout(context)
        fun button(label: String, action:()->Unit) { row.addView(Button(context).apply {
            text=label; textSize=11f; isAllCaps=false; setPadding(dp(4),0,dp(4),0); minWidth=0;minimumWidth=0;minHeight=dp(44);minimumHeight=dp(44);gravity=Gravity.CENTER;maxLines=2
            setOnClickListener { action() }
        },LinearLayout.LayoutParams(0,dp(48),1f)) }
        button("Skanuj moje") { if(!active && ready("self")) beginSide("self") }
        button("Skanuj przeciwnika") { if(!active && ready("opponent")) beginSide("opponent") }
        (row.getChildAt(0) as Button).also { scanButtons+=it to "self" }
        (row.getChildAt(1) as Button).also { scanButtons+=it to "opponent" }
        panel!!.addView(row)
        val repeatRow=LinearLayout(context)
        repeatRow.addView(Button(context).apply {
            text="↻ Powtórz walkę";isAllCaps=false;setOnClickListener { if(!active && dialogs.canRepeat()) dialogs.chooseBattle() }
            repeatButtons+=this
        },LinearLayout.LayoutParams(-1,dp(44)))
        panel!!.addView(repeatRow)
        val tools=LinearLayout(context)
        fun tool(label:String,action:()->Unit) { tools.addView(Button(context).apply {
            text=label;isAllCaps=false;textSize=10f;minWidth=0;minimumWidth=0;setPadding(dp(2),0,dp(2),0);setOnClickListener { action() }
        },LinearLayout.LayoutParams(0,dp(44),1f)) }
        tool("Zwiń") { if(!active) { compact=true;refreshChrome() } }
        tool("Zakończ") { (context.applicationContext as eu.komars.forgescanner.ScannerApplication).closeScanner() }
        tool("Menu") { if(!active) dialogs.choose(listOf(
            "Wróć do aplikacji" to { returnToApp() },
            "Konfiguracja mojej postaci" to { beginSide("self",true) },
            "Ustaw od nowa" to { restartSetup("self") },
            "Popraw pola przeciwnika (opcjonalnie)" to { beginSide("opponent",true) },
            "Przygotowanie do bitwy wojennej" to { (context.applicationContext as eu.komars.forgescanner.ScannerApplication).warOverlay.start(false) },
            "Lista wojenna" to { (context.applicationContext as eu.komars.forgescanner.ScannerApplication).warOverlay.start(true) },
            "Moje dane" to { dialogs.review("self") { refreshChrome() } },
            "Dane przeciwnika" to { dialogs.review("opponent") { refreshChrome() } },
            "Zakończ odczyt i zamknij" to { (context.applicationContext as eu.komars.forgescanner.ScannerApplication).closeScanner() })) }
        panel!!.addView(tools)
        setupRow=LinearLayout(context).also { initial ->
            initial.addView(Button(context).apply { text=if(partialSetup("self") || ready("self")) "Kontynuuj" else "Ustaw pole";isAllCaps=false;setOnClickListener { if(!active) continueSetup() } },LinearLayout.LayoutParams(-1,dp(48)))
            panel!!.addView(initial,panel!!.indexOfChild(tools))
        }
        idleRows.addAll(listOf(row,repeatRow,tools))
        runRow=LinearLayout(context).also { running ->
            running.addView(eu.komars.forgescanner.ui.ScannerAppearance.overlayAction(context,"STOP") { (context.applicationContext as eu.komars.forgescanner.ScannerApplication).closeScanner() },LinearLayout.LayoutParams(-1,dp(48)))
            panel!!.addView(running)
        }
        refreshChrome()
        wm.addView(panel,WindowManager.LayoutParams(panelWidth(),-2,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT).apply {
            gravity=Gravity.TOP or Gravity.START; x=dp(8);y=dp(30)
            if(android.os.Build.VERSION.SDK_INT>=30) { setFitInsetsTypes(0);layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS }
        })
        val warDay=WarPreparationModel.day()
        val own=runCatching { JSONObject(drafts.getString("self","{}")!!) }.getOrNull()
        if(ready("self") && warDay!=null && own!=null && WarPreparationModel.day(java.time.Instant.ofEpochMilli(own.optLong("scannedAt")))!=warDay) {
            status?.text="Dane postaci są z innego dnia. W niedzielę ponów Skanuj moje i przygotowanie bonusów wojennych."
        }
    }
    fun endSession() { dialogs.resetOpponentSession();hide() }
    fun hide() {
        val borrowed=borrowedPanel;batchComplete=null;borrowedPanel=false;batchDragging=null;batchResume=null;batchCheckpoint=null;batchNavigation=null;batchLastCheckpoint=null;batchLayout=null;batchExpectedName=null;batchIdentityVerified=false
        capturedText.cancel();skillGate.cancel();afterHeader=null;clearProfileSnapshot()
        dialogs.hide();dialogs.clearOpponentDraft()
        idleRows.clear();scanButtons.clear();repeatButtons.clear()
        listPrompt?.dismiss(); listPrompt=null
        generation++; active=false; session=null
        selection?.close(); selection=null
        releaseSelectionFrame()
        GestureConnection.cancel(); shield.hide()
        GestureConnection.beforeGesture=null; GestureConnection.afterGesture=null; GestureConnection.inputSettleMillis=null
        if(!borrowed)panel?.let { runCatching { wm.removeViewImmediate(it) } }; panel=null
        CaptureConnection.removeStateListener(captureListener)
    }
    private fun fail(message: String) {
        if(batchComplete!=null){
            val error=if(batchIdentityVerified)BatchScanException(message,draftSnapshot()) else IllegalStateException(message)
            saveBatch();deliverBatch(Result.failure(error));return
        }
        capturedText.cancel();skillGate.cancel();afterHeader=null;clearProfileSnapshot()
        ownHomeKnown=false
        failureMessage=message
        compact=false
        dialogs.hide()
        listPrompt?.dismiss(); listPrompt=null
        generation++; active=false; session=null; selection?.close(); selection=null
        releaseSelectionFrame()
        GestureConnection.cancel(); shield.hide()
        GestureConnection.beforeGesture=null; GestureConnection.afterGesture=null; GestureConnection.inputSettleMillis=null
        panel?.visibility=android.view.View.VISIBLE; status?.text=message;refreshChrome()
    }
    private fun recoverRead(message:String) {
        if(!active || setup || scanRecoveries>=2 || CaptureConnection.state!=CaptureState.READY || !GestureConnection.isConnected) { fail(message);return }
        if(side=="opponent" && !opponentReprepared) recheckOpponent=true
        capturedText.cancel();skillGate.cancel();afterHeader=null;clearProfileSnapshot()
        scanRecoveries++;generation++;session=null
        GestureConnection.cancel();shield.hide()
        GestureConnection.beforeGesture=null;GestureConnection.afterGesture=null;GestureConnection.inputSettleMillis=null
        panel?.visibility=android.view.View.VISIBLE
        status?.text="Niepełny odczyt — resetuję skan i ponawiam ${scanRecoveries}/2. Nie dotykaj gry."
        runtimeKey="_prepare";refreshChrome()
        later(900) { beginSide(side,false,true) }
    }
    private fun ready(which:String):Boolean {
        val source=if(which=="opponent" && !prefs.contains("battle_opponent")) "self" else which
        return runCatching {
            val o=JSONObject(prefs.getString("battle_$source",null) ?: return false)
            o.optInt("width")>0 && o.optInt("height")>0 && listOf("name","header","bonuses","weapon","title","skill1","skill2","skill3","fairy").all { o.has(it) } &&
                (source!="self" || (o.has("open") && o.has("close")))
        }.getOrDefault(false)
    }
    private var failureMessage:String?=null
    private fun updateIndicator() {(context.applicationContext as eu.komars.forgescanner.ScannerApplication).setScannerBusy("battle",active && selection==null)}
    private fun refreshChrome() {
        if(borrowedPanel)return
        val scanning=active && !setup
        idleRows.forEach { it.visibility=if(active || compact) android.view.View.GONE else android.view.View.VISIBLE }
        setupRow?.visibility=if(!active && !compact && !isConfigured()) android.view.View.VISIBLE else android.view.View.GONE
        if(!active && !isConfigured()) { idleRows.take(1).forEach { it.visibility=android.view.View.GONE } }
        runRow?.visibility=if(scanning) android.view.View.VISIBLE else android.view.View.GONE
        warning?.visibility=if(scanning && !compact) android.view.View.VISIBLE else android.view.View.GONE
        scanButtons.forEach { (button,which) ->
            button.isEnabled=!active && ready(which)
            button.visibility=if(isConfigured()) android.view.View.VISIBLE else android.view.View.GONE
        }
        setupIcon?.visibility=if(!isConfigured()) android.view.View.VISIBLE else android.view.View.GONE
        compactRow?.visibility=if(compact) android.view.View.VISIBLE else android.view.View.GONE
        compactRow?.let {row ->for(i in 0 until row.childCount) {val child=row.getChildAt(i);child.layoutParams=LinearLayout.LayoutParams(if(child is Button)compactCell() else dp(24),compactCell())}}
        expandButton?.isEnabled=!active
        repeatButtons.forEach { it.isEnabled=!active && dialogs.canRepeat();it.visibility=if(isConfigured() && dialogs.canRepeat()) android.view.View.VISIBLE else android.view.View.GONE }
        status?.visibility=if(compact) android.view.View.GONE else android.view.View.VISIBLE
        stageLabel?.visibility=if(compact && !active) android.view.View.GONE else android.view.View.VISIBLE
        stageLabel?.textSize=if(compact) 12f else 14f
        (panel?.layoutParams as? WindowManager.LayoutParams)?.let { lp ->
            val target=panelWidth()
            if(lp.width!=target) { lp.width=target;lp.x=lp.x.coerceIn(0,(context.resources.displayMetrics.widthPixels-target).coerceAtLeast(0));runCatching { wm.updateViewLayout(panel,lp) } }
        }
        val keys=if(side=="self") listOf("open","name","header","bonuses","weapon","title","skills","close") else listOf("name","header","bonuses","weapon","title","skills")
        val stage=keys.indexOf(runtimeKey)
        val n=if(setup) index else stage.coerceAtLeast(0)
        val colors=intArrayOf(0xFFFFCF66.toInt(),0xFF67D8F4.toInt(),0xFF84AFFF.toInt(),0xFFBDA0FF.toInt(),0xFF5CE1BD.toInt(),0xFFFF91A6.toInt(),0xFFFFAA67.toInt(),0xFF969DFF.toInt())
        val color=colors[n.coerceIn(0,7)]
        stageLabel?.text=if(compact && scanning) "▶ ${if(stage>=0) "${stage+1}/${keys.size}" else "…"} · NIE DOTYKAJ" else if(setup && active && index<steps.size) "Ustaw ${index+1}/${steps.size}: ${labels[steps[index]]}" else if(setup && active) "Konfiguracja gotowa" else if(scanning && stage>=0) "▶ Krok ${n+1}/${keys.size}: ${labels[runtimeKey]}" else if(scanning) "▶ Przygotowanie skanu" else "⌖ Symulator walki"
        stageLabel?.setTextColor(color)
        (panel?.background as? android.graphics.drawable.GradientDrawable)?.setStroke(dp(2),color)
        (setupRow?.getChildAt(0) as? Button)?.text=if(partialSetup("self") || ready("self")) "Kontynuuj" else "Ustaw pole"
        if(!active && !isConfigured() && failureMessage==null) status?.text=if(partialSetup("self") || ready("self")) "Konfiguracja przerwana. Wybierz Kontynuuj — wrócę do brakującego pola." else "Wybierz Ustaw pole. Skaner przygotuje odpowiedni ekran gry."
    }
    private fun returnToApp() {
        context.startActivity(android.content.Intent(context,BattleActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP))
        (context.applicationContext as eu.komars.forgescanner.ScannerApplication).closeScanner()
    }
    private fun compactCount()=if(!isConfigured()) 3 else if(dialogs.canRepeat()) 5 else 4
    private fun compactCell()=minOf(dp(44),(context.resources.displayMetrics.widthPixels-dp(16)-dp(12)*2-dp(24))/compactCount()).coerceAtLeast(1)
    private fun panelWidth()=if(borrowedPanel)(panel?.layoutParams as? WindowManager.LayoutParams)?.width?:dp(250) else minOf(if(compact) dp(12)*2+dp(24)+compactCell()*compactCount() else dp(282),context.resources.displayMetrics.widthPixels-dp(16))
    private fun placeAway(region:Rect):Boolean {
        val box=panel ?: return false
        refreshChrome()
        box.measure(android.view.View.MeasureSpec.makeMeasureSpec(panelWidth(),android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(0,android.view.View.MeasureSpec.UNSPECIFIED))
        val current=box.layoutParams as WindowManager.LayoutParams
        if(box.height>0 && !Rect.intersects(Rect(current.x,current.y,current.x+box.width,current.y+box.height),region)) {box.visibility=android.view.View.VISIBLE;return true}
        val y=eu.komars.forgescanner.model.OverlayPlacement.find(height,region.top,region.bottom,maxOf(box.measuredHeight,box.height),0,dp(20),maxOf(dp(8),32))
        if(y==null) { fail("Brak miejsca na STOP poza odczytywanym polem.");return false }
        val params=box.layoutParams as WindowManager.LayoutParams;params.y=y;wm.updateViewLayout(box,params)
        box.visibility=android.view.View.VISIBLE
        return true
    }
    private fun gestureSpace(target:Rect,next:()->Unit,attempt:Int=0) {
        val protected=Rect(target)
        if(runtimeKey=="bonuses" && fields.has("bonuses")) protected.union(region("bonuses"))
        val padded=Rect((protected.left-24).coerceAtLeast(0),(protected.top-24).coerceAtLeast(0),(protected.right+24).coerceAtMost(width),(protected.bottom+24).coerceAtMost(height))
        if(!placeAway(padded)) return
        later(120) {
            val p=panel ?: return@later
            val location=IntArray(2);p.getLocationOnScreen(location)
            val actual=Rect(location[0],location[1],location[0]+p.width,location[1]+p.height)
            if(Rect.intersects(actual,padded)) {
                if(attempt<1) gestureSpace(target,next,attempt+1) else fail("Nie udało się ustawić STOP poza gestem. Popraw pole listy.")
            } else next()
        }
    }
    private fun later(delay: Long=500, action:()->Unit) {
        val token=generation; handler.postDelayed({ if(active && token==generation){if(batchDragging?.invoke()==true)later(80,action)else action()} },delay)
    }
    private fun releaseSelectionFrame() {
        val frame=selectionFrame; selectionFrame=null
        handler.postDelayed({ frame?.let { if(!it.isRecycled) it.recycle() } },250)
    }
    private fun frame(action:(Bitmap)->Unit) {
        val token=generation
        CaptureConnection.requestFreshFrame { bitmap,error ->
            if(token!=generation || !active) { bitmap?.recycle(); return@requestFreshFrame }
            if(bitmap==null) { recoverRead(error ?: "Brak obrazu"); return@requestFreshFrame }
            if(width!=0 && (bitmap.width!=width || bitmap.height!=height)) { bitmap.recycle(); fail("Rozmiar ekranu zmienił się. Ustaw pola ponownie."); return@requestFreshFrame }
            width=bitmap.width; height=bitmap.height; action(bitmap)
        }
    }
    private fun beginSide(which:String,forceSetup:Boolean=false,recovering:Boolean=false) {
        if(which=="self" && !forceSetup && ready("self") && !isConfigured()) {
            (context.applicationContext as eu.komars.forgescanner.ScannerApplication).warOverlay.start(false,true);return
        }
        if(!forceSetup && !ready(which)) { status?.text="Najpierw skonfiguruj pola w Menu.";return }
        if(!recovering) { scanRecoveries=0;recheckOpponent=false;opponentReprepared=false }
        failureMessage=null;ownHomeKnown=false
        dialogs.hide();side=which;generation++;active=true;setup=forceSetup;runtimeKey="_prepare";session=null;width=0;height=0;status?.text="Przygotowuję skan. Nie dotykaj gry.";refreshChrome()
        prepareScreen(forceSetup,0)
    }
    private fun prepareScreen(forceSetup:Boolean,attempt:Int,homeStable:Int=0) {
        panel?.visibility=android.view.View.INVISIBLE
        later(180) { frame { bitmap ->
            val found=detector.observe(bitmap)
            if(borrowedPanel&&found.profile){batchLayout=found;eu.komars.forgescanner.vision.CloseButtonDetector().detect(bitmap)?.let {batchNavigation?.invoke(Rect(it))}}
            val ownHome=side=="self" && BattleHomeProof.isHome(bitmap)
            if(eu.komars.forgescanner.BuildConfig.DEBUG && side=="self" && !ownHome && attempt==0) {
                runCatching {java.io.FileOutputStream(java.io.File(context.cacheDir,"home-rejected.png")).use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}}
            }
            val fallbackClose=if(side=="self") eu.komars.forgescanner.vision.CloseButtonDetector().detect(bitmap) else null
            panel?.visibility=android.view.View.VISIBLE
            if(found.profile && side=="self" && !ownHome) {
                val close=eu.komars.forgescanner.vision.CloseButtonDetector().detect(bitmap)
                bitmap.recycle()
                if(close==null || attempt>=2) { fail("Wróć do głównego ekranu gry i wybierz Skanuj moje.");return@frame }
                fields.put("_home",encoded(close))
                arm { tap("_home") { prepareScreen(forceSetup,attempt+1) } }
            } else if(found.panel!=null && !found.profile && !ownHome) {
                val outside=WeaponDetailLayout.outsideArea(found.panel,width,height)
                bitmap.recycle()
                if(outside==null || attempt>=2 || found.panel.height>width*.7) { fail("Zamknij okno szczegółów w grze i ponów skan.");return@frame }
                fields.put("back",encoded(Rect(outside.left,outside.top,outside.right,outside.bottom)))
                arm { tap("back") { prepareScreen(forceSetup,attempt+1) } }
            } else {
                bitmap.recycle()
                if(side=="self" && !ownHome) {
                    if(attempt>=4){fail("Nie potwierdzono głównego ekranu postaci. Skan nie został uruchomiony.");return@frame}
                    if(fallbackClose!=null) {
                        fields.put("_home",encoded(fallbackClose))
                        arm {tap("_home") {prepareScreen(forceSetup,attempt+1)}}
                    } else later(500) {prepareScreen(forceSetup,attempt+1)}
                    return@frame
                }
                if(side=="self")ownHomeKnown=true
                if(side=="opponent" && !found.profile) { fail("Otwórz profil przeciwnika w grze i wybierz Skanuj przeciwnika.");return@frame }
                if(side=="opponent" && !forceSetup && prefs.contains("battle_opponent")) {
                    val cached=runCatching { JSONObject(prefs.getString("battle_opponent","{}")!!) }.getOrNull()
                    if(!recheckOpponent && cached!=null && cached.optInt("width")==width && cached.optInt("height")==height && ready("opponent")) {
                        active=false;scan();return@frame
                    }
                    recheckOpponent=true
                }
                if(side=="opponent" && !forceSetup && !prefs.contains("battle_opponent")) {
                    fields=JSONObject(prefs.getString("battle_self","{}")!!).put("shared",true)
                }
                if(side=="opponent" && !forceSetup && (recheckOpponent || fields.optBoolean("shared") || runCatching { JSONObject(prefs.getString("battle_opponent","{}")!!).optBoolean("shared") }.getOrDefault(false))) {
                    adaptOpponent(0);return@frame
                }
                active=false
                if(forceSetup) configure() else scan()
            }
        } }
    }
    private fun adaptOpponent(at:Int) {
        val targets=listOf("name","header","bonuses","weapon","skill1","skill2","skill3","fairy")
        if(at==targets.size) {
            fields.put("width",width).put("height",height).put("shared",true)
            prefs.edit().putString("battle_opponent",fields.toString()).apply()
            recheckOpponent=false;opponentReprepared=true
            active=false;scan();return
        }
        status?.text="Przygotowuję pola przeciwnika ${at+1}/${targets.size}. Nie dotykaj gry."
        panel?.visibility=android.view.View.VISIBLE
        if(fields.has(targets[at]) && !placeAway(region(targets[at]))) return
        later(120) { frame { bitmap ->
            val token=generation
            detector.detect(bitmap,targets[at]) { proposal ->
                bitmap.recycle()
                if(!active || token!=generation) return@detect
                panel?.visibility=android.view.View.VISIBLE
                status?.text="Przygotowuję pola przeciwnika ${at+1}/${targets.size}. Nie dotykaj gry."
                val fallback=if(fields.has(targets[at])) region(targets[at]).takeIf { Rect(0,0,width,height).contains(it) && !it.isEmpty } else null
                val target=proposal?.region ?: fallback
                if(target==null) { fail("Nie znaleziono pola ${targets[at]}. Popraw to pole w Menu.");return@detect }
                fields.put(targets[at],encoded(target));later(180) { adaptOpponent(at+1) }
            }
        } }
    }
    private fun encoded(rect:Rect)=JSONObject().put("l",rect.left).put("t",rect.top).put("r",rect.right).put("b",rect.bottom)
    private fun dismissWeapon(next:()->Unit) {
        val token=generation
        panel?.visibility=android.view.View.INVISIBLE
        later(180) { frame { bitmap ->
            detector.detect(bitmap,"back") { proposal ->
                bitmap.recycle()
                if(!active || token!=generation) return@detect
                panel?.visibility=android.view.View.VISIBLE
                if(proposal==null) { recoverRead("Nie rozpoznano granic dymku. Zamknij go w grze i ponów skan.");return@detect }
                fields.put("back",encoded(proposal.region))
                arm { tap("back",next) }
            }
        } }
    }
    private fun configure() {
        val checkpoint=runCatching { JSONObject(prefs.getString("battle_setup_$side",null) ?: "{}") }.getOrDefault(JSONObject())
        val compatible=checkpoint.optInt("width")==width && checkpoint.optInt("height")==height
        val missing=steps.indexOfFirst { !checkpoint.has(it) }
        val resume=!ready(side) && compatible && missing>0
        active=true;setup=true;generation++;session=null
        fields=if(resume) checkpoint else JSONObject()
        index=if(resume) missing else 0
        if(!resume) prefs.edit().remove("battle_setup_$side").remove("battle_$side").apply()
        status?.text=if(resume) "Kontynuuję od pola ${index+1}/${steps.size}." else "Konfiguracja: zatwierdzaj proponowane pola lub popraw ramkę."
        refreshChrome()
        // prepareScreen returns to the forge; replay only navigation needed by the missing field.
        if(resume && side=="self" && index>steps.indexOf("open")) {
            arm { tap("open") {
                if(index>steps.indexOf("weapon") && index<=steps.indexOf("title")) tap("weapon") {choose()} else choose()
            }}
        } else if(resume && index>steps.indexOf("weapon") && index<=steps.indexOf("title")) {
            arm { tap("weapon") {choose()} }
        } else choose()
    }
    private fun choose() {
        refreshChrome()
        if(index>=steps.size) {
            fields.put("width",width).put("height",height)
            prefs.edit().putString("battle_$side",fields.toString()).remove("battle_setup_$side").apply()
            active=false; setup=false
            status?.text="Pola postaci zapisane. Najpierw konfiguruję badania i wojnę — nie dotykaj ekranu."
            if(side=="self") (context.applicationContext as eu.komars.forgescanner.ScannerApplication).warOverlay.start(false,true) else scan()
            return
        }
        if(steps[index] in profileKeys) {chooseProfileFields();return}
        panel?.visibility=android.view.View.INVISIBLE
        later(200) { frame { bitmap ->
            val key=steps[index]
            val token=generation
            selectionFrame=bitmap
            detector.detect(bitmap,key) { proposal ->
                if(!active || token!=generation || selectionFrame!==bitmap) return@detect
                val suggestion=proposal?.region
                selection=ScreenSelectionWindow(context,bitmap,false,{ rect,_,_ ->
                    selection=null
                    releaseSelectionFrame()
                    if(!active || token!=generation) return@ScreenSelectionWindow
                    fields.put(key,JSONObject().put("l",rect.left).put("t",rect.top).put("r",rect.right).put("b",rect.bottom))
                    fields.put("width",width).put("height",height)
                    prefs.edit().putString("battle_setup_$side",fields.toString()).apply()
                    panel?.visibility=android.view.View.VISIBLE
                    if(key=="title") {
                        dismissWeapon { if(side=="self")arm {tap("close"){index=steps.size;choose()}} else {index=steps.size;choose()} }
                    } else if(key in listOf("open","weapon","close")) {
                        arm { tap(key) { index++; choose() } }
                    } else { index++; choose() }
                },{ releaseSelectionFrame(); fail("Konfiguracja przerwana. Zatwierdzone pola zapisane — wybierz Kontynuuj.") },
                    titleText="${index+1}/${steps.size}: ${labels[key]}",
                    instructionText=when {
                        key=="back" -> "Zaznacz mały obszar POZA oknem broni. Zatwierdzenie kliknie w jego środek i zamknie szczegóły. Nie zaznaczaj czerwonego X profilu."
                        key=="title" -> "Zaznacz okno broni wraz z nazwą i całym wierszem obrażeń — oznaczenie (dystansowy) znajduje się przy obrażeniach. Nie obejmuj tekstu poza oknem broni."
                        suggestion!=null && key in listOf("open","weapon","close") -> "${proposal.reason} Zatwierdź lub popraw ramkę. Po zatwierdzeniu kliknę i przejdę do kolejnego ekranu."
                        suggestion!=null && key=="bonuses" -> "Czy ramka obejmuje tylko widoczne, ucięte pole przewijania bonusów? Nie obejmuj pustego miejsca pod nim. Popraw ją w razie potrzeby. Przyciski Bitwa, Zgłoś i Zablokuj muszą pozostać poza ramką."
                        suggestion!=null -> "${proposal.reason} Zatwierdź albo narysuj poprawną ramkę."
                        else -> "Nie znaleziono jednoznacznego pola. Zaznacz ${labels[key]}. Zatwierdzenie przycisku wykona jedno kliknięcie."
                    },initialSelection=suggestion,minimumSelectionSize=8)
                selection!!.show()
            }
        } }
    }
    private val profileKeys=listOf("name","header","bonuses","weapon","skill1","skill2","skill3","fairy","close")
    private fun chooseProfileFields() {
        panel?.visibility=android.view.View.INVISIBLE
        val token=generation
        later(180) {frame {bitmap ->
            selectionFrame=bitmap
            val keys=profileKeys.filter {it in steps}
            val proposals=linkedMapOf<String,Pair<String,Rect?>>()
            val layout=detector.observe(bitmap)
            fun propose(at:Int) {
                if(!active || token!=generation)return
                if(at<keys.size) {
                    val key=keys[at]
                    val geometric=layout.regions[key]?.let {Rect(it.left,it.top,it.right,it.bottom)}
                    if(key=="name" || key=="close") detector.detect(bitmap,key) {proposal ->
                        if(active && token==generation) {proposals[key]=(labels[key]?:key) to (if(fields.has(key))region(key)else proposal?.region);propose(at+1)}
                    } else {proposals[key]=(labels[key]?:key) to (if(fields.has(key))region(key)else geometric);propose(at+1)}
                    return
                }
                selection=ScreenSelectionWindow(context,bitmap,false,{_,_,_->},{releaseSelectionFrame();fail("Konfiguracja przerwana. Wybierz Kontynuuj.")},
                    titleText="Pola profilu postaci",instructionText="Wybierz pole po prawej i popraw jego ramkę. Każdy kolor oznacza inne pole. Zatwierdź cały ekran.",minimumSelectionSize=8,
                    fields=proposals,onFields={values ->
                        selection=null;releaseSelectionFrame()
                        if(active && token==generation) {
                            values.forEach {(key,r)->fields.put(key,encoded(r))}
                            fields.put("width",width).put("height",height)
                            prefs.edit().putString("battle_setup_$side",fields.toString()).apply()
                            panel?.visibility=android.view.View.VISIBLE
                            if(!fields.has("title")) {index=steps.indexOf("title");arm {tap("weapon"){choose()}}}
                            else if(side=="self")arm {tap("close"){index=steps.size;choose()}}
                            else {index=steps.size;choose()}
                        }
                    })
                selection!!.show()
            };propose(0)
        }}
    }
    private fun region(key:String):Rect {
        val o=fields.getJSONObject(key)
        return Rect(o.getInt("l"),o.getInt("t"),o.getInt("r"),o.getInt("b"))
    }
    private fun arm(action:()->Unit) {
        if(session!=null && !setup) { action(); return }
        val token=generation
        GestureConnection.requestArm(width,height) { result ->
            if(!active || token!=generation) return@requestArm
            session=result.getOrNull()
            if(session==null) fail(result.exceptionOrNull()?.message ?: "Włącz sterowanie gestami") else action()
        }
    }
    private fun guard():Boolean {
        val p=panel ?: return false
        val loc=IntArray(2); p.getLocationOnScreen(loc)
        val ok=shield.show(Rect(loc[0],loc[1],loc[0]+p.width,loc[1]+p.height))
        if(!ok) { fail("Nie można zabezpieczyć obszaru gestów."); return false }
        GestureConnection.beforeGesture={ shield.openGesture(it) }
        GestureConnection.afterGesture={ shield.closeGesture() }
        GestureConnection.inputSettleMillis={ shield.inputSettleMillis() }
        return true
    }
    private fun tap(key:String, next:()->Unit) {
        if(key in listOf("open","weapon","close")) runtimeKey=key
        else if(key.startsWith("skill")) runtimeKey="skills" else if(key!="back") runtimeKey="_prepare"
        refreshChrome()
        val r=region(key); val token=generation
        gestureSpace(r,{
            if(!guard()) return@gestureSpace
            GestureConnection.tap(requireNotNull(session),(if(key=="weapon") r.left+r.width()*.40f else r.exactCenterX()),(if(key=="weapon") r.top+r.height()*.72f else r.exactCenterY())) { result ->
                if(!active || token!=generation) return@tap
                shield.hide()
                if(result.isFailure) fail(result.exceptionOrNull()?.message ?: "Nie udało się kliknąć") else later { next() }
            }
        })
    }

    private fun scan() {
        if(!prefs.contains("battle_$side")&&!(borrowedPanel&&prefs.contains("battle_self"))) { fail("Najpierw ustaw pola w Menu.");return }
        fields=runCatching { JSONObject(prefs.getString(if(borrowedPanel&&!prefs.contains("battle_opponent"))"battle_self"else"battle_$side","{}")!!) }.getOrElse { fail("Ustaw pola ponownie"); return }
        width=fields.optInt("width"); height=fields.optInt("height")
        if(width<=0 || height<=0) { fail("Ustaw pola ponownie"); return }
        capturedText.cancel();skillGate.cancel();afterHeader=null;clearProfileSnapshot();headerPending=0;headerReadable=false
        generation++; active=true; setup=false; session=null
        fairyScan=JSONObject();name=""; damage=""; health=""; weapon=""; damageRaw="";healthRaw="";headerRaw="";skillScans=org.json.JSONArray();skillEvidence.clear();texts.clear(); previous=""; repeats=0; passes=0;listMoved=false;listAttempt=0;seenBonusKeys.clear()
        status?.text="Skanuję ${if(side=="self") "moją postać" else "przeciwnika"}. Nie dotykaj gry."
        arm { if(side=="self") {if(ownHomeKnown)tap("open"){readHeader()}else confirmOwnHome {ownHomeKnown=true;tap("open"){readHeader()}}} else readHeader() }
    }
    private fun confirmOwnHome(stable:Int=0,next:()->Unit) {
        status?.text="Potwierdzam główny ekran postaci przed odczytem. Nie dotykaj gry."
        panel?.visibility=android.view.View.INVISIBLE
        later(180) {frame {image ->
            val valid=BattleHomeProof.isHome(image);image.recycle();panel?.visibility=android.view.View.VISIBLE
            if(!valid){fail("Nie potwierdzono głównego ekranu postaci. Nie rozpoczęto odczytu.");return@frame}
            next()
        }}
    }
    private fun read(key:String, scale:Int=2, contrast:Int=0, next:(String)->Unit) {
        runtimeKey=if(key.startsWith("_skill")) "skills" else if(key.startsWith("_fairy")) "fairy" else key;refreshChrome()
        val fullFairy=key=="_fairyDetails" && region(key).width()>=width*.9 && region(key).height()>=height*.9
        if(fullFairy)panel?.visibility=android.view.View.INVISIBLE else if(!placeAway(region(key))) return
        later(180) { frame { bitmap ->
            val r=region(key)
            if(!Rect(0,0,bitmap.width,bitmap.height).contains(r) || r.isEmpty) { bitmap.recycle(); fail("Pole poza ekranem. Ustaw pola ponownie."); return@frame }
            val crop=Bitmap.createBitmap(bitmap,r.left,r.top,r.width(),r.height())
            if(key=="_skillText" && contrast==0 && eu.komars.forgescanner.BuildConfig.DEBUG)runCatching {
                java.io.FileOutputStream(java.io.File(context.cacheDir,"skill-latest.png")).use {crop.compress(Bitmap.CompressFormat.PNG,100,it)}
            }
            var scaled=Bitmap.createScaledBitmap(crop,crop.width*scale,crop.height*scale,true)
            if(crop!==scaled) crop.recycle(); if(bitmap!==crop) bitmap.recycle()
            if(contrast>0) {
                val pixels=IntArray(scaled.width*scaled.height);scaled.getPixels(pixels,0,scaled.width,0,0,scaled.width,scaled.height)
                for(i in pixels.indices) {val c=pixels[i];val v=((c ushr 16 and 255)*30+(c ushr 8 and 255)*59+(c and 255)*11)/100
                    val light=when(contrast){1->v>=145;2->v>=195;else->v>=95}
                    pixels[i]=if(light) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                };val transformed=Bitmap.createBitmap(pixels,scaled.width,scaled.height,Bitmap.Config.ARGB_8888);scaled.recycle();scaled=transformed
            }
            val token=generation
            recognizer.process(InputImage.fromBitmap(scaled,0)).addOnSuccessListener { result ->
                if(active && token==generation) {
                    panel?.visibility=android.view.View.VISIBLE
                    next(BattleOcrRows.text(result.textBlocks.flatMap { it.lines }.mapNotNull { line ->
                        line.boundingBox?.let { q -> BattleOcrLine(line.text,q.left,q.top,q.right,q.bottom) }
                    }))
                }
            }.addOnFailureListener { if(active && token==generation) recoverRead("Nie udało się odczytać tekstu. Ponów skan.") }
                .addOnCompleteListener { scaled.recycle() }
        } }
    }
    private fun readHeader() {
        if(!fields.has("header")){
            read("name"){name=BattleText.playerName(it);verifyBatchName {prepareTotals()}}
            return
        }
        var nameBox=region("name");val headerBox=region("header")
        if(borrowedPanel)batchLayout?.regions?.get("name")?.let {q->nameBox=Rect(q.left,q.top,q.right,q.bottom)}
        val union=Rect(nameBox).apply {union(headerBox)}
        if(borrowedPanel)batchLayout?.regions?.filterKeys {it in listOf("mount","pet1","pet2","pet3","fairy","weapon","skill1","skill2","skill3")}?.values?.forEach {union.union(Rect(it.left,it.top,it.right,it.bottom+it.height/2))}
        if(!placeAway(union))return
        val token=generation
        later(180){frame {image ->
            if(borrowedPanel&&nameBox.width()>0) {
                val p=IntArray(nameBox.width()*nameBox.height());image.getPixels(p,0,nameBox.width(),nameBox.left,nameBox.top,nameBox.width(),nameBox.height())
                nameBox.right=nameBox.left+PlayerNameRegion.rightEdge(nameBox.width(),nameBox.height(),p)
            }
            val areas=listOf(nameBox,headerBox)
            if(areas.any {it.isEmpty || !Rect(0,0,image.width,image.height).contains(it)}) {
                image.recycle();fail("Pole poza ekranem. Popraw pola profilu.");return@frame
            }
            val crops=areas.map {r ->
                val crop=Bitmap.createBitmap(image,r.left,r.top,r.width(),r.height())
                Bitmap.createScaledBitmap(crop,crop.width*3,crop.height*3,true).also {if(it!==crop)crop.recycle()}
            }
            snapshotProfile(image)
            if(borrowedPanel)fields.put("_batchName",encoded(nameBox))
            image.recycle()
            val results=arrayOfNulls<String>(2);headerPending=2
            crops.forEachIndexed {i,crop ->
                recognizer.process(InputImage.fromBitmap(crop,0)).addOnSuccessListener {result ->
                    if(active && token==generation)results[i]=BattleOcrRows.text(result.textBlocks.flatMap {it.lines}.mapNotNull {line->line.boundingBox?.let {q->BattleOcrLine(line.text,q.left,q.top,q.right,q.bottom)}})
                }.addOnCompleteListener {
                    crop.recycle()
                    if(active && token==generation) {
                        headerPending--
                        if(headerPending==0) {
                            name=BattleText.playerName(results[0].orEmpty());headerRaw=results[1].orEmpty()
                            val totals=BattleHeaderText.parse(headerRaw)
                            headerReadable=totals.damage!=null && totals.health!=null
                            damage=totals.damage.orEmpty();health=totals.health.orEmpty();damageRaw=headerRaw;healthRaw=headerRaw
                            verifyBatchName {
                                saveBatch("header")
                                if(borrowedPanel)rewindList() else {val resume=afterHeader;afterHeader=null;resume?.invoke()}
                            }
                        }
                    }
                }
            }
            if(!borrowedPanel)rewindList()
        }}
    }
    private fun verifyBatchName(next:()->Unit) {
        val expected=batchExpectedName
        if(expected==null){next();return}
        fun matches()=BattleText.normalize(name)==BattleText.normalize(expected)
        fun accept(){batchIdentityVerified=true;next()}
        if(matches()){accept();return}
        if(!fields.has("_batchName")){fail("Nie potwierdzono nicku $expected (OCR: $name).");return}
        read("_batchName",4,contrast=1) {raw ->
            name=BattleText.playerName(raw)
            if(matches())accept() else fail("Nie potwierdzono nicku $expected (OCR: $name).")
        }
    }
    private fun clearProfileSnapshot() {
        quickSkills.clear();quickSkillPending=0;afterQuickSkills=null
        fairySnapshot?.let {if(!it.isRecycled)it.recycle()};fairySnapshot=null;fairyGlyph=null;fairyHint=null;fairyAbsent=false;batchPets=null;batchMounted=null
    }
    private fun emptyProfileSlot(image:Bitmap,layout:BattleSetupGeometry.Layout,key:String,r:Rect):Boolean {
        val panel=layout.panel?:return false
        val expected=layout.regions[key]?:return false
        if(!layout.profile || r.isEmpty || !Rect(0,0,image.width,image.height).contains(r) ||
            r.centerX() !in expected.left..expected.right || r.centerY() !in expected.top..expected.bottom ||
            r.width()<expected.width/2 || r.width()>expected.width*2)return false
        val background=image.getPixel((panel.left+maxOf(3,panel.width/50)).coerceIn(0,image.width-1),r.centerY())
        val crop=IntArray(r.width()*r.height());image.getPixels(crop,0,r.width(),r.left,r.top,r.width(),r.height())
        return EquipmentSlotPresence.isEmpty(r.width(),r.height(),crop,eu.komars.forgescanner.model.PixelRegion(0,0,r.width(),r.height()),background)
    }
    private fun snapshotProfile(image:Bitmap) {
        if(borrowedPanel) {
            val layout=batchLayout?:detector.observe(image);val p=layout.panel
            if(layout.profile&&p!=null) {
                val bg=image.getPixel((p.left+maxOf(3,p.width/50)).coerceIn(0,image.width-1),(p.top+p.height/2).coerceIn(0,image.height-1))
                fun presence(key:String):Boolean? {val q=layout.regions[key]?:return null;if(q.left<0||q.top<0||q.right>image.width||q.bottom>image.height)return null
                    val pixels=IntArray(q.width*q.height);image.getPixels(pixels,0,q.width,q.left,q.top,q.width,q.height);return ProfileSlotPresence.occupied(q.width,q.height,pixels,bg)}
                val pets=(1..3).map {presence("pet$it")};batchPets=if(pets.all {it!=null})pets.count {it==true}else null
                batchMounted=presence("mount")
                captureQuickSkills(image,layout)
            }
        }
        val bounds=Rect(0,0,image.width,image.height)
        if(fields.has("fairy")) {
            val r=region("fairy")
            fairyAbsent=emptyProfileSlot(image,detector.observe(image),"fairy",r)
            if(!r.isEmpty && bounds.contains(r)) {
                fairySnapshot?.recycle()
                val captured=Bitmap.createBitmap(image,r.left,r.top,r.width(),r.height())
                fairyGlyph=fairyMemory.capture(captured,Rect(0,0,captured.width,captured.height))
                fairySnapshot=if(captured===image)captured.copy(Bitmap.Config.ARGB_8888,false)else captured
                var yellow=0;var red=0;var purple=0
                for(y in r.top until r.top+r.height()*2/3)for(x in r.left until r.right) {
                    val c=image.getPixel(x,y);val rr=Color.red(c);val g=Color.green(c);val b=Color.blue(c)
                    if(rr>170 && g>130 && b<100)yellow++
                    if(rr>150 && g<115 && b<145)red++
                    if(rr>80 && b>100 && b>g*1.3 && rr>g*1.2)purple++
                }
                fairyHint=when {yellow>15->"TIRA";red>8->"LORA";purple>8->"MIRA";else->null}
                fields.put("_fairy",encoded(r))
            }
        }
    }
    private fun captureQuickSkills(image:Bitmap,layout:BattleSetupGeometry.Layout) {
        val bounds=Rect(0,0,image.width,image.height)
        for(at in 0..2) {
            val key="skill${at+1}"
            val q=layout.regions[key]?:continue
            val icon=Rect(q.left,q.top,q.right,q.bottom)
            if(!bounds.contains(icon))continue
            if(emptyProfileSlot(image,layout,key,icon)){
                quickSkills[at]=JSONObject().put("empty",true)
                continue
            }
            val match=visualMemory.wikiMatch(image,icon)?.takeIf {it.error<=42 && it.margin>=6}?:continue
            val label=Rect(q.left-q.width/10,q.bottom-q.height/6,q.right+q.width/10,q.bottom+q.height/2)
            if(!bounds.contains(label))continue
            val crop=Bitmap.createBitmap(image,label.left,label.top,label.width(),label.height())
            val levels=mutableListOf<Int>()
            quickSkillPending++
            capturedText.read(crop,{raw ->
                SkillLevelText.parse(raw)?.let(levels::add)
                levels.size>=2&&levels.distinct().size==1
            }) {
                val level=levels.distinct().singleOrNull()
                if(level!=null&&levels.size>=2)quickSkills[at]=JSONObject().put("type",match.id).put("level",level).put("fromProfile",true)
                quickSkillPending--
                if(quickSkillPending==0){val resume=afterQuickSkills;afterQuickSkills=null;resume?.invoke()}
            }
        }
    }
    private fun prepareTotals() {
        if(fields.has("header")) { readTotals(0);return }
        panel?.visibility=android.view.View.INVISIBLE
        later(150) { frame { bitmap ->
            val token=generation
            detector.detect(bitmap,"header") { proposal ->
                bitmap.recycle()
                if(active && token==generation) {
                    if(proposal!=null) fields.put("header",encoded(proposal.region))
                    readTotals(0)
                }
            }
        } }
    }
    private fun readTotals(attempt:Int,next:()->Unit={rewindList()}) {
        if(attempt==2) {
            val q=region("header");val padding=maxOf(8,width/40)
            fields.put("header",encoded(Rect((q.left-padding).coerceAtLeast(0),(q.top-padding).coerceAtLeast(0),(q.right+padding).coerceAtMost(width),(q.bottom+padding).coerceAtMost(height))))
        }
        read("header",if(attempt==0) 3 else 4) { text ->
            headerRaw=text
            val totals=BattleHeaderText.parse(text)
            if((totals.damage==null || totals.health==null) && attempt<2) {
                status?.text="Ponawiam odczyt obrażeń i zdrowia ${attempt+1}/2.";later(200) { readTotals(attempt+1,next) };return@read
            }
            if((totals.damage==null || totals.health==null) && side=="opponent" && !opponentReprepared) { recoverRead("Niepełny odczyt statystyk — ponawiam pola przeciwnika.");return@read }
            damage=totals.damage.orEmpty();health=totals.health.orEmpty()
            damageRaw=text;healthRaw=text;headerReadable=true
            next()
        }
    }
    private fun rewindList(remaining:Int=1) {
        if(borrowedPanel&&batchSections.optBoolean("bonuses")) {texts.add(batchResume?.optString("raw").orEmpty());readWeapon();return}
        status?.text="Przewijam bonusy na początek listy."
        runtimeKey="bonuses";refreshChrome()
        // The short list needs a fixed sweep, not OCR after each upward gesture.
        if(remaining==0) { later(200) { readList() };return }
        scrollList(true) { rewindList(remaining-1) }
    }
    private fun scrollList(toTop:Boolean,next:()->Unit) {
        val r=region("bonuses");val x=r.exactCenterX()
        val distance=minOf(r.height()*(if(toTop) .80f else .60f),height*.25f)
        val from=if(toTop) r.top+r.height()*.15f else r.bottom-r.height()*.15f
        val to=if(toTop) from+distance else from-distance
        val token=generation
        val corridor=Rect((x-20).toInt(),minOf(from,to).toInt()-20,kotlin.math.ceil(x.toDouble()).toInt()+21,kotlin.math.ceil(maxOf(from,to).toDouble()).toInt()+21)
        gestureSpace(corridor,{
            if(!guard()) return@gestureSpace
            GestureConnection.drag(requireNotNull(session),x,from,x,to,if(toTop) 650 else 320,false) { result ->
                if(active && token==generation) {
                    shield.hide()
                    if(result.isFailure) fail(result.exceptionOrNull()?.message ?: "Przewijanie przerwane") else later(if(toTop) 80 else 250,next)
                }
            }
        })
    }
    private fun readList() {
        status?.text="Odczytuję listę bonusów. Przewijanie jest automatyczne."
        read("bonuses") { text ->
            texts.add(text)
            val keys=BattleText.bonuses(text).keys
            if(passes>0 && keys.any { it !in seenBonusKeys }) listMoved=true
            seenBonusKeys.addAll(keys)
            val parsed=BattleText.bonuses(text)
            val signature=if(parsed.isNotEmpty()) parsed.entries.sortedBy { it.key.ordinal }.joinToString { "${it.key}:${it.value}" } else BattleText.normalize(text)
            if(signature.isBlank()) { retryList(); return@read }
            repeats=if(signature==previous) repeats+1 else 0
            previous=signature; passes++
            if(repeats>=1) { if(listMoved) readWeapon() else confirmUnmovedList(); return@read }
            if(passes>=8) { retryList();return@read }
            scrollList(false) { readList() }
        }
    }
    private fun retryList() {
        if(listAttempt>=1) { recoverRead("Nie udało się odczytać całej listy. Sprawdź pole bonusów. Poprzednie dane zachowane.");return }
        listAttempt++
        texts.clear();previous="";repeats=0;passes=0;listMoved=false;seenBonusKeys.clear()
        status?.text="Ponawiam listę od początku — wracam na górę."
        rewindList()
    }
    private fun confirmUnmovedList() {
        if(borrowedPanel){fail("Nie potwierdzono końca listy bonusów.");return}
        val token=generation
        status?.text="Lista nie przesunęła się. Sprawdź ramkę i koniec listy."
        val prompt=AlertDialog.Builder(android.view.ContextThemeWrapper(context,eu.komars.forgescanner.R.style.ScannerDialogTheme)).setTitle("Nie potwierdzono przewinięcia")
            .setMessage("Lista bonusów nie pokazała nowych pozycji. Ramka może wykraczać poza przewijane pole. Potwierdź pełną listę tylko wtedy, gdy widzisz wszystkie bonusy; w przeciwnym razie popraw pole.")
            .setPositiveButton("Widzę całą listę") { _,_ -> if(active && token==generation) { listPrompt=null;readWeapon() } }
            .setNegativeButton("Popraw pole") { _,_ -> if(active && token==generation) fail("Ustaw ponownie widoczny, ucięty obszar bonusów. Nie obejmuj pustego miejsca pod listą.") }
            .setOnCancelListener { if(active && token==generation) fail("Skan przerwany. Poprzednie dane zachowane.") }.create()
        listPrompt=prompt;prompt.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);prompt.show()
    }
    private fun readWeapon() {
        if(headerPending>0){afterHeader={readWeapon()};status?.text="Kończę analizę obrażeń i zdrowia z pierwszego obrazu.";return}
        if(!headerReadable){readTotals(0){readWeapon()};return}
        saveBatch("bonuses")
        if(borrowedPanel&&batchSections.optBoolean("weapon")&&BattleText.weapon(batchResume?.optString("weapon").orEmpty())!=null) {weapon=batchResume!!.getString("weapon");scanSkills(0);return}
        skillGate=CaptureAnalysisGate();weaponAttempts=0;weaponReadable=false;skillGate.beginCapture()
        captureWeapon {skillGate.finishCapture();completeWeaponRead()}
    }
    private fun captureWeapon(closed:()->Unit) {
        val token=generation;val gate=skillGate;weaponAttempts++
        status?.text="Zbieram okno broni. Analiza działa w tle."
        tap("weapon") {
            panel?.visibility=android.view.View.INVISIBLE
            later(120){frame {image ->
                val popup=detector.observe(image);val box=popup.panel
                if(popup.profile){image.recycle();closed();return@frame}
                if(box==null){image.recycle();fail("Nie rozpoznano granic okna broni.");return@frame}
                val r=Rect(box.left+3,box.top+3,box.right-3,box.bottom-3)
                val crop=Bitmap.createBitmap(image,r.left,r.top,r.width(),r.height());image.recycle()
                gate.submitAnalysis()
                capturedText.read(crop,{raw ->
                    raw.lines().any {line->val n=BattleText.normalize(line);(n.contains("obrazenia") || n.contains("damage")) && !line.contains('%')} && BattleText.weapon(raw)!=null
                },{result ->
                    if(active && token==generation) {
                        val raw=result.getOrDefault("")
                        weaponReadable=raw.lines().any {line->val n=BattleText.normalize(line);(n.contains("obrazenia") || n.contains("damage")) && !line.contains('%')} && BattleText.weapon(raw)!=null
                        weapon=if(weaponReadable)raw else ""
                        gate.finishAnalysis();completeWeaponRead()
                    }
                })
                val outside=WeaponDetailLayout.outsideArea(box,width,height)
                if(outside==null){fail("Nie znaleziono miejsca zamknięcia dymka broni.");return@frame}
                fields.put("back",encoded(Rect(outside.left,outside.top,outside.right,outside.bottom)))
                tap("back",closed)
            }}
        }
    }
    private var fairyScan=JSONObject()
    private var fairyLevels=FairyLevelEvidence()
    private fun scanFairy(attempt:Int=0) {
        saveBatch("skills")
        if(borrowedPanel&&attempt==0) {
            val saved=batchResume?.optJSONObject("fairy")
            if(saved!=null) {
                val rawLevel=SkillLevelText.parse(saved.optString("raw"));val detailLevel=SkillLevelText.parse(saved.optString("detailRaw"))
                val calculated=fairyProgressions.infer(saved.optString("type"),saved.optString("detailRaw"))
                if(!saved.optBoolean("levelConfirmed")&&calculated!=null&&(rawLevel==calculated.level||detailLevel==calculated.level))saved.put("level",calculated.level).put("levelSource","progression").put("levelCalculation",fairyProgressions.evidence(calculated))
                val type=saved.optString("type")
                val bonus=if(type=="MIRA")Bonus.CRIT_REDUCTION else Bonus.FIFTH_ATTACK
                val effect=saved.optDouble(if(type=="MIRA")"criticalReduction"else"fifthBonus",batchResume?.optJSONObject("bonuses")?.optDouble(bonus.name,Double.NaN)?:Double.NaN)
                val ambiguous=saved.optInt("level") in setOf(3,5,8)
                val trusted=saved.optBoolean("levelConfirmed")||saved.optString("levelSource") in setOf("effect","glyph","manual","progression")||saved.optBoolean("levelByEffect")
                if((!ambiguous||trusted)&&CachedFairyPolicy.reusable(saved.has("present")&&!saved.optBoolean("present"),type,saved.optInt("level"),effect)) {
                    fairyScan=JSONObject(saved.toString());finish();return
                }
            }
        }
        if(attempt==0){fairyLevels=FairyLevelEvidence();fairyProgressionEvidence=FairyProgressionEvidence()}
        if(fairyAbsent){fairySnapshot?.recycle();fairySnapshot=null;finishWithoutFairy();return}
        if(attempt==0 && fairySnapshot!=null) {
            val snapshot=fairySnapshot!!;fairySnapshot=null;val token=generation;var passes=0
            fairyScan=JSONObject();fairyHint?.let {fairyScan.put("type",it)}
            status?.text="Analizuję wróżkę z pierwszego obrazu profilu."
            capturedText.read(snapshot,{raw ->
                passes++;val level=SkillLevelText.parse(raw)?.takeIf {it in 1..20}
                fairyLevels.add(level,false);fairyScan.put("raw",raw)
                passes>=2 && level!=null
            },{if(active && token==generation)readFairyDetails()})
            return
        }
        status?.text="Odczytuję wróżkę, poziom i efekt. Nie dotykaj gry."
        panel?.visibility=android.view.View.INVISIBLE
        later(120) {frame {bitmap ->
            val found=detector.observe(bitmap);val q=if(fields.has("fairy"))region("fairy").let {eu.komars.forgescanner.model.PixelRegion(it.left,it.top,it.right,it.bottom)} else found.regions["fairy"]
            if(!found.profile || q==null){bitmap.recycle();recoverRead("Nie rozpoznano miejsca wróżki w profilu.");return@frame}
            fields.put("_fairy",encoded(Rect(q.left,q.top,q.right,q.bottom)))
            var yellow=0;var red=0;var purple=0
            for(y in q.top until q.top+(q.bottom-q.top)*2/3)for(x in q.left until q.right) {
                val c=bitmap.getPixel(x,y);val r=Color.red(c);val g=Color.green(c);val b=Color.blue(c)
                if(r>170 && g>130 && b<100)yellow++
                if(r>150 && g<115 && b<145)red++
                if(r>80 && b>100 && b>g*1.3 && r>g*1.2)purple++
            }
            fairyGlyph=fairyMemory.capture(bitmap,Rect(q.left,q.top,q.right,q.bottom))
            bitmap.recycle()
            val kind=when {yellow>15->"TIRA";red>8->"LORA";purple>8->"MIRA";else->null}
            read("_fairy",4,contrast=attempt) {text ->
                val level=SkillLevelText.parse(text)?.takeIf {it in 1..20}
                fairyLevels.add(level,false)
                if(attempt<1 || (level==null && attempt<2)){scanFairy(attempt+1);return@read}
                fairyScan=JSONObject().put("raw",text)
                if(kind!=null)fairyScan.put("type",kind)
                fairyScan.remove("level")
                readFairyDetails()
            }
        }}
    }
    private fun finishWithoutFairy() {
        fairyScan=JSONObject().put("present",false).put("level",0)
        if(side=="self")tap("close"){finish()}else finish()
    }
    private fun confirmFairyPresence() {
        if(borrowedPanel){fail("Nie potwierdzono obecności wróżki.");return}
        val token=generation
        val app=context.applicationContext as eu.komars.forgescanner.ScannerApplication
        app.setScannerBusy("battle",false);warning?.visibility=android.view.View.GONE
        status?.text="Nie otwarto okna wróżki. Potwierdź, czy jest wyposażona."
        dialogs.choose(listOf(
            "Brak wyposażonej wróżki" to {if(active && token==generation){app.setScannerBusy("battle",true);finishWithoutFairy()}},
            "Mam wróżkę — ponów odczyt" to {if(active && token==generation){app.setScannerBusy("battle",true);scanFairy()}},
            "Zakończ skan" to {if(active && token==generation)fail("Skan przerwany przy sprawdzaniu wróżki.")}
        ))
    }
    private fun readFairyDetails() {
        tap("_fairy") {
            panel?.visibility=android.view.View.INVISIBLE
            later(180) {frame {image ->
                val observed=detector.observe(image)
                if(observed.profile){image.recycle();confirmFairyPresence();return@frame}
                val close=eu.komars.forgescanner.vision.CloseButtonDetector().detect(image)
                val bounds=observed.panel
                val w=minOf(360,image.width);val hh=image.height*w/image.width
                val small=Bitmap.createScaledBitmap(image,w,hh,true);val colors=IntArray(w*hh)
                small.getPixels(colors,0,w,0,0,w,hh)
                val white=eu.komars.forgescanner.model.ResearchWindowGeometry.find(w,hh,colors)
                if(small!==image)small.recycle()
                if(borrowedPanel&&bounds==null&&white==null){image.recycle();fail("Nie potwierdzono granic opisu wróżki. Nie odczytuję całego ekranu.");return@frame}
                val textBox=if(bounds!=null)Rect(bounds.left,bounds.top,bounds.right,bounds.bottom)
                    else if(white!=null)Rect(white.left*image.width/w,white.top*image.height/hh,white.right*image.width/w,(white.top+(white.bottom-white.top)*.60).toInt()*image.height/hh)
                    else Rect(0,0,image.width,image.height)
                fields.put("_fairyDetails",encoded(textBox))
                fields.remove("_fairyClose")
                val compact=bounds!=null && bounds.width>bounds.height*1.5
                if(close!=null && !compact)fields.put("_fairyClose",encoded(close))
                image.recycle()
                fun detail(at:Int) {
                    read("_fairyDetails",2,contrast=at) {raw ->
                        val type=FairyText.type(raw)
                        if((type==null || (type=="LORA" && FairyText.firstPercent(raw)==null)) && at<2){detail(at+1);return@read}
                        if(type==null){recoverRead("Nie odczytano nazwy wróżki w szczegółach.");return@read}
                        fairyScan.put("type",type).put("detailRaw",raw)
                        fairyLevels.add(SkillLevelText.parse(raw),true)
                        fairyProgressionEvidence.add(fairyProgressions.infer(type,raw))
                        if(type=="LORA")fairyLevels.addLoraEffect(FairyText.firstPercent(raw),FairyText.current(raw))
                        if(at<1){detail(at+1);return@read}
                        if(type=="LORA")FairyText.firstPercent(raw)?.let {fairyScan.put("fifthBonus",it)}
                        if(type=="MIRA")FairyText.current(raw)?.takeIf {it in 0.0..70.0}?.let {fairyScan.put("criticalReduction",it)}
                        val done={if(side=="self")tap("close"){finish()}else finish()}
                        fun proceed() {if(fields.has("_fairyClose"))tap("_fairyClose",done)else dismissWeapon(done)}
                        val calculated=fairyProgressionEvidence.accepted()
                        val glyphLevel=fairyLevels.acceptedWithGlyph(fairyMemory.recognize(fairyGlyph))
                        val fallback=(glyphLevel?:fairyLevels.accepted())?.takeIf {fairyProgressionEvidence.agreesWith(it)}
                        if(calculated==null&&at<2&&(fallback==null||fairyProgressionEvidence.candidates.isNotEmpty())){status?.text="Sprawdzam współczynnik wróżki i zielony przyrost.";detail(at+1);return@read}
                        val accepted=calculated?.level?:fallback
                        if(accepted!=null){
                            fairyScan.put("level",accepted).put("levelConfirmed",false).put("levelSource",if(calculated!=null)"progression"else if(glyphLevel!=null)"glyph"else"views")
                            if(calculated!=null){fairyScan.put("levelCalculation",fairyProgressions.evidence(calculated));status?.text="Poziom $accepted wyliczony z bonusu i przyrostu.";fairyMemory.remember(accepted,fairyGlyph)}
                            proceed()
                        }
                        else {
                            saveBatch("fairyDetails");if(borrowedPanel)(context.applicationContext as eu.komars.forgescanner.ScannerApplication).setScannerBusy("war",false)
                            val token=generation
                            (context.applicationContext as eu.komars.forgescanner.ScannerApplication).setScannerBusy("battle",false)
                            warning?.visibility=android.view.View.GONE
                            dialogs.confirmFairyLevel(fairyLevels.candidates,calculated=fairyProgressionEvidence.candidates,preview=fairyMemory.preview(fairyGlyph),confirmed={level ->
                                if(active && token==generation) {
                                    fairyScan.put("level",level).put("levelConfirmed",true).put("levelSource","manual");fairyMemory.remember(level,fairyGlyph);if(borrowedPanel)(context.applicationContext as eu.komars.forgescanner.ScannerApplication).setScannerBusy("war",true)
                                    (context.applicationContext as eu.komars.forgescanner.ScannerApplication).setScannerBusy("battle",true)
                                    session=null;arm { proceed() }
                                }
                            },cancelled={if(active && token==generation)fail("Nie potwierdzono poziomu wróżki.")})
                        }
                    }
                }
                detail(0)
            }}
        }
    }
    private fun completeWeaponRead() {
        if(!active || !skillGate.ready)return
        if(!weaponReadable && weaponAttempts<2) {
            status?.text="Ponawiam tylko nieczytelne okno broni."
            skillGate.beginCapture();captureWeapon {skillGate.finishCapture();completeWeaponRead()}
        } else scanSkills(0)
    }
    private fun scanSkills(at:Int,attempt:Int=0) {
        if(weapon.isNotBlank())saveBatch("weapon")
        if(quickSkillPending>0){afterQuickSkills={scanSkills(at,attempt)};return}
        val quick=quickSkills[at]
        if(quick!=null){
            val duplicate=(0 until skillScans.length()).any {skillScans.optJSONObject(it)?.optString("type")==quick.optString("type") && !quick.optBoolean("empty")}
            if(!duplicate){skillScans.put(quick);scanSkills(at+1);return}
        }
        val saved=batchResume?.optJSONArray("skillScans")?.optJSONObject(at)
        if(borrowedPanel&&at<3&&saved!=null&&(saved.optBoolean("empty")||(runCatching {BattleSkill.valueOf(saved.optString("type"))}.isSuccess&&saved.optInt("level") in 1..100))) {
            skillScans.put(JSONObject(saved.toString()));scanSkills(at+1);return
        }
        if(at>=3) {scanFairy();return}
        runtimeKey="skills";refreshChrome();status?.text="Odczytuję umiejętność ${at+1}/3. Nie dotykaj gry."
        panel?.visibility=android.view.View.INVISIBLE
        later(180) { frame { bitmap ->
            val found=detector.observe(bitmap);val marked=if(fields.has("skill${at+1}")) region("skill${at+1}") else null
            val q=marked?.let { eu.komars.forgescanner.model.PixelRegion(it.left,it.top,it.right,it.bottom) } ?: found.regions["skill${at+1}"]
            val wikiKind=if(q!=null) visualMemory.fromWiki(bitmap,Rect(q.left,q.top,q.right,q.bottom)) else null
            val visual=if(q!=null) visualMemory.fingerprint(bitmap,Rect(q.left,q.top,q.right,q.bottom)) else null
            val empty=if(q!=null)emptyProfileSlot(bitmap,found,"skill${at+1}",Rect(q.left,q.top,q.right,q.bottom))else false
            bitmap.recycle()
            if(empty){skillScans.put(JSONObject().put("empty",true));scanSkills(at+1);return@frame}
            if(!found.profile || q==null) { recoverRead("Nie rozpoznano rzędu umiejętności. Wróć do profilu i ponów skan.");return@frame }
            fields.put("skill${at+1}",encoded(Rect(q.left,q.top,q.right,q.bottom)))
            tap("skill${at+1}") {
                panel?.visibility=android.view.View.INVISIBLE
                later(180) { frame { detail ->
                    val popup=detector.observe(detail)
                    if(popup.profile) {
                        detail.recycle()
                        if(attempt<2) { panel?.visibility=android.view.View.VISIBLE;status?.text="Ponawiam otwarcie umiejętności ${at+1}/3.";later(600) { scanSkills(at,attempt+1) };return@frame }
                        skillScans.put(JSONObject().put("raw","Nie otwarto szczegółów — wybierz umiejętność ręcznie"));scanSkills(at+1)
                    } else {
                        val box=popup.panel
                        if(box==null) {detail.recycle();recoverRead("Nie rozpoznano okna umiejętności. Zamknij szczegóły i ponów skan.");return@frame}
                        val textBox=Rect(box.left+3,box.top+3,box.right-3,box.bottom-3)
                        fields.put("_skillText",encoded(textBox))
                        val evidence=skillEvidence.getOrPut(at){SkillReadEvidence()}
                        evidence.addVisual(wikiKind)
                        fun addEvidence(text:String) {
                            val textKind=visualMemory.byName(text) ?: BattleSkill.recognize(text)
                            evidence.add(textKind,SkillLevelText.parse(text))
                            if(wikiKind==null)evidence.addVisual(visual?.let {visualMemory.recognize(it)})
                        }
                        fun saveSkill(text:String) {
                            val item=JSONObject().put("raw",text).put("visual",visual)
                            evidence.kind?.let {item.put("type",it.name)}
                            evidence.level?.let {item.put("level",it)}
                            skillScans.put(item)
                        }
                        if(borrowedPanel&&!textBox.isEmpty&&Rect(0,0,detail.width,detail.height).contains(textBox)){
                            val crop=Bitmap.createBitmap(detail,textBox.left,textBox.top,textBox.width(),textBox.height())
                            if(crop!==detail)detail.recycle()
                            val outside=WeaponDetailLayout.outsideArea(box,width,height)
                            fun close(next:()->Unit) {
                                if(outside==null){dismissWeapon(next);return}
                                fields.put("back",encoded(Rect(outside.left,outside.top,outside.right,outside.bottom)))
                                tap("back",next)
                            }
                            capturedText.read(crop,{raw ->
                                addEvidence(raw)
                                evidence.kind!=null&&evidence.level!=null
                            }) {result ->
                                if((evidence.kind==null||evidence.level==null)&&attempt<2)close {scanSkills(at,attempt+1)}
                                else {saveSkill(result.getOrDefault(""));close {scanSkills(at+1)}}
                            }
                            return@frame
                        }
                        detail.recycle()
                        fun readDetail(retry:Int) {
                            read("_skillText",if(retry==0) 3 else 4,contrast=retry) { text ->
                                addEvidence(text)
                                val kind=evidence.kind
                                val level=evidence.level
                                if((kind==null || level==null) && retry<3) {
                                    status?.text="Umiejętność ${at+1}/3: ponawiam nazwę i poziom, kontrast ${retry+1}/3."
                                    later(300) { readDetail(retry+1) };return@read
                                }
                                if((kind==null || level==null) && attempt<2) {
                                    status?.text="Ponownie otwieram umiejętność ${at+1}/3 — próba ${attempt+2}/3."
                                    dismissWeapon {scanSkills(at,attempt+1)};return@read
                                }
                                saveSkill(text)
                                dismissWeapon { scanSkills(at+1) }
                            }
                        }
                        readDetail(0)
                    }
                } }
            }
        } }
    }
    private fun dp(value:Int)=eu.komars.forgescanner.ui.ScannerAppearance.dp(context,value)
    private fun draftSnapshot():JSONObject {
        // Keep raw OCR and parsed values as an unreviewed draft. Existing accepted data is untouched.
        val raw=texts.joinToString("\n")
        val bonuses=JSONObject(); BattleText.completeBonuses(raw).forEach { (k,v) -> bonuses.put(k.name,v) }
        if(fairyScan.has("fifthBonus") && bonuses.optDouble(Bonus.FIFTH_ATTACK.name)==0.0)bonuses.put(Bonus.FIFTH_ATTACK.name,fairyScan.getDouble("fifthBonus"))
        if(fairyScan.has("criticalReduction") && bonuses.optDouble(Bonus.CRIT_REDUCTION.name)==0.0)bonuses.put(Bonus.CRIT_REDUCTION.name,fairyScan.getDouble("criticalReduction"))
        val draft=JSONObject().put("schema",2).put("name",name).put("damage",damage).put("health",health)
            .put("fairy",fairyScan).put("skillScans",skillScans).put("headerRaw",headerRaw).put("damageRaw",damageRaw).put("healthRaw",healthRaw).put("weapon",weapon).put("bonuses",bonuses).put("raw",raw).put("scannedAt",System.currentTimeMillis())
        if(borrowedPanel)draft.put("_sections",JSONObject(batchSections.toString()))
        if(batchPets!=null)draft.put("pets",batchPets)
        if(batchMounted!=null)draft.put("mounted",batchMounted)
        if(borrowedPanel)batchResume?.let {old ->
            for(key in listOf("name","damage","health","weapon","raw","headerRaw","damageRaw","healthRaw"))if(draft.optString(key).isBlank()&&old.optString(key).isNotBlank())draft.put(key,old.get(key))
            if(draft.optString("raw").isNotBlank()&&texts.isEmpty()&&old.has("bonuses"))draft.put("bonuses",old.get("bonuses"))
            if((draft.optJSONArray("skillScans")?.length()?:0)<(old.optJSONArray("skillScans")?.length()?:0))draft.put("skillScans",old.getJSONArray("skillScans"))
            if((draft.optJSONObject("fairy")?.length()?:0)==0&&old.has("fairy"))draft.put("fairy",old.getJSONObject("fairy"))
            for(key in listOf("pets","mounted"))if(!draft.has(key)&&old.has(key))draft.put(key,old.get(key))
        }
        return draft
    }
    private fun finish() {
        val draft=draftSnapshot()
        if(batchComplete!=null){saveBatch("finished");deliverBatch(Result.success(draft));return}
        if(side=="self") drafts.edit().putString("draft_self",draft.toString()).apply() else dialogs.setOpponentDraft(draft)
        generation++;active=false;session=null
        GestureConnection.cancel();shield.hide()
        panel?.visibility=android.view.View.VISIBLE
        status?.text="Skan gotowy. Sprawdź dane w oknie nad grą.";refreshChrome()
        if(side=="opponent") dialogs.acceptOpponent { status?.text="Przeciwnik odczytany. Wybierz rodzaj walki.";refreshChrome();dialogs.chooseBattle() }
        else dialogs.review(side) {
            status?.text="Moja postać zapisana. Otwórz przeciwnika w grze i wybierz Skanuj przeciwnika."
            val app=context.applicationContext as eu.komars.forgescanner.ScannerApplication
            if(app.mobile.paired && context.getSharedPreferences("war_preparation_v1",0).getLong("researchAt",0)==0L) app.warOverlay.start(false)
        }
    }
}

