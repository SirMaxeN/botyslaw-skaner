package eu.komars.forgescanner.battle

import android.content.Context
import android.graphics.*
import android.os.*
import android.view.*
import android.widget.*
import eu.komars.forgescanner.ScannerApplication
import eu.komars.forgescanner.capture.CaptureConnection
import eu.komars.forgescanner.capture.CaptureState
import eu.komars.forgescanner.automation.*
import eu.komars.forgescanner.ui.*
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.json.*
import java.time.Instant
import java.util.UUID

/** Guided first marking, then bounded automatic navigation. Never presses Attack or donation buttons. */
class WarPreparationOverlay(private val context:Context) {
 private val app get()=context.applicationContext as ScannerApplication
 private val wm=context.getSystemService(WindowManager::class.java)
 private val prefs=context.getSharedPreferences("scan_calibrations_v1",0)
 private val data=context.getSharedPreferences("war_preparation_v1",0)
 private val h=Handler(Looper.getMainLooper())
 private val analysisWorker=java.util.concurrent.Executors.newSingleThreadExecutor()
 private val navigation=AutoScanStore(context)
 private var stageLabel:TextView?=null;private var warning:TextView?=null;private var stage=0
 private val ocr=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
 private var panel:LinearLayout?=null;private var status:TextView?=null;private var selector:ScreenSelectionWindow?=null;private var selectionFrame:Bitmap?=null
 private var chrome:WarOverlayChrome?=null
 private var recovery:LinearLayout?=null
 private var protectedBeforeInteraction=false
 private val fullStore=FullWarStore(context)
 private val fullWorker=java.util.concurrent.Executors.newSingleThreadExecutor()
 private var fullCancel=java.util.concurrent.atomic.AtomicBoolean(false)
 private var fullMode=false;private var fullComputing=false
 private var fullResult:JSONObject?=null
 private var fullSide=false;private var fullTarget:String?=null
 private var fullProcessed=mutableSetOf<String>()
 private var fullProfileAnchor:MemberProfileAnchor?=null
 private val fullCentered=mutableSetOf<String>()
 private var fullRepairPass=false
 private var rankingMode=false
 private var fullAutoRetry=0
 private var paused=false
 /** Running without the hands-off banner and recovery buttons, so the panel fits beside the list. */
 private var slim=false
 /** Collapsed to icons by placement (never by choice): expanded again at the next step. */
 private var autoCompacted=false
 private fun setSlim(value:Boolean) {
  slim=value
  warning?.visibility=if(value||paused)View.GONE else if(active)View.VISIBLE else warning?.visibility?:View.GONE
  recovery?.visibility=if(value)View.GONE else View.VISIBLE
  status?.maxLines=if(value)2 else Int.MAX_VALUE
 }
 private fun restoreFullPanel() {
  if(slim)setSlim(false)
  if(autoCompacted){autoCompacted=false;chrome?.setCompact(false)}
 }
 /**
  * Pause keeps screen sharing and every saved profile, cancels pending taps and lets the user leave the
  * game. Resume re-detects the current screen and continues from the last checkpoint.
  */
 private fun setPaused(value:Boolean) {
  if(!active)return
  paused=value
  if(value) {
   generation++;fullCancel.set(true);GestureConnection.cancel();session=null;shield.hide();app.battleOverlay.cancelBatch()
   CaptureConnection.holdWhileHidden=true;app.setScannerBusy("war",false);warning?.visibility=View.GONE
   if(fullMode)saveFull("paused")
   fullStore.log("pause",JSONObject().put("target",fullTarget?:JSONObject.NULL))
   say("Pauza. Możesz korzystać z telefonu — udostępnianie ekranu trwa. Wróć do gry i naciśnij Wznów.")
  } else {
   CaptureConnection.holdWhileHidden=false;fullCancel=java.util.concurrent.atomic.AtomicBoolean(false)
   generation++;app.setScannerBusy("war",true);warning?.visibility=View.VISIBLE
   fullStore.log("resume-after-pause",JSONObject().put("target",fullTarget?:JSONObject.NULL))
   say("Wznawiam od zapisanego miejsca. Nie dotykaj gry.")
   if(fullMode&&fullStore.state()!=null)later(400){resumeFullWork()} else retryStep?.let {step->later(400){step()}}
  }
 }
 private val maxAutoRetry=3
 private var fullEnemySwept=false
 private var lastFullViewport:List<Pair<Int,Int>> = emptyList()
 private var stableFullViewport=0
 private fun fullId(enemy:Boolean,member:WarMember)="$enemy|${BattleText.normalize(member.name)}|${member.power}"
 private fun saveFull(phase:String,saveRoster:Boolean=false) {
  if(!fullMode||ownClan==null||ourExpected !in 1..50)return
  val state=JSONObject().put("phase",phase).put("enemy",fullSide).put("target",fullTarget?:JSONObject.NULL).put("own_clan",ownClan).put("enemy_clan",enemyClan?:JSONObject.NULL)
   .put("own_expected",ourExpected).put("enemy_expected",enemyExpected).put("width",width).put("height",height).put("own_damage",ownDamage).put("own_health",ownHealth).put("enemy_damage",enemyDamage).put("enemy_health",enemyHealth)
   .put("processed",JSONArray(fullProcessed.toList())).put("enemy_swept",fullEnemySwept).put("ranking",rankingMode)
  fullStore.checkpoint(state,ours.savedRows(),enemies.savedRows(),saveRoster)
 }
 private val fullEditor=BattleOverlayDialogs(context,resetSessionOnCreate=false)
 private val memberDetector=BattleFieldDetector(ocr)
 private var ownHeader:IntArray?=null
 private var ownClan:String?=null;private var enemyClan:String?=null
 private var ourExpected=0;private var enemyExpected=0
 private var uploadRequestId:String?=null
 private var active=false
  set(value) {field=value;app.setScannerBusy("war",value && selector==null && !fullComputing)}
 ;private var generation=0;private var width=0;private var height=0;private var session:GestureSession?=null
 private val proposals=mutableMapOf<String,Rect>()
 private val screenFlow=eu.komars.forgescanner.model.GameNavigationGraph.Cursor()
 private var researchReplayAllowed=true
 private var transitionEpoch=0
 private var retryStep:(()->Unit)?=null
 private var repairKey:String?=null
 private var pendingHealth=false
 private var preparingAll=false;private var rosterConfigured=false
 private var testMode=false
 private var listControl:Button?=null
 private var rosterMode=0
 private var rosterDownGain=1f
 private var manualListBoundary=false
 private var rosterEnd=RosterEndGate()
 private var rosterTop=RosterEndGate(1,6)
 private var listMode=false;private var scrolls=0;private var previous="";private var repeats=0;private var expected=0
 private val ours=RosterRegistry();private val enemies=RosterRegistry()
 private val rosterReview=RosterRepairDialog(context)
 private val rivalBonusDialog=RivalBonusDialog(context)
 private val rosterReader=RosterFrameReader(ocr,if(eu.komars.forgescanner.BuildConfig.DEBUG)context.cacheDir else null)
 private var ownDamage=0.0;private var ownHealth=0.0;private var enemyDamage=0.0;private var enemyHealth=0.0
 private val shield=AutomationTouchShield(context) { fail("Skan przerwany po dotknięciu ekranu.") }
 private val listener:(CaptureState,String?)->Unit={state,_ -> if(!fullComputing&&state!=CaptureState.READY && state!=CaptureState.STARTING) hide() }
 fun hide() {if(fullMode)runCatching {app.knowledge.syncAsync(force=true);flushShared()};serverAsked.clear();rosterRebuilt.clear();paused=false;CaptureConnection.holdWhileHidden=false;fullProfileAnchor=null;if(fullMode&&active)fullStore.log("stop",JSONObject().put("target",fullTarget?:JSONObject.NULL));fullCancel.set(true);fullEditor.hide();app.battleOverlay.cancelBatch();fullComputing=false;fullResult=null;ownHeader=null;chrome=null;recovery=null;ownClan=null;enemyClan=null;ourExpected=0;enemyExpected=0;uploadRequestId=null;rosterReview.hide();rivalBonusDialog.hide();ours.clear();enemies.clear();listControl=null;rosterMode=0;rosterDownGain=1f;manualListBoundary=false;screenFlow.reset();transitionEpoch++; generation++;active=false;selector?.close();selector=null;releaseSelectionFrame();GestureConnection.cancel();shield.hide();GestureConnection.beforeGesture=null;GestureConnection.afterGesture=null;GestureConnection.inputSettleMillis=null;panel?.let { runCatching {wm.removeViewImmediate(it)} };panel=null;CaptureConnection.removeStateListener(listener) }
 private fun releaseSelectionFrame() {
  val image=selectionFrame;selectionFrame=null
  // ScreenSelectionWindow.removeView is deferred; let its last queued draw finish.
  if(image!=null)h.postDelayed({if(!image.isRecycled)image.recycle()},300)
 }
 private fun stop() { hide();app.closeScanner() }
 private fun later(delay:Long=400,action:()->Unit) { val g=generation;h.postDelayed({if(active&&g==generation){if(chrome?.dragging==true)later(80,action)else action()}},delay) }
 private fun dp(v:Int)=(v*context.resources.displayMetrics.density).toInt()
 private fun say(text:String) {status?.text=text}
 private fun step(at:Int,title:String) {
  if(fullMode)fullStore.log("step",JSONObject().put("stage",at).put("title",title))
  if(!paused)panel?.visibility=View.VISIBLE
  if(at!=stage)restoreFullPanel() else if(autoCompacted){autoCompacted=false;chrome?.setCompact(false)}
  stage=at;val color=eu.komars.forgescanner.ui.ScannerAppearance.stepColor(at)
  stageLabel?.text=if(rankingMode)"▶ Ranking klanu: $title" else "▶ Krok ${at+1}/${maxOf(at+1,if(listMode) 8 else if(preparingAll) 7 else 6)}: $title";stageLabel?.setTextColor(color)
  (panel?.background as? android.graphics.drawable.GradientDrawable)?.setStroke(dp(2),color)
 }
 private fun clearRecoveryActions() {recovery?.removeAllViews()}
 /** After a finished list or ranking: one tap back to the app (closes the overlay and capture). */
 private fun addReturnToApp() {
  restoreFullPanel()
  recovery?.addView(ScannerAppearance.overlayAction(context,"↩ Wróć do aplikacji") {
   context.startActivity(android.content.Intent(context,BattleActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP))
   stop()
  },0)
 }
 private fun fail(text:String) {
  restoreFullPanel()
  if(fullMode){fullStore.log("failure",JSONObject().put("stage",stage).put("target",fullTarget?:JSONObject.NULL).put("reason",text));if(stage==6&&fullStore.state()!=null)retryStep={resumeFullWork()}}
  screenFlow.reset();transitionEpoch++
  setRosterControls(0);warning?.visibility=View.GONE;active=false;generation++;GestureConnection.cancel();shield.hide();selector?.close();selector=null;releaseSelectionFrame()
  GestureConnection.beforeGesture=null;GestureConnection.afterGesture=null;GestureConnection.inputSettleMillis=null
  clearRecoveryActions();status?.maxLines=Int.MAX_VALUE;status?.text=text;panel?.visibility=View.VISIBLE
  recovery?.addView(ScannerAppearance.overlayAction(context,if(fullMode&&fullTarget!=null)"Kontynuuj: $fullTarget"else"Kontynuuj",action={val retry=retryStep;if(retry!=null)recoverAction(retry)}))
  repairKey?.let {key ->recovery?.addView(ScannerAppearance.overlayAction(context,"Popraw bieżące pole",action={
   val retry=retryStep
   prefs.edit().remove("war_$key").putBoolean("war_verified_$key",false).apply()
   if(retry!=null)recoverAction(retry)
  }))}
  if(stage in 1..3)recovery?.addView(ScannerAppearance.overlayAction(context,"Wskaż badania ręcznie",action={manualResearch()}))
  if(stage in 2..3)recovery?.addView(ScannerAppearance.overlayAction(context,"Popraw opis i zamknięcie badania",action={
   prefs.edit().remove("war_researchText").remove("war_researchClose").apply()
   recoverAction {prepareResearchWindow(pendingHealth)}
  }))
  if(stage in 4..6&&!(fullMode&&stage==6))recovery?.addView(ScannerAppearance.overlayAction(context,"Popraw przyciski klanu i wojny",action={
   val edit=prefs.edit();listOf("clan","warTab","ourFlag","enemyFlag").forEach {edit.remove("war_$it");proposals.remove(it)};edit.apply()
   recoverAction {returnHome {warScreen()}}
  }))
 }
 private fun recoverAction(next:()->Unit) {
  clearRecoveryActions();active=true;generation++;warning?.visibility=View.VISIBLE
  if(fullComputing){next();return}
  val g=generation
  GestureConnection.requestArm(width,height) {result ->if(active&&g==generation){session=result.getOrNull();if(session==null)fail(result.exceptionOrNull()?.message?:"Włącz dostępność i ponów.")else next()}}
 }
 private fun saveRegion(key:String,r:Rect) {prefs.edit().putBoolean("war_verified_$key",false).putString("war_$key",JSONObject().put("width",width).put("height",height).put("l",r.left).put("t",r.top).put("r",r.right).put("b",r.bottom).toString()).apply()}
 private fun manualResearch() {
  shield.hide();prefs.edit().remove("war_manualResearchPair").apply()
  recoverAction {
  mark("manualResearchPair","Zaznacz obie ikony: flagę z mieczem po lewej i flagę z sercem po prawej") {r ->
   if(r.width()<r.height()) {fail("Obejmij dwie ikony obok siebie jednym poziomym prostokątem.");return@mark}
   val middle=r.centerX()
   saveRegion("damageNode",Rect(r.left,r.top,middle,r.bottom));saveRegion("healthNode",Rect(middle,r.top,r.right,r.bottom))
   warning?.visibility=View.VISIBLE;researchNode(false)
  }
  }
 }
 fun configurationReady():Boolean {
  val keys=listOf("technology","clanTechnologyCategory","damageNode","healthNode","researchText","researchClose","clan","warTab","ourFlag","enemyFlag","roster","rosterClose") + if(WarPreparationModel.day()!=null) listOf("rivalInfo","rivalText","infoOk") else emptyList()
  val geometries=keys.map {key ->runCatching {JSONObject(prefs.getString("war_$key",null)!!)}.getOrNull()?:return false}
  val w=geometries.first().optInt("width");val hh=geometries.first().optInt("height")
  if(w<=0||hh<=0||geometries.any {o ->o.optInt("width")!=w||o.optInt("height")!=hh||o.optInt("l",-1)<0||o.optInt("t",-1)<0||o.optInt("r")>w||o.optInt("b")>hh||o.optInt("r")-o.optInt("l")<8||o.optInt("b")-o.optInt("t")<8})return false
  val navigations=listOf("science","technology","clanTechnologyCategory","damageNode","healthNode","researchClose","clan","warTab","ourFlag","enemyFlag","rosterClose") + if(WarPreparationModel.day()!=null)listOf("rivalInfo","infoOk")else emptyList()
  return navigation.hasNavigation(AutoScanStep.OPEN_RESEARCH) && navigations.all {prefs.getBoolean("war_verified_$it",false)}
 }
 fun offer(roster:Boolean,configureAll:Boolean=false,test:Boolean=false,full:Boolean=false,ranking:Boolean=false) {
  hide();app.battleOverlay.hide()
  if(ranking){offerRanking();return}
  panel=LinearLayout(context).apply {
   orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(8),dp(12),dp(8))
   background=ScannerAppearance.overlayPanel(context)
   addView(ScannerAppearance.title(context,if(full)"Odczyt listy wojennej" else "Przygotowanie wojenne"))
   addView(ScannerAppearance.body(context,if(full)"Odczytam profile obu klanów. Profile z ostatnich 30 dni z niezmienioną mocą pomijam — skanuję tylko nowych i zmienionych graczy. STOP zapisuje postęp. "+(if(test)"Poza niedzielą rywal dostaje takie same bonusy jak nasz klan." else "W niedzielę odczytam też bonusy rywala; listę na tę wojnę zapisuję raz.")else "Poczekaj, aż gra się uruchomi. Rozpocznij przyciskiem poniżej."))
   addView(ScannerAppearance.overlayAction(context,"Skanuj / Rozpocznij") {start(roster,configureAll,test,full)})
   if(full)addView(ScannerAppearance.overlayAction(context,"Nowy skan — aktualizuj tylko zmiany") {fullStore.resetRun();start(roster,configureAll,test,full)})
   if(full)addView(ScannerAppearance.button(context,"Usuń zapisane profile i skanuj od zera",quiet=true) {confirmClear {fullStore.clear();start(roster,configureAll,test,full)}})
   addView(ScannerAppearance.overlayAction(context,"Zakończ") {stop()})
  }
  wm.addView(panel,WindowManager.LayoutParams(dp(250),-2,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT).apply {gravity=Gravity.TOP or Gravity.START;x=dp(8);y=dp(30)})
  CaptureConnection.addStateListener(listener)
 }
 private fun confirmClear(action:()->Unit) {
  android.app.AlertDialog.Builder(android.view.ContextThemeWrapper(context,eu.komars.forgescanner.R.style.ScannerDialogTheme)).setTitle("Usunąć zapisane profile?")
   .setMessage("Następny skan odczyta od nowa wszystkich graczy obu klanów. Pola i kalibracja zostają.")
   .setPositiveButton("Usuń") {_,_->action()}.setNegativeButton("Anuluj",null).create().apply {window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)}.show()
 }
 private fun offerRanking() {
  val saved=savedRankingRoster()
  panel=LinearLayout(context).apply {
   orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(8),dp(12),dp(8))
   background=ScannerAppearance.overlayPanel(context)
   addView(ScannerAppearance.title(context,"Odczyt rankingu klanu"))
   addView(ScannerAppearance.body(context,"Każdy gracz naszego klanu walczy z każdym (bez bonusów wojennych). Profile z ostatnich 30 dni z niezmienioną mocą pomijam. Wynik trafia do /report clan-ranking."))
   addView(ScannerAppearance.overlayAction(context,"Skanuj nasz klan — aktualizuj zmiany") {start(false,full=true,ranking=true)})
   if(saved!=null)addView(ScannerAppearance.overlayAction(context,"Przelicz z zapisanych profili (${saved.second.size} graczy)") {start(false,full=true,ranking=true,savedOnly=true)})
   addView(ScannerAppearance.overlayAction(context,"Zakończ") {stop()})
  }
  wm.addView(panel,WindowManager.LayoutParams(dp(250),-2,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT).apply {gravity=Gravity.TOP or Gravity.START;x=dp(8);y=dp(30)})
  CaptureConnection.addStateListener(listener)
 }
 private fun savedRankingRoster():Pair<String,List<WarMember>>? = runCatching {
  val o=JSONObject(data.getString("rankingRoster",null)!!);val list=o.getJSONArray("members")
  o.getString("clan") to (0 until list.length()).map {WarMember(list.getJSONObject(it).getString("name"),list.getJSONObject(it).getDouble("power"))}
 }.getOrNull()?.takeIf {it.second.size>=2}
 fun start(roster:Boolean,configureAll:Boolean=false,test:Boolean=false,full:Boolean=false,ranking:Boolean=false,savedOnly:Boolean=false) {
  hide();app.battleOverlay.hide();rankingMode=ranking&&full;fullAutoRetry=0;slim=false;autoCompacted=false
  // A run checkpoint belongs to one mode; switching mode restarts navigation but keeps every profile.
  if(full&&!savedOnly&&fullStore.state()?.let {it.optBoolean("ranking")!=rankingMode}==true)fullStore.resetRun()
  // A run left for hours (e.g. overnight) is not resumed: members' powers changed, so the saved roster no
  // longer matches the screen ("Nie udało się potwierdzić tożsamości"). Profiles are kept; the list is read anew.
  if(full&&!savedOnly&&fullStore.state()?.let {System.currentTimeMillis()-it.optLong("updatedAt")>2*60*60*1000L}==true)fullStore.resetRun()
  fullMode=full;fullProcessed.clear();fullTarget=null;fullSide=false;fullCentered.clear();fullRepairPass=false;fullEnemySwept=false;lastFullViewport=emptyList();stableFullViewport=0;fullCancel=java.util.concurrent.atomic.AtomicBoolean(false);if(full)fullStore.prune();testMode=test;listMode=roster;preparingAll=configureAll;rosterConfigured=false;proposals.clear()
  if(!app.mobile.paired) {Toast.makeText(context,"Połącz aplikację z Discordem w menu głównym.",Toast.LENGTH_LONG).show();return}
  if(rankingMode)testMode=false
  if(roster && !testMode && WarPreparationModel.day()==null) {Toast.makeText(context,"Lista dostępna od niedzieli 01:00 do poniedziałku 01:00.",Toast.LENGTH_LONG).show();return}
  active=true;generation++;width=0;height=0;ours.clear();enemies.clear()
  chrome=WarOverlayChrome(context,::stop,{manualListBoundary=true;say("Zatrzymuję przewijanie po bieżącym odczycie…")},if(rankingMode)"Odczyt rankingu klanu" else if(fullMode)"Odczyt listy wojennej" else "Przygotowanie wojenne",if(full)::setPaused else null) {touching ->
   app.battleOverlay.batchInteraction(touching)
   if(touching){protectedBeforeInteraction=shield.isShowing;shield.hide()}
   else if(active&&protectedBeforeInteraction)panelBounds()?.let {shield.show(it)}
  }
  panel=chrome!!.root;recovery=chrome!!.recovery
  stageLabel=TextView(context).apply {textSize=12f;setTypeface(null,android.graphics.Typeface.BOLD)};chrome!!.content.addView(stageLabel)
  warning=ScannerAppearance.handsOffWarning(context);chrome!!.content.addView(warning)
  status=TextView(context).apply {setTextColor(ScannerAppearance.ACCENT);textSize=12f};chrome!!.content.addView(status)
  chrome!!.content.addView(recovery)
  listControl=ScannerAppearance.overlayAction(context,"Zatrzymaj listę") {manualListBoundary=true;say("Zatrzymuję przewijanie po bieżącym odczycie…")}.apply {visibility=View.GONE}
  chrome!!.addListControl(listControl!!);chrome!!.show()
  CaptureConnection.addStateListener(listener)
  say(if(rankingMode)"Ranking klanu" else "Przygotowanie do bitwy wojennej")
  val g=generation
  if(savedOnly){val saved=savedRankingRoster();if(saved==null)fail("Brak zapisanej listy klanu. Zeskanuj nasz klan.")else {ownClan=saved.first;computeRanking(saved.second)};return}
  if(roster && !testMode) app.mobile.warStatus { result -> if(active && g==generation) result.fold({ val st=JSONObject(it);canOverrideList=st.optBoolean("can_override");if(st.optBoolean("locked")&&!canOverrideList) fail("Lista na tę wojnę została już utworzona. Nie nadpisujemy jej.") else readyToBegin() },{fail(it.message?:"Nie udało się sprawdzić blokady serwera.")}) } else readyToBegin()
 }
 private fun readyToBegin() {if(fullMode&&fullStore.state()!=null){resumeFullWork();return};if(rankingMode){beginRanking();return}; step(0,"Przygotowanie badań");say("Dane postaci zapisane. Przygotowuję odczyt badań — nie dotykaj ekranu.");later(500) {begin()} }
 private fun frame(fresh:Boolean=true,next:(Bitmap)->Unit) {
  val g=generation
  val request: (eu.komars.forgescanner.capture.FrameCallback)->Unit=if(fresh) CaptureConnection::requestFreshFrame else CaptureConnection::requestFrame
  request received@{image,error ->
   if(!active||g!=generation){image?.recycle();return@received}
   if(image==null){fail(error?:"Brak obrazu gry");return@received}
   if(width!=0&&(width!=image.width||height!=image.height)){image.recycle();fail("Zmieniono rozmiar obrazu. Ustaw pola ponownie.");return@received}
   width=image.width;height=image.height;next(image)
  }
 }
 private fun rect(key:String):Rect?=runCatching { val o=JSONObject(prefs.getString("war_$key",null)!!);if(o.getInt("width")!=width||o.getInt("height")!=height)return null;Rect(o.getInt("l"),o.getInt("t"),o.getInt("r"),o.getInt("b")) }.getOrNull()
 private fun mark(key:String,title:String,next:(Rect)->Unit) {
  repairKey=key
  rect(key)?.let {next(it);return}
  say("Ustaw: $title")
  panel?.visibility=View.INVISIBLE
  later(180) {frame {image ->
   // Never draw a research proposal measured on an older, still-moving frame.
   if(key in listOf("damageNode","healthNode")) {
    val nodes=navigationPixels(image) {w,h,p ->
     eu.komars.forgescanner.model.WarResearchNodes.find(w,h,p)?.let {listOf(it.first,it.second)}?:emptyList()
    }
    proposals.remove(key)
    nodes.getOrNull(0)?.let {proposals["damageNode"]=it}
    nodes.getOrNull(1)?.let {proposals["healthNode"]=it}
   }
   val proposed=proposals[key]
   selectionFrame=image
   val group=when(key) {
    "damageNode","healthNode"->listOf("damageNode" to "Obrażenia wojenne", "healthNode" to "Zdrowie wojenne")
    "researchText"->listOf("researchText" to "Opis badania", "researchClose" to "Zamknięcie badania")
    "ourFlag"->listOf("ourFlag" to "Nasz klan", "enemyFlag" to "Klan rywala")
    "roster"->listOf("roster" to "Lista członków", "rosterClose" to "Zamknięcie listy")
    else->emptyList()
   }
   fun accept(values:Map<String,Rect>) {
    selector=null;app.setScannerBusy("war",active);releaseSelectionFrame();panel?.visibility=View.VISIBLE
    values.forEach {(field,r)->saveRegion(field,r);if(field=="technology")navigation.saveTechnologyTab(r,width,height)};next(values.getValue(key))
   }
   val choices=group.associate {(field,label)->field to (label to (rect(field)?:proposals[field]))}
   selector=ScreenSelectionWindow(context,image,false,{r,_,_ ->accept(mapOf(key to r))},
    {releaseSelectionFrame();fail("Konfiguracja przerwana.")},titleText="Krok ${stage+1}: $title",
    instructionText=if(group.isNotEmpty()) "Wybierz pole po prawej i popraw ramkę. Zatwierdź pola tego okna razem. Nie zaznaczaj Atak ani Przekaż darowiznę." else "Zatwierdź propozycję lub narysuj ramkę na właściwym elemencie.",
    initialSelection=if(group.isEmpty())proposed else null,minimumSelectionSize=8,fields=choices,onFields=if(group.isEmpty())null else ::accept)
   app.setScannerBusy("war",false)
   selector!!.show()
  }}
 }
 private fun panelBounds():Rect? {val box=panel?:return null;val loc=IntArray(2);box.getLocationOnScreen(loc);return Rect(loc[0],loc[1],loc[0]+box.width,loc[1]+box.height)}
 private fun away(r:Rect,attempt:Int=0,next:()->Unit) {
  val box=panel?:return;box.visibility=View.VISIBLE;box.measure(View.MeasureSpec.makeMeasureSpec((box.layoutParams as WindowManager.LayoutParams).width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED))
  val previous=box.layoutParams as WindowManager.LayoutParams
  val occupied=Rect(previous.x,previous.y,previous.x+maxOf(box.width,box.measuredWidth),previous.y+maxOf(box.height,box.measuredHeight))
  val y=if(!Rect.intersects(occupied,r))previous.y else eu.komars.forgescanner.model.OverlayPlacement.find(height,r.top,r.bottom,maxOf(box.height,box.measuredHeight),0,dp(20),32)
  if(y==null&&chrome?.compact==false&&!slim) {
   // No room beside the target: first drop the banner and recovery buttons (STOP and Pause stay).
   setSlim(true)
   later(180){away(r,attempt,next)}
   return
  }
  if(y==null&&chrome?.compact==false) {
   // Last resort only; the panel expands again by itself at the next step.
   autoCompacted=true;chrome?.setCompact(true)
   later(180){away(r,attempt,next)}
   return
  }
  if(y==null){fail("Brak miejsca na STOP poza polem.");return}
  val lp=box.layoutParams as WindowManager.LayoutParams;lp.y=y;wm.updateViewLayout(box,lp)
  later(180) {val loc=IntArray(2);box.getLocationOnScreen(loc)
   val actual=Rect(loc[0],loc[1],loc[0]+box.width,loc[1]+box.height)
   if(Rect.intersects(actual,r)) {
    if(attempt<1){away(r,attempt+1,next)}else fail("Nakładka zasłania pole gestu. Popraw wskazany przycisk.")
    return@later
   }
   val allowed=shield.show(Rect(loc[0],loc[1],loc[0]+box.width,loc[1]+box.height));if(!allowed){fail("Nie udało się zabezpieczyć gestu.");return@later}
   GestureConnection.beforeGesture={target ->chrome?.dragging!=true && panelBounds()?.let {shield.show(it)&&shield.openGesture(target)}==true};GestureConnection.afterGesture={shield.closeGesture()};GestureConnection.inputSettleMillis={shield.inputSettleMillis()};next()
  }
 }
 private fun tap(key:String,title:String,next:()->Unit) {
  say(title);mark(key,title) {r ->verifiedTap(key,r,next)}
 }
 private data class Scene(val states:Set<String>,val fields:Map<String,Rect>,val forbidden:List<Rect> = emptyList())
 private fun scene(image:Bitmap,homeFallback:Boolean=false):Scene {
  val ww=minOf(360,image.width);val hh=image.height*ww/image.width
  val small=Bitmap.createScaledBitmap(image,ww,hh,true);val p=IntArray(ww*hh);small.getPixels(p,0,ww,0,0,ww,hh)
  val geometry=eu.komars.forgescanner.model.GameUiGeometryDetector(ww,hh,p)
  val observed=eu.komars.forgescanner.model.WarScreenGeometry.observe(ww,hh,p)
  fun scaled(r:eu.komars.forgescanner.model.PixelRegion)=Rect(r.left*width/ww,r.top*height/hh,r.right*width/ww,r.bottom*height/hh)
  val fields=mutableMapOf<String,Rect>();val states=mutableSetOf<String>()
  val observedRoster=observed.roster;val observedOk=observed.ok;val observedResearch=observed.research
  if(observedRoster!=null) {
   states+="roster";fields["roster"]=scaled(observedRoster);observed.rosterClose?.let {fields["rosterClose"]=scaled(it)}
  } else if(observedOk!=null) {
   states+="bonus";fields["infoOk"]=scaled(observedOk);observed.bonusText?.let {fields["rivalText"]=scaled(it)}
  } else if(observedResearch!=null) {
   states+="researchModal";fields["researchText"]=scaled(eu.komars.forgescanner.model.ResearchWindowGeometry.description(observedResearch));observed.researchClose?.let {fields["researchClose"]=scaled(it)}
  } else {
   if(BattleHomeProof.isHome(ww,hh,p)||(homeFallback&&BattleHomeProof.isHome(image))) {
    states+="home"
    eu.komars.forgescanner.model.WarNavigationGeometry.clanEntry(ww,hh,p)?.let {fields["clan"]=scaled(it)}
    geometry.detect(AutoScanStep.OPEN_RESEARCH)?.region?.let {fields["science"]=scaled(it)}
   }
   val flags=eu.komars.forgescanner.model.WarNavigationGeometry.flags(ww,hh,p)
   if(flags!=null){states+="war";fields["ourFlag"]=scaled(flags.first);fields["enemyFlag"]=scaled(flags.second);observed.info?.let {fields["rivalInfo"]=scaled(it)}}
   val kind=geometry.screenKind()
   if(kind in listOf(eu.komars.forgescanner.model.GameScreenKind.SKILLS,eu.komars.forgescanner.model.GameScreenKind.PETS,eu.komars.forgescanner.model.GameScreenKind.TECHNOLOGY)) {
    states+="researchTabs";geometry.technologyTab()?.let {fields["technology"]=scaled(it)}
   }
   if(observed.tree) {
    states+="tree"
    eu.komars.forgescanner.model.WarResearchNodes.find(ww,hh,p)?.let {fields["damageNode"]=scaled(it.first);fields["healthNode"]=scaled(it.second)}
   }
   val category=eu.komars.forgescanner.model.WarNavigationGeometry.clanTechnologyCategory(ww,hh,p)
   if(category!=null && "researchTabs" in states && "tree" !in states){states+="categories";fields["clanTechnologyCategory"]=scaled(category)}
   observed.footerClose?.let {q ->
    val target=scaled(q);fields["mainClose"]=target
    fun near(r:Rect?)=r!=null&&kotlin.math.abs(r.exactCenterX()-target.exactCenterX())<maxOf(r.width(),target.width())&&kotlin.math.abs(r.exactCenterY()-target.exactCenterY())<maxOf(r.height(),target.height())
    if("home" !in states) {
     if(near(rect("clan")))states+="clan"
     if(near(navigation.navigation(AutoScanStep.OPEN_RESEARCH,width,height)?.region?:rect("science")))states+="researchTabs"
     val layout=BattleSetupGeometry(ww,hh,p).detect(true)
     if(layout.panel!=null)states+=if(layout.profile)"profile"else"modal"
    }
   }
  }
  if(small!==image)small.recycle()
  
  // Resource scanning bans all technology content. War setup legitimately opens those nodes;
  // typed source/target checks and OCR donation/upgrade exclusions protect that context instead.
  val resourceForbidden=if(geometry.screenKind()==eu.komars.forgescanner.model.GameScreenKind.TECHNOLOGY)emptyList()else geometry.forbiddenActionAreas().map {scaled(it)}
  return Scene(states,fields,resourceForbidden)
 }
 private fun cleanFrame(next:(Bitmap)->Unit) {
  panel?.visibility=View.INVISIBLE
  later(180){frame {image->panel?.visibility=View.VISIBLE;next(image)}}
 }
 private fun observeScene(homeFallback:Boolean=false,next:(Result<Scene>)->Unit) {
  val g=generation
  cleanFrame {image ->
   analysisWorker.execute {
    val result=runCatching {scene(image,homeFallback)};image.recycle()
    h.post {if(active&&g==generation)next(result)}
   }
  }
 }
 private fun verifiedTap(key:String,target:Rect,next:()->Unit) {
  if(fullMode)fullStore.log("tap-request",JSONObject().put("key",key).put("x",target.centerX()).put("y",target.centerY()).put("target",fullTarget?:JSONObject.NULL))
  val graph=eu.komars.forgescanner.model.GameNavigationGraph
  val action=when(key) {
   "sectionExit","mainClose"->if(openSection=="science")eu.komars.forgescanner.model.GameNavigationGraph.Action.CLOSE_RESEARCH else eu.komars.forgescanner.model.GameNavigationGraph.Action.CLOSE_CLAN
   else->graph.warAction(key)
  }?:run {fail("Nieznany przycisk w mapie przejść: $key");return}
  if(!screenFlow.allows(action)){fail("Kolejność ekranów wymaga ponowienia bieżącego przejścia. Wróć do właściwego okna i wybierz Kontynuuj.");return}
  val g=generation;val epoch=++transitionEpoch
  away(Rect(target).apply {inset(-32,-32)}) {
   GestureConnection.tap(requireNotNull(session),target.exactCenterX(),target.exactCenterY()) {result ->
    if(active&&g==generation&&epoch==transitionEpoch) {
     shield.hide()
     result.fold({
      if(fullMode)fullStore.log("tap-complete",JSONObject().put("key",key))
      screenFlow.completed(action);prefs.edit().putBoolean("war_verified_$key",true).apply()
      later(if(key=="sectionExit")350 else 220,next)
     },{fail(it.message?:"Nie udało się wykonać przejścia.")})
    }
   }
  }
 }
 private fun readClanHeader(r:Rect,next:(String,IntArray)->Unit) {
  say("Odczytuję nagłówek klanu")
  val g=generation
  away(r){shield.hide();later(180){frame {image ->
   if(!Rect(0,0,width,height).contains(r)){image.recycle();fail("Pole nagłówka poza ekranem.");return@frame}
   val crop=Bitmap.createBitmap(image,r.left,r.top,r.width(),r.height());if(crop!==image)image.recycle()
   RosterHeaderReader(ocr,if(eu.komars.forgescanner.BuildConfig.DEBUG)context.cacheDir else null).read(crop,{active&&g==generation},attempted={pass,text->
    fullStore.log("header-ocr",JSONObject().put("bounds",r.toShortString()).put("pass",pass).put("text",text).put("count",WarPreparationModel.memberCount(text)?:JSONObject.NULL).put("clan",WarRosterProof.clan(text)?:JSONObject.NULL))
    if(pass<2&&(WarPreparationModel.memberCount(text) !in 1..50||WarRosterProof.clan(text)==null))say("Ponawiam nagłówek w innym kontraście. Nie dotykaj gry.")
   }) {reading->panel?.visibility=View.VISIBLE;next(reading.text,reading.snapshot)}
  }}}
 }
 private fun read(r:Rect,variant:Int=-1,sampled:((IntArray)->Unit)?=null,headerSnapshot:((IntArray)->Unit)?=null,next:(String)->Unit) {
  if(rosterMode==0)say("Odczytuję dane")
  val box=panel?:return
  // A full-screen text observation briefly hides the panel; region reads move it outside the crop.
  fun capture() {later(180) {frame(fresh=sampled==null) {image ->
   if(!Rect(0,0,width,height).contains(r)){image.recycle();fail("Pole poza ekranem");return@frame}
   sampled?.invoke(rosterSample(image,r))
   val crop=Bitmap.createBitmap(image,r.left,r.top,r.width(),r.height());headerSnapshot?.let {capture ->val p=IntArray(crop.width*crop.height);crop.getPixels(p,0,crop.width,0,0,crop.width,crop.height);capture(WarRosterProof.headerSnapshot(crop.width,crop.height,p))};val enlarged=Bitmap.createScaledBitmap(crop,crop.width*(if(variant<0)2 else 3),crop.height*(if(variant<0)2 else 3),true);val scaled=if(variant<=0)enlarged else {val colors=IntArray(enlarged.width*enlarged.height);enlarged.getPixels(colors,0,enlarged.width,0,0,enlarged.width,enlarged.height);for(i in colors.indices){val c=colors[i];val gray=((c ushr 16 and 255)*299+(c ushr 8 and 255)*587+(c and 255)*114)/1000;colors[i]=if(gray>(if(variant==1)150 else 200))Color.WHITE else Color.BLACK};Bitmap.createBitmap(colors,enlarged.width,enlarged.height,Bitmap.Config.ARGB_8888).also {if(enlarged!==crop)enlarged.recycle()}};if(crop!==scaled)crop.recycle();if(image!==crop)image.recycle()
   val g=generation
   ocr.process(InputImage.fromBitmap(scaled,0)).addOnSuccessListener {result ->if(active&&g==generation){box.visibility=View.VISIBLE;next(BattleOcrRows.text(result.textBlocks.flatMap {it.lines}.mapNotNull {line->line.boundingBox?.let {BattleOcrLine(line.text,it.left,it.top,it.right,it.bottom)}}))}}.addOnFailureListener {if(active&&g==generation)fail("Nieczytelny odczyt. Ponów skan.")}.addOnCompleteListener {scaled.recycle()}
  }}}
  if(r.height()>height*.8){box.visibility=View.INVISIBLE;capture()}else away(r){shield.hide();capture()}
 }
 private fun returnHome(at:Int=0,stable:Int=0,next:()->Unit) {
  retryStep={returnHome(next=next)};repairKey=null
  if(at==0 && openSection!=null) {
   if(openSection=="science"){leaveResearch(next);return}
   rect("clan")?.let {r ->saveRegion("sectionExit",r);tap("sectionExit","Powrót dolnym przyciskiem klanu"){openSection=null;next()};return}
  }
  say("Sprawdzam główny ekran gry. Nie dotykaj ekranu.")
  fun arrived(){openSection=null;screenFlow.observe(eu.komars.forgescanner.model.GameNavigationGraph.Screen.HOME);next()}
  fun waitForHome(){if(at>=8)fail("Nie potwierdzono głównego ekranu. Otwórz go w grze i wybierz Kontynuuj.")else later(250){returnHome(at+1,0,next)}}
  observeScene(homeFallback=true) {result ->result.fold({observed ->
   when(val plan=WarReturnPlan.decide(observed.states,observed.fields.keys)) {
    WarReturnPlan.Step.Home->arrived()
    WarReturnPlan.Step.Wait->waitForHome()
    is WarReturnPlan.Step.Close->{
     // The next frame may already be HOME. Never draw or tap an earlier frame's close proposal.
     observeScene(homeFallback=true) {freshResult ->freshResult.fold({fresh ->
      when(val current=WarReturnPlan.decide(fresh.states,fresh.fields.keys)) {
       WarReturnPlan.Step.Home->arrived()
       is WarReturnPlan.Step.Close->if(current.key==plan.key) {
        if(fresh.states.any {it in listOf("researchTabs","tree","categories")})openSection="science"
        else if(fresh.states.any {it in listOf("war","clan")})openSection="clan"
        verifiedTap(current.key,fresh.fields.getValue(current.key)){returnHome(at+1,0,next)}
       }else waitForHome()
       WarReturnPlan.Step.Wait->waitForHome()
      }
     },{fail(it.message?:"Nie odczytano aktualnego ekranu.")})}
    }
   }
  },{fail(it.message?:"Nie udało się rozpoznać ekranu przed powrotem.")})}
 }
 /** Ranking needs no research bonuses: go straight to our clan list. */
 private fun beginRanking() {
  openSection=null;screenFlow.reset();step(0,"Główny ekran gry");say("Przechodzę do listy naszego klanu…")
  frame {image ->image.recycle();GestureConnection.requestArm(width,height) {r ->
   session=r.getOrNull()
   if(session==null)fail(r.exceptionOrNull()?.message?:"Włącz dostępność")else returnHome {warScreen()}
  }}
 }
 private fun begin() {
  openSection=null;researchReplayAllowed=true;screenFlow.reset()
  step(0,"Główny ekran gry");say("Przygotowuję nawigację…")
  frame {image ->image.recycle();GestureConnection.requestArm(width,height) {r ->
   session=r.getOrNull()
   if(session==null)fail(r.exceptionOrNull()?.message?:"Włącz dostępność")else returnHome {research()}
  }}
 }
 private var openSection:String?=null
 private fun leaveResearch(next:()->Unit) {
  val entrance=navigation.navigation(AutoScanStep.OPEN_RESEARCH,width,height)?.region?:rect("science")
  if(entrance!=null) {saveRegion("sectionExit",entrance);tap("sectionExit","Powrót dolnym przyciskiem badań"){openSection=null;next()}}
  else returnHome(next=next)
 }
 private fun research() {
  retryStep={research()}
  val savedScience=navigation.navigation(AutoScanStep.OPEN_RESEARCH,width,height)?.takeIf {it.after!=null}
  if(savedScience!=null){prefs.edit().putBoolean("war_verified_science",true).apply();verifiedTap("science",savedScience.region){technologyTab()};return}
  step(1,"Badania i ulepszenia");say("Przygotowuję wspólny przycisk badań i ulepszeń.")
  panel?.visibility=View.INVISIBLE
  later(180) {frame {image ->
   val saved=navigation.navigation(AutoScanStep.OPEN_RESEARCH,width,height)?.takeIf {it.after!=null}
   val detected=eu.komars.forgescanner.vision.GameUiDetector().detect(image,AutoScanStep.OPEN_RESEARCH)?.region
   val before=ScreenCheckpoint.capture(image);image.recycle();panel?.visibility=View.VISIBLE
   if(saved!=null) {verifiedTap("science",saved.region) {technologyTab()};return@frame}
   prefs.edit().remove("war_science").apply()
   if(detected!=null)proposals["science"]=detected
   mark("science","Przycisk badań / ulepszeń na głównym ekranie gry") {target ->
    navigation.saveNavigation(AutoScanStep.OPEN_RESEARCH,target,width,height,before)
    verifiedTap("science",target) {verifyResearchDestination(0)}
   }
  }}
 }
 private fun verifyResearchDestination(at:Int) {
  // Share the destination signature with resources using one frame, without reclassifying the screen.
  frame {image ->
   val checkpoint=ScreenCheckpoint.capture(image);image.recycle()
   if(checkpoint.valid)navigation.saveDestination(AutoScanStep.OPEN_RESEARCH,checkpoint)
   technologyTab()
  }
 }
 private fun technologyTab() {
  openSection="science"
  retryStep={technologyTab()}
  if(rect("technology")==null)navigation.technologyTab(width,height)?.let {saveRegion("technology",it)}
  if(rect("technology")!=null){tap("technology","Zapisany przycisk"){openClanTechnology()};return}
  say("Rozpoznaję zakładki: umiejętności, zwierzaki i drzewo technologii. Nie dotykaj ekranu.")
  panel?.visibility=View.INVISIBLE
  later(180) {frame {image ->
   val target=eu.komars.forgescanner.vision.GameUiDetector().technologyTab(image)
   image.recycle();panel?.visibility=View.VISIBLE
   propose("technology",target)
   tap("technology","Zakładka Drzewo technologii — pole do zatwierdzenia") {openClanTechnology()}
  }}
 }
 private fun openClanTechnology() {
  retryStep={openClanTechnology()}
  if(rect("clanTechnologyCategory")!=null){tap("clanTechnologyCategory","Zapisany przycisk"){rewindResearch(0)};return}
  say("Rozpoznaję kafelek klanu po czerwonej fladze. Nazwa klanu może się zmieniać.")
  panel?.visibility=View.INVISIBLE
  later(180) {frame {image ->
   val target=navigationPixels(image) {w,h,p ->listOfNotNull(eu.komars.forgescanner.model.WarNavigationGeometry.clanTechnologyCategory(w,h,p))}.singleOrNull()
   image.recycle();panel?.visibility=View.VISIBLE;propose("clanTechnologyCategory",target)
   mark("clanTechnologyCategory","Kafelek klanu z czerwoną flagą") {target ->verifiedTap("clanTechnologyCategory",target) {rewindResearch(0)}}
  }}
 }
 private fun seekResearch(at:Int,rewound:Boolean=false,replayCheck:Boolean=false) {
  retryStep={seekResearch(at,rewound,replayCheck)}
  say("Szukam badań wojennych: ikony flagi z mieczem i sercem (niebieskie lub zielone). Nie dotykaj ekranu.")
  panel?.visibility=View.INVISIBLE
  later(180) {frame {image ->
   val ww=minOf(360,image.width);val hh=image.height*ww/image.width
   val small=Bitmap.createScaledBitmap(image,ww,hh,true);val pixels=IntArray(ww*hh);small.getPixels(pixels,0,ww,0,0,ww,hh)
   val nodes=eu.komars.forgescanner.model.WarResearchNodes.find(ww,hh,pixels)
   if(small!==image)small.recycle();image.recycle();panel?.visibility=View.VISIBLE
   if(nodes!=null) {
    fun scaled(r:eu.komars.forgescanner.model.PixelRegion)=Rect(r.left*width/ww,r.top*height/hh,r.right*width/ww,r.bottom*height/hh)
    for((key,r) in listOf("damageNode" to scaled(nodes.first),"healthNode" to scaled(nodes.second))) {
     proposals[key]=r
     val old=rect(key)
     if(old!=null){saveRegion(key,r);prefs.edit().putBoolean("war_verified_$key",true).apply()}
    }
    prefs.edit().putString("war_researchRoute",JSONObject().put("version",ResearchScrollGesture.ROUTE_VERSION).put("width",width).put("height",height).put("steps",at).toString()).apply()
    researchNode(false)
   } else if(replayCheck) {
    researchReplayAllowed=false;prefs.edit().remove("war_researchRoute").apply()
    say("Zapisana trasa nie trafiła w badania. Wracam na początek i odświeżam trasę.")
    rewindResearch(0)
   } else if(at==0 && !rewound) {
    rewindResearch(0)
   } else if(at>=12) {
    if(rewound)fail("Nie rozpoznano obu badań po przeszukaniu drzewka. Możesz wskazać je ręcznie.")
    else rewindResearch(0)
   } else scrollResearch(false,brake=true) {seekResearch(at+1,rewound)}
  }}
 }
 private fun beginResearchSearch() {
  val saved=runCatching {JSONObject(prefs.getString("war_researchRoute",null)!!)}.getOrNull()
  val count=if(researchReplayAllowed && saved!=null)ResearchScrollGesture.replaySteps(saved.optInt("version"),saved.optInt("width"),saved.optInt("height"),saved.optInt("steps",-1),width,height)else null
  if(count==null){seekResearch(0,true);return}
  fun replay(at:Int) {
   if(at>=count){seekResearch(count,true,true);return}
   say("Odtwarzam zapisaną trasę badań: ruch ${at+1}/$count. Nie dotykaj ekranu.")
   scrollResearch(false,brake=true){replay(at+1)}
  };replay(0)
 }
 private var researchTopGate=ResearchMotionGate(1)
 private fun researchSample(image:Bitmap):IntArray = IntArray(64*64) {i ->
  val c=image.getPixel((image.width*(.08+(i%64+.5)*.84/64)).toInt(),(image.height*(.30+(i/64+.5)*.46/64)).toInt())
  ((c ushr 16 and 255)*299+(c ushr 8 and 255)*587+(c and 255)*114)/1000
 }
 private fun rewindResearch(at:Int) {
  if(at==0)researchTopGate=ResearchMotionGate(1)
  if(at<3) {
   say("Wracam na początek badań: szybki ruch ${at+1}/3. Nie dotykaj ekranu.")
   scrollResearch(true,brake=false) {if(at==2)waitForResearchStill {rewindResearch(at+1)} else rewindResearch(at+1)}
   return
  }
  say("Wracam na początek badań — sprawdzam koniec przewijania. Nie dotykaj ekranu.")
  panel?.visibility=View.INVISIBLE
  later(180) {frame(fresh=false) {image ->
   val top=researchTopGate.add(researchSample(image));image.recycle()
   if(top){beginResearchSearch();return@frame}
   if(at>=12){fail("Nie potwierdzono początku listy badań. Ponów lub wskaż badania ręcznie.");return@frame}
   panel?.visibility=View.VISIBLE
   scrollResearch(true,brake=false) {waitForResearchStill {rewindResearch(at+1)}}
  }}
 }
 private fun scrollResearch(toTop:Boolean,brake:Boolean=true,next:()->Unit) {
  val stroke=ResearchScrollGesture.create(width,height,toTop)
  val corridor=Rect((stroke.x-dp(16)).toInt(),minOf(stroke.fromY,stroke.toY).toInt()-dp(16),
   (stroke.x+dp(16)).toInt(),maxOf(stroke.fromY,stroke.toY).toInt()+dp(16))
  away(corridor) {
   val g=generation
   GestureConnection.drag(requireNotNull(session),stroke.x,stroke.fromY,stroke.x,stroke.toY,stroke.durationMs,brake) {result ->
    if(active&&g==generation)result.fold({
     if(!brake){shield.hide();later(60,next);return@fold}
     // Continue the SAME touch with a stationary release. No new tap on any node.
     GestureConnection.endHold(requireNotNull(session)) {released ->
      if(active&&g==generation){shield.hide();released.fold({later(180,next)},{fail(it.message?:"Nie zakończono przewijania")})}
     }
    },{shield.hide();fail(it.message?:"Przewijanie przerwane")})
   }
  }
 }
 private fun waitForResearchStill(next:()->Unit) {
  say("Czekam na zatrzymanie listy badań. Nie dotykaj ekranu.")
  panel?.visibility=View.INVISIBLE
  val gate=ResearchMotionGate()
  var finished=false;var brakeChecked=false
  fun sample(at:Int) {
   frame(fresh=false) {image ->
    if(finished){image.recycle();return@frame}
    if(!brakeChecked) {
     brakeChecked=true
     val ww=minOf(360,image.width);val hh=image.height*ww/image.width
     val small=Bitmap.createScaledBitmap(image,ww,hh,true);val pixels=IntArray(ww*hh)
     small.getPixels(pixels,0,ww,0,0,ww,hh)
     val point=ResearchBrakePoint.find(ww,hh,pixels)
     if(small!==image)small.recycle()
     if(point!=null) {
      val x=point.first.toFloat()*width/ww;val y=point.second.toFloat()*height/hh
      image.recycle()
      away(Rect(x.toInt()-8,y.toInt()-8,x.toInt()+8,y.toInt()+8)) {
       val g=generation
       GestureConnection.tap(requireNotNull(session),x,y) {result ->
        if(active && g==generation) {
         shield.hide();panel?.visibility=View.INVISIBLE
         result.fold({later(120){sample(0)}},{fail(it.message?:"Nie zatrzymano listy")})
        }
       }
      }
      return@frame
     }
    }
    val values=researchSample(image)
    image.recycle()
    if(gate.add(values)){finished=true;panel?.visibility=View.VISIBLE;next()}
    else {
     later(120){sample(at+1)}
    }
   }
  }
  later(120){sample(0)}
 }
 private fun researchNode(health:Boolean) {
  retryStep={researchNode(health)}
  pendingHealth=health
  step(if(health)3 else 2,if(health) "Zdrowie wojenne" else "Obrażenia wojenne")
  tap(if(health)"healthNode" else "damageNode",if(health)"Badanie Zdrowie Wojny Klanów" else "Badanie Obrażenia Wojny Klanów") {prepareResearchWindow(health)}
 }
 private fun prepareResearchWindow(health:Boolean) {
  retryStep={prepareResearchWindow(health)};repairKey="researchText"
  if(rect("researchText")!=null && rect("researchClose")!=null){consumeResearchWindow(health);return}
  say("Rozpoznaję wspólne okno badań: opis i czerwony X. Nie dotykaj ekranu.")
  panel?.visibility=View.INVISIBLE
  later(180) {frame {image ->
   val ww=minOf(360,image.width);val hh=image.height*ww/image.width
   val small=Bitmap.createScaledBitmap(image,ww,hh,true);val pixels=IntArray(ww*hh);small.getPixels(pixels,0,ww,0,0,ww,hh)
   val modal=eu.komars.forgescanner.model.ResearchWindowGeometry.find(ww,hh,pixels)
   fun scaled(r:eu.komars.forgescanner.model.PixelRegion)=Rect(r.left*width/ww,r.top*height/hh,r.right*width/ww,r.bottom*height/hh)
   if(modal!=null) {
    proposals["researchText"]=scaled(eu.komars.forgescanner.model.ResearchWindowGeometry.description(modal))
    val bounds=scaled(modal);val rad=(width*.075).toInt()
    val expected=Rect(bounds.centerX()-rad,(bounds.bottom-rad).coerceAtLeast(0),bounds.centerX()+rad,(bounds.bottom+rad).coerceAtMost(height))
    val close=eu.komars.forgescanner.vision.CloseButtonDetector().detect(image,expected)
    if(close!=null)proposals["researchClose"]=close else {proposals.remove("researchClose");prefs.edit().remove("war_researchClose").putBoolean("war_verified_researchClose",false).apply()}
    for(key in listOf("researchText","researchClose")) {
     val old=rect(key);val fresh=proposals[key]?:continue
     if(old!=null && !fresh.contains(old.centerX(),old.centerY()))prefs.edit().remove("war_$key").putBoolean("war_verified_$key",false).apply()
    }
   }
   if(modal==null){proposals.remove("researchText");proposals.remove("researchClose");prefs.edit().remove("war_researchText").remove("war_researchClose").apply()}
   if(small!==image)small.recycle();image.recycle();panel?.visibility=View.VISIBLE
   consumeResearchWindow(health)
  }}
 }
 private fun consumeResearchWindow(health:Boolean) {
  mark("researchText","Opis badania: nazwa i bieżący procent (bez darowizny)") {r ->
    if(preparingAll) {
     tap("researchClose","Czerwony X otwartego okna badania") {if(!health)researchNode(true)else leaveResearch {warScreen()}}
     return@mark
    }
    fun readValue(at:Int) {
    repairKey="researchText"
    read(r,variant=at) {text ->
    val value=WarPreparationModel.researchBonus(text,health)
    if(value==null){
     if(at<2){say("Ponawiam procent tego samego badania w innym kontraście.");readValue(at+1)}
     else {
      if(eu.komars.forgescanner.BuildConfig.DEBUG)runCatching {java.io.File(context.cacheDir,"research-rejected.txt").writeText(text)}
      fail("Nie odczytano właściwego badania i procentu. Popraw tylko obszar opisu lub ponów odczyt.")
     }
     return@read
    }
    if(health)ownHealth=value else ownDamage=value
    tap("researchClose","Czerwony X otwartego okna badania") {
     if(!health)researchNode(true)else {
      data.edit().putFloat("damage",ownDamage.toFloat()).putFloat("health",ownHealth.toFloat()).putLong("researchAt",System.currentTimeMillis()).apply()
      leaveResearch {warScreen()}
     }
    }
   }}
   readValue(0)
   
  }
 }

 private fun propose(key:String,target:Rect?) {
  if(target==null){proposals.remove(key);prefs.edit().remove("war_$key").putBoolean("war_verified_$key",false).apply();return}
  proposals[key]=target
  val old=rect(key)
  if(old!=null && !target.contains(old.centerX(),old.centerY()))prefs.edit().remove("war_$key").putBoolean("war_verified_$key",false).apply()
 }
 private fun navigationPixels(image:Bitmap,find:(Int,Int,IntArray)->List<eu.komars.forgescanner.model.PixelRegion>):List<Rect> {
  val ww=minOf(360,image.width);val hh=image.height*ww/image.width
  val small=Bitmap.createScaledBitmap(image,ww,hh,true);val pixels=IntArray(ww*hh);small.getPixels(pixels,0,ww,0,0,ww,hh)
  val result=find(ww,hh,pixels).map {Rect(it.left*width/ww,it.top*height/hh,it.right*width/ww,it.bottom*height/hh)}
  if(small!==image)small.recycle()
  return result
 }
 private fun warScreen() {
  retryStep={warScreen()}
  if(rect("clan")!=null){tap("clan","Zapisany przycisk"){warTab()};return}
  step(4,"Ekran wojny i flagi klanów");say("Rozpoznaję przycisk klanu na dolnym pasku.")
  panel?.visibility=View.INVISIBLE
  later(180) {frame {image ->
   val target=navigationPixels(image) {w,h,p ->listOfNotNull(eu.komars.forgescanner.model.WarNavigationGeometry.clanEntry(w,h,p))}.singleOrNull()
   image.recycle();panel?.visibility=View.VISIBLE;propose("clan",target)
   tap("clan","Przycisk klanu na dolnym pasku") {warTab()}
  }}
 }
 private fun warTab() {
  openSection="clan"
  retryStep={warTab()}
  if(rect("warTab")!=null){tap("warTab","Zapisany przycisk"){warFlags()};return}
  say("Szukam napisu Wojna w pasku zakładek klanu. Nie dotykaj ekranu.")
  panel?.visibility=View.INVISIBLE
  later(180) {frame {image ->
   val g=generation
   ocr.process(InputImage.fromBitmap(image,0)).addOnSuccessListener {result ->
    if(active && g==generation) {
     val words=result.textBlocks.flatMap {it.lines}.flatMap {it.elements}.mapNotNull {word ->word.boundingBox?.let {BattleOcrLine(word.text,it.left,it.top,it.right,it.bottom)}}
     val target=WarTabTextProposal.find(width,height,words)?.let {Rect(it.left,it.top,it.right,it.bottom)}
     propose("warTab",target);panel?.visibility=View.VISIBLE
     tap("warTab","Zakładka Wojna — pole do zatwierdzenia") {warFlags()}
    }
   }.addOnFailureListener {if(active && g==generation){propose("warTab",null);panel?.visibility=View.VISIBLE;tap("warTab","Wskaż zakładkę Wojna") {warFlags()}}}.addOnCompleteListener {image.recycle()}
  }}
 }
 private fun warFlags() {
  retryStep={warFlags()}
  if(rect("ourFlag")!=null && rect("enemyFlag")!=null) {
   if(rankingMode)clanRoster(false)
   else if(fullMode&&enemyDamage>0)clanRoster(fullSide)
   else if(testMode){enemyDamage=ownDamage;enemyHealth=ownHealth;clanRoster(if(fullMode)fullSide else false)}
   else if(WarPreparationModel.day()==null)completePreparation()else rivalInfo()
   return
  }
  say("Rozpoznaję dwie flagi w nagłówku wojny. Dostępność dnia 6 sprawdzam według czasu polskiego.")
  panel?.visibility=View.INVISIBLE
  later(180) {frame {image ->
   val targets=navigationPixels(image) {w,h,p ->eu.komars.forgescanner.model.WarNavigationGeometry.flags(w,h,p)?.let {listOf(it.first,it.second)}?:emptyList()}
   image.recycle();panel?.visibility=View.VISIBLE
   propose("ourFlag",targets.getOrNull(0));propose("enemyFlag",targets.getOrNull(1))
   mark("ourFlag","Lewa flaga naszego klanu") {
    mark("enemyFlag","Prawa flaga przeciwnika") {
     if(rankingMode)clanRoster(false)
     else if(testMode){enemyDamage=ownDamage;enemyHealth=ownHealth;clanRoster(if(fullMode)fullSide else false)}else if(WarPreparationModel.day()==null)completePreparation()else rivalInfo()
    }
   }
  }}
 }
 private fun rivalInfo() {
  retryStep={rivalInfo()}
  step(5,"Bonusy rywalizującego klanu")
  if(rect("rivalInfo")!=null && rect("rivalText")!=null && rect("infoOk")!=null) {
   tap("rivalInfo","Otwieram zapisaną premię rywala"){consumeRivalBonus()};return
  }
  observeScene {result ->result.fold({observed ->
   propose("rivalInfo",observed.fields["rivalInfo"])
   tap("rivalInfo","Małe i — Premia za rywalizujący klan") {
    observeScene {dialog ->dialog.fold({seen ->
     propose("rivalText",seen.fields["rivalText"]);propose("infoOk",seen.fields["infoOk"])
     if("bonus" !in seen.states){fail("Nie potwierdzono okna premii rywala.");return@fold}
     consumeRivalBonus()
    },{fail(it.message?:"Nie odczytano okna premii.")})}
   }
  },{fail(it.message?:"Nie odczytano ekranu wojny.")})}
 }
 private fun consumeRivalBonus() {
  mark("rivalText","Opis premii rywala z dwoma procentami") {r ->
      if(preparingAll) {tap("infoOk","Przycisk OK w oknie premii") {completePreparation()};return@mark}
      // Three contrast variants, like our own research; the failure shows the text read for diagnosis.
      fun readValue(at:Int) {read(r,variant=at) {text ->
      val bonus=WarPreparationModel.bonuses(text)
      fun accept(damage:Double,health:Double) {
       enemyDamage=damage;enemyHealth=health
       tap("infoOk","Przycisk OK w oknie premii") {
        data.edit().putFloat("enemyDamage",enemyDamage.toFloat()).putFloat("enemyHealth",enemyHealth.toFloat()).putString("day",WarPreparationModel.day()).apply()
        if(listMode)clanRoster(fullMode&&fullSide)else completePreparation()
       }
      }
      if(bonus==null){
       if(at<2){say("Ponawiam odczyt premii rywala w innym kontraście.");readValue(at+1);return@read}
       if(fullMode)fullStore.log("rival-bonus-rejected",JSONObject().put("text",text.take(400)))
       // Last resort: the user types the two numbers shown in the game window.
       say("Nie odczytano premii rywala. Wpisz obie liczby ręcznie.")
       val g=generation
       rivalBonusDialog.show(text,{d,h->if(active&&g==generation){fullStore.log("rival-bonus-manual",JSONObject().put("damage",d).put("health",h));accept(d,h)}}) {
        if(active&&g==generation){prefs.edit().remove("war_rivalText").apply();fail("Nie odczytano obu bonusów rywala. Odczytano: „${text.replace('\n',' ').take(140)}”")}
       }
       return@read
      }
      accept(bonus.first,bonus.second)
     }}
      readValue(0)
     }
 }

 private fun scrollRosterDown(r:Rect,rows:List<RosterObservation>,next:()->Unit) {
  if(fullMode){saveFull("scroll");fullStore.log("scroll",JSONObject().put("direction","down").put("visible",rows.size))}
  val distance=RosterScrollPlan.down(r.height(),height,rows.mapNotNull {it.anchorY})*rosterDownGain
  val x=kotlin.math.round(r.exactCenterX());val from=kotlin.math.round(r.bottom-r.height()*.08f);val to=kotlin.math.round(from-distance)
  away(Rect(r).apply {inset(-24,-24)}) {
   val g=generation
   GestureConnection.drag(requireNotNull(session),x,from,x,to,350,true) {result ->
    if(active&&g==generation)result.fold({brakeRoster(next)},{fail(it.message?:"Przewijanie przerwane")})
   }
  }
 }
 /** A single final stationary release replaces the extra held stroke introduced in Test 61. */
 private fun brakeRoster(next:()->Unit) {
  val g=generation
  GestureConnection.endHold(requireNotNull(session),360) {result ->if(active&&g==generation){shield.hide();result.fold({later(150,next)},{fail(it.message?:"Nie wyhamowano listy")})}}
 }
 private fun restoreRosterOverlap(r:Rect,rows:List<RosterObservation>,next:()->Unit) {
  val distance=RosterScrollPlan.backtrack(r.height(),height,rows.mapNotNull {it.anchorY})
  val x=kotlin.math.round(r.exactCenterX());val from=kotlin.math.round(r.top+r.height()*.10f);val to=kotlin.math.round(from+distance)
  rosterDownGain=(rosterDownGain*.85f).coerceAtLeast(if(fullMode&&fullRepairPass).65f else .75f)
  say("Ruch był za duży. Cofam o jeden wiersz, aby odzyskać wspólne postacie.")
  away(Rect(r).apply {inset(-24,-24)}) {
   val g=generation;GestureConnection.drag(requireNotNull(session),x,from,x,to,400,true) {result ->if(active&&g==generation)result.fold({brakeRoster(next)},{fail(it.message?:"Nie przywrócono wspólnych wierszy")})}
  }
 }
 /** The first two fast strokes may fling; the third ends held, braking without tapping a player. */
 private fun rewindRosterBatch(r:Rect,next:()->Unit) {
  val x=kotlin.math.round(r.exactCenterX());val from=kotlin.math.round(r.top+r.height()*.08f);val to=kotlin.math.round(from+RosterScrollPlan.rewind(r.height(),height));val g=generation
  fun stroke(at:Int) {
   if(!active||g!=generation)return
   GestureConnection.drag(requireNotNull(session),x,from,x,to,180,at==2) {result ->
    if(active&&g==generation)result.fold({
     if(at<2)later(35){stroke(at+1)}else GestureConnection.endHold(requireNotNull(session)) {ended ->if(active&&g==generation){shield.hide();ended.fold({later(150,next)},{fail(it.message?:"Przewijanie przerwane")})}}
    },{fail(it.message?:"Przewijanie przerwane")})
   }
  };stroke(0)
 }
 private fun clanRoster(enemy:Boolean) {
  if(fullMode){fullRepairPass=false;lastFullViewport=emptyList();stableFullViewport=0;rosterDownGain=.90f}
  retryStep={clanRoster(enemy)}
  step(6,if(enemy)"Lista rywali"else"Lista naszego klanu")
  tap(if(enemy)"enemyFlag"else"ourFlag",if(enemy)"Otwieram klan rywala"else"Otwieram nasz klan") {
   if(rect("roster")!=null && rect("rosterClose")!=null){consumeClanRoster(enemy);return@tap}
   observeScene {result ->result.fold({observed ->
    if("roster" !in observed.states){fail("Nie potwierdzono okna członków klanu.");return@fold}
    propose("roster",observed.fields["roster"]);propose("rosterClose",observed.fields["rosterClose"])
    consumeClanRoster(enemy)
   },{fail(it.message?:"Nie odczytano listy klanu.")})}
  }
 }
 private fun consumeClanRoster(enemy:Boolean) {
    if(preparingAll) {
     mark("roster","Przewijana lista członków — bez nagłówka i X") {
      tap("rosterClose","Czerwony X listy — wspólny dla obu klanów") {
       if(!enemy)clanRoster(true)else {rosterConfigured=true;completePreparation()}
      }
     }
    } else {
     retryStep={consumeClanRoster(enemy)};repairKey="roster"
     val list=rect("roster")?:proposals["roster"]
     val header=list?.let {RosterHeaderRegion.above(width,height,eu.komars.forgescanner.model.PixelRegion(it.left,it.top,it.right,it.bottom))}
     if(header==null){repairKey="roster";fail("Nie potwierdzono pola listy klanu. Popraw listę; odczyt nagłówka został wstrzymany.");return}
     readClanHeader(Rect(header.left,header.top,header.right,header.bottom)) {text,visual ->
     val count=WarPreparationModel.memberCount(text)
     if(count !in 1..50){fail("Nie odczytano licznika po trzech próbach. Zapisane profile zachowane; Kontynuuj ponowi tylko nagłówek.");return@readClanHeader}
     expected=count!!
     val clan=WarRosterProof.clan(text)
     if(clan==null){fail("Nie odczytano nazwy klanu z nagłówka. Ponów tylko nagłówek — listy są zachowane.");return@readClanHeader}
     if(enemy&&((ownClan?.let {WarResumePolicy.sameClan(it,clan)}==true)||WarRosterProof.sameHeader(ownHeader,visual))) {
      repairKey="enemyFlag";retryStep={tap("rosterClose","Zamykam listę naszego klanu"){clanRoster(true)}}
      fail("Ponownie otwarto nasz klan ($clan). Nie uznaję tej listy za przeciwników. Popraw bieżące pole: flagę rywala po prawej.");return@readClanHeader
     }
     if(enemy){enemyClan=clan;enemyExpected=expected}else{ownClan=clan;ourExpected=expected;ownHeader=visual}
     logRosterState("header")
     if(fullMode){fullSide=enemy;fullTarget=null;fullProcessed.removeAll {it.startsWith("$enemy|")};saveFull("roster",true);fullStore.log("roster-header",JSONObject().put("enemy",enemy).put("clan",clan).put("expected",expected))}
     if(expected !in 1..50){fail("Nie odczytano liczby członków klanu. Raport nie zostanie wysłany.");return@readClanHeader}
     mark("roster","Przewijana lista członków — bez nagłówka i X") {r ->scrolls=0;repeats=0;previous="";rosterTop=RosterEndGate(1,6);rosterEnd=RosterEndGate();manualListBoundary=false;rewindRoster(enemy,r)}
     }
    }
 }

 private fun setRosterControls(mode:Int) {
  rosterMode=mode;chrome?.listMode(mode in 1..2,mode==1)
  listControl?.apply {visibility=if(mode in 1..2)View.VISIBLE else View.GONE;text=if(mode==1)"Lista jest na górze"else"Zatrzymaj listę";isEnabled=true}
  if(mode!=0) {
   warning?.text="SKANUJĘ · NIE DOTYKAJ";warning?.textSize=11f
   stageLabel?.textSize=12f;status?.textSize=12f;status?.maxLines=2
  } else {
   warning?.text="NIE DOTYKAJ EKRANU\nTRWA AUTOMATYCZNY ODCZYT";warning?.textSize=15f
   stageLabel?.textSize=14f;status?.textSize=14f;status?.maxLines=4
  }
 }
 private fun rosterSample(image:Bitmap,r:Rect):IntArray = IntArray(64*128) {i ->
  val x=(r.left+(.18+(i%64+.5)*.77/64)*r.width()).toInt().coerceIn(0,image.width-1)
  val y=(r.top+(i/64+.5)*r.height()/128).toInt().coerceIn(0,image.height-1)
  val c=image.getPixel(x,y);((c ushr 16 and 255)*299+(c ushr 8 and 255)*587+(c and 255)*114)/1000
 }
 private fun retryRoster(enemy:Boolean,fromTop:Boolean) {
  mark("roster","Lista członków — od nagłówka do dolnej granicy przewijania") {r ->
   (if(enemy)enemies else ours).beginPass()
   manualListBoundary=false;rosterTop=RosterEndGate(1,6);rosterEnd=RosterEndGate();scrolls=0
   if(fromTop)rewindRoster(enemy,r)else readRoster(enemy,r)
  }
 }
 private fun rewindRoster(enemy:Boolean,r:Rect,attempt:Int=0) {
  if(attempt==0)(if(enemy)enemies else ours).beginPass()
  retryStep={retryRoster(enemy,true)};repairKey="roster";setRosterControls(1)
  say("${if(enemy)"Rywal: $enemyClan"else"Nasz klan: $ownClan"} · $expected członków. Wracam na początek.")
  away(r) {frame(fresh=false) {image ->
   val top=rosterTop.add(rosterSample(image,r));image.recycle()
   if(top || manualListBoundary){manualListBoundary=false;rosterEnd=RosterEndGate();readRoster(enemy,r);return@frame}
   if(attempt>=8){fail("Nie potwierdzono góry listy klanu. Zapisane pole zachowane — ponów albo popraw pole.");return@frame}
   rewindRosterBatch(r){rewindRoster(enemy,r,attempt+1)}
  }}
 }
 private fun finishRoster(enemy:Boolean) {
  val scanEnemy=!enemy&&!rankingMode&&(!fullMode||!fullEnemySwept)
  if(fullMode) {
   if(enemy)fullEnemySwept=true
   fullTarget=null
   fullSide=scanEnemy||enemy
   saveFull("clan-swept",true)
  }
  manualListBoundary=false;setRosterControls(0);logRosterState(if(enemy)"enemy-swept"else"own-swept")
  tap("rosterClose","Zamykam listę członków") {if(scanEnemy)clanRoster(true)else if(rankingMode)computeRanking(ours.values)else upload()}
 }
 private fun captureRoster(r:Rect,reuse:((RosterVisual)->RosterReading?)?,next:(IntArray,List<RosterObservation>)->Unit) {
  away(r) {shield.hide();later(220) {frame(fresh=false) {image ->
   val sample=rosterSample(image,r);val g=generation
   rosterReader.read(image,r,{active&&g==generation},reuse) {result ->
    if(active&&g==generation)result.fold({next(sample,it)},{fail(it.message?:"Nieczytelne pola graczy. Ponów bieżący widok.")})
   }
  }}}
 }
 private fun readRoster(enemy:Boolean,r:Rect,attempt:Int=0,recovered:Boolean=false) {
  retryStep={retryRoster(enemy,false)};repairKey="roster";setRosterControls(2)
  val list=if(enemy)enemies else ours
  say("${if(enemy)"Rywal"else"Nasz klan"}: ${list.size}/$expected postaci, dane ${list.resolvedCount}. Odczytuję pola…")
  val clan=if(enemy)enemyClan.orEmpty()else ownClan.orEmpty()
  val reuse:((RosterVisual)->RosterReading?)?=if(fullMode) {visual ->
   list.confirmedReading(visual)?.takeIf {reading ->
    fullStore.entry(enemy,clan,WarMember(requireNotNull(reading.name),requireNotNull(reading.power)))?.optBoolean("complete")==true
   }
  } else null
  captureRoster(r,reuse) {sample,rows ->
   val merged=if(fullMode)list.addUnorderedView(rows,expected) {member ->
    fullStore.entry(enemy,clan,member)?.optBoolean("complete")==true
   } else list.addView(rows,expected)
   if(fullMode&&merged.isSuccess){fullSide=enemy;saveFull("roster",true);fullStore.log("roster-read",JSONObject().put("enemy",enemy).put("visible",rows.size).put("known",list.size).put("resolved",list.resolvedCount))}
   if(merged.isFailure) {
    if(!recovered&&list.needsOverlapRecovery(rows)){restoreRosterOverlap(r,rows){readRoster(enemy,r,0,true)};return@captureRoster}
    if(attempt<2){say("Sprawdzam powtórzone awatary i nicki. Nie dodaję nowych postaci.");later(if(attempt==0)400 else 900){readRoster(enemy,r,attempt+1,recovered)}}
    else if(fullMode&&rosterRebuilt.add(enemy)) {
     // The saved list no longer matches the screen (someone left, joined or was swapped): read it anew once.
     list.clear();fullStore.log("roster-reset",JSONObject().put("enemy",enemy).put("reason","identity"))
     say("Zapis listy klanu jest nieaktualny — czytam ją od nowa. Zapisane profile zostają.")
     rosterTop=RosterEndGate(1,6);rosterEnd=RosterEndGate();scrolls=0;rewindRoster(enemy,r)
    }
    else if(fullMode) recoverFullCoverage(enemy,r,"Zapis listy klanu nadal nie zgadza się z ekranem. „Przeskanuj listę od góry” wyczyści go i przeczyta listę od nowa.")
    else failRosterEnd(enemy,"Nie udało się połączyć tego widoku z wcześniejszym. Zachowano ${list.size} postaci; nie dopisano powtórek. Ponów bieżący widok lub listę od góry.")
    return@captureRoster
   }
   if(fullMode)scanFullRows(enemy,r,rows) {finishRosterPage(enemy,r,rows,sample)}else finishRosterPage(enemy,r,rows,sample)
  }
 }
 private fun finishRosterPage(enemy:Boolean,r:Rect,rows:List<RosterObservation>,sample:IntArray) {
   val list=if(enemy)enemies else ours
   val stopped=rosterEnd.add(sample)
   if(fullMode) {
    val clan=if(enemy)enemyClan.orEmpty()else ownClan.orEmpty()
    val remaining=list.values.count {member ->
     fullId(enemy,member) !in fullProcessed&&fullStore.entry(enemy,clan,member)?.optBoolean("complete")!=true
    }
    if(list.size==expected&&list.resolvedCount==expected&&remaining==0){finishRoster(enemy);return}
    val viewport=list.visibleIndices(rows.size).zip(rows.map {it.anchorY?:-1})
    val same=viewport.isNotEmpty()&&viewport.size==lastFullViewport.size&&viewport.zip(lastFullViewport).all { (now,old) ->
     now.first==old.first&&kotlin.math.abs(now.second-old.second)<=3
    }
    stableFullViewport=if(same)stableFullViewport+1 else 0
    lastFullViewport=viewport
    if(manualListBoundary||stopped||stableFullViewport>=2||++scrolls>=40) {
     recoverFullCoverage(enemy,r,"Zebrano ${list.resolvedCount}/$expected członków. Pozostało $remaining profili do otwarcia.")
    } else scrollRosterDown(r,rows){readRoster(enemy,r)}
    return
   }
   if(list.size==expected && list.resolvedCount==expected){finishRoster(enemy);return}
   if(manualListBoundary){failRosterEnd(enemy,"Lista zatrzymana: ${list.size}/$expected postaci, kompletne dane ${list.resolvedCount}. Raport nie został wysłany.");return}
   if(list.size==expected){repairMissingRoster(enemy);return}
   if(stopped)failRosterEnd(enemy,"Koniec listy: ${list.size}/$expected postaci. Nie przewijam listy ponownie. Ponów bieżący widok albo wybierz ręcznie przejście od góry.")
   else if(++scrolls>=40)failRosterEnd(enemy,"Zatrzymano długie przewijanie: ${list.size}/$expected postaci, dane ${list.resolvedCount}.")
   else {say("${if(enemy)"Rywal"else"Nasz klan"}: ${list.size}/$expected postaci, dane ${list.resolvedCount}. Odczytuję cały widok i przesuwam dalej…");scrollRosterDown(r,rows){readRoster(enemy,r)}}

 }
 private fun recoverFullCoverage(enemy:Boolean,r:Rect,reason:String) {
  val list=if(enemy)enemies else ours
  fullStore.log("roster-coverage",JSONObject().put("enemy",enemy).put("expected",expected)
   .put("known",list.size).put("resolved",list.resolvedCount).put("second_pass",fullRepairPass).put("reason",reason))
  if(!fullRepairPass&&!manualListBoundary) {
   fullRepairPass=true
   rosterDownGain=.72f
   fullTarget=null
   fullCentered.clear()
   scrolls=0
   lastFullViewport=emptyList()
   stableFullViewport=0
   rosterTop=RosterEndGate(1,6)
   rosterEnd=RosterEndGate()
   // Retry failures once, preserving complete profiles and their snapshot power.
   fullProcessed.removeAll {it.startsWith("$enemy|")}
   rewindRoster(enemy,r)
  } else if(!enemy&&!fullEnemySwept&&list.size>0) {
   say(if(rankingMode)"Zapisuję braki i liczę ranking z dostępnych profili." else "Zapisuję braki własnego klanu i przechodzę do przeciwnika.")
   finishRoster(false)
  } else if(enemy&&list.size==expected&&list.resolvedCount==expected) {
   finishRoster(true)
  } else {
   failRosterEnd(enemy,reason+" Zapisane profile są zachowane.")
  }
 }
 private fun repairMissingRoster(enemy:Boolean,attempt:Int=0,processed:Set<Int> = emptySet()) {
  val list=if(enemy)enemies else ours
  retryStep={repairMissingRoster(enemy)};repairKey=null;setRosterControls(3)
  if(list.size==expected&&list.resolvedCount==expected){finishRoster(enemy);return}
  val targets=list.repairTargets.filter {it.index !in processed}.take(6)
  if(targets.isEmpty()) {
   if(attempt<1&&list.repairTargets.isNotEmpty()){repairMissingRoster(enemy,attempt+1);return}
   failRosterEnd(enemy,"Mam wszystkie $expected postaci. Brakuje danych dla ${list.unresolvedIndices.size}. Uzupełnij tylko te wpisy — pozostali gracze są zachowani.");return
  }
  say("Mam $expected/$expected postaci. Poprawiam tylko ${list.unresolvedIndices.size} brakujących wpisów, bez przewijania listy.")
  val g=generation
  rosterReader.retry(targets.map {it.snapshot},attempt,{active&&g==generation}) {result ->
   if(!active||g!=generation)return@retry
   result.fold({values ->targets.forEachIndexed {i,target ->list.repair(target.index,values[i])};repairMissingRoster(enemy,attempt,processed+targets.map {it.index})},
    {failRosterEnd(enemy,"Nie odczytano brakujących wpisów. Pozostałe dane są zachowane; możesz uzupełnić braki ręcznie.")})
  }
 }
 private fun reviewMissingRoster(enemy:Boolean) {
  val list=if(enemy)enemies else ours;val index=list.unresolvedIndices.firstOrNull()
  if(index==null){if(list.size==expected)recoverAction {finishRoster(enemy)};return}
  val g=generation
  rosterReview.show(index,list.partial(index),list.snapshot(index),save={name,power ->g==generation&&list.setManual(index,name,power)},complete={
   if(g==generation&&panel!=null){if(list.resolvedCount==expected&&list.size==expected)recoverAction {finishRoster(enemy)}else reviewMissingRoster(enemy)}
  })
 }
 private fun failRosterEnd(enemy:Boolean,message:String) {
  fail(message)
  val list=if(enemy)enemies else ours
  if(list.unresolvedIndices.isNotEmpty())recovery?.addView(ScannerAppearance.overlayAction(context,"Uzupełnij braki (${list.unresolvedIndices.size})") {reviewMissingRoster(enemy)})
  // A rescan from the top drops the stale saved list (profiles stay in the store) so it cannot block identity again.
  recovery?.addView(ScannerAppearance.overlayAction(context,"Przeskanuj listę od góry") {recoverAction {
   list.clear();fullProcessed.removeAll {it.startsWith("$enemy|")};fullRepairPass=false
   if(fullMode)fullStore.log("roster-reset",JSONObject().put("enemy",enemy).put("reason","manual"))
   retryRoster(enemy,true)
  }})
 }
 private fun completePreparation() {
  if(preparingAll && !rosterConfigured) {clanRoster(false);return}
  if(preparingAll) {
   // Setup only configures fields; the character and tech are read when the user starts a scan.
   say("Pola ustawione. Wracam do głównego ekranu gry.")
   returnHome {hide();app.battleOverlay.configurationDone()}
  } else {say("Badania i pola zapisane.");hide();app.battleOverlay.show("self")}
 }


 private fun centerFullTarget(r:Rect,next:()->Unit) {
  fullStore.log("center-member",JSONObject().put("target",fullTarget?:JSONObject.NULL));saveFull("seek")
  say("Przesuwam kartę $fullTarget do widocznego obszaru. Zapisane pola pozostają zachowane.")
  away(r){val g=generation;val x=kotlin.math.round(r.exactCenterX());val from=kotlin.math.round(r.bottom-r.height()*.12f)
   GestureConnection.drag(requireNotNull(session),x,from,x,kotlin.math.round(from-WarResumePolicy.centerDistance(r.height())),300,true) {result->if(active&&g==generation)result.fold({brakeRoster(next)},{fail(it.message?:"Nie przesunięto karty; punkt wznowienia zachowany.")})}
  }
 }
 private fun resumeFullWork() {
  val g=generation
  val saved=fullStore.state()?:run {retryStep={retryRoster(fullSide,false)};retryRoster(fullSide,false);return}
  ownClan=saved.optString("own_clan").takeIf {it.isNotBlank()};enemyClan=if(saved.isNull("enemy_clan"))null else saved.optString("enemy_clan")
  ourExpected=saved.optInt("own_expected");enemyExpected=saved.optInt("enemy_expected")
  fullSide=saved.optBoolean("enemy");fullTarget=if(saved.isNull("target"))null else saved.optString("target").takeIf {it.isNotBlank()}
  fullProcessed=(0 until (saved.optJSONArray("processed")?.length()?:0)).map {saved.getJSONArray("processed").getString(it)}.toMutableSet()
  ours.restore(fullStore.roster(false),repairDuplicates=true);enemies.restore(fullStore.roster(true),repairDuplicates=true)
  fullEnemySwept=saved.optBoolean("enemy_swept")
  fullRepairPass=false
  lastFullViewport=emptyList()
  stableFullViewport=0
  rosterDownGain=.90f
  scrolls=0
  ownDamage=saved.optDouble("own_damage");ownHealth=saved.optDouble("own_health");enemyDamage=saved.optDouble("enemy_damage");enemyHealth=saved.optDouble("enemy_health")
  expected=if(fullSide)enemyExpected else ourExpected
  retryStep={resumeFullWork()};repairKey=null;fullCentered.clear();step(6,"Wznawiam ${fullTarget?:if(fullSide)"listę rywali"else"nasz klan"}")
  fullStore.log("resume",JSONObject().put("target",fullTarget?:JSONObject.NULL).put("known",if(fullSide)enemies.size else ours.size).put("expected",expected))
  cleanFrame {image ->
   val seen=scene(image);val profile=memberDetector.observe(image).profile;val close=if(profile)eu.komars.forgescanner.vision.CloseButtonDetector().detect(image)else null;image.recycle()
   if(saved.optInt("width")!=width||saved.optInt("height")!=height){fail("Zmieniono rozmiar obrazu. Profile zapisane; popraw pola przed wznowieniem.");return@cleanFrame}
   GestureConnection.requestArm(width,height) {armed->if(!active||g!=generation)return@requestArm;session=armed.getOrNull();if(session==null){fail(armed.exceptionOrNull()?.message?:"Włącz dostępność i ponów wznowienie.");return@requestArm}
    if("roster" in seen.states) {
     val r=rect("roster")?:run {fail("Brak pola listy. Zapisany postęp pozostaje zachowany.");return@requestArm}
     val header=RosterHeaderRegion.above(width,height,eu.komars.forgescanner.model.PixelRegion(r.left,r.top,r.right,r.bottom))?:run {fail("Nie potwierdzono nagłówka listy.");return@requestArm}
     readClanHeader(Rect(header.left,header.top,header.right,header.bottom)) {text,_ ->
      val count=WarPreparationModel.memberCount(text);val clan=WarRosterProof.clan(text);val wanted=if(fullSide)enemyClan else ownClan
      if(count in 1..50&&clan!=null&&(wanted==null||!WarResumePolicy.sameClan(clan,wanted))) {
       val other=if(fullSide)ownClan else enemyClan
       if(other!=null&&WarResumePolicy.sameClan(clan,other)){screenFlow.observe(eu.komars.forgescanner.model.GameNavigationGraph.Screen.ROSTER);tap("rosterClose","Przechodzę do zapisanego klanu"){clanRoster(fullSide)};return@readClanHeader}
      }
      if(count !in 1..50||clan==null||wanted==null||!WarResumePolicy.sameClan(clan,wanted)){fail("Nie potwierdzono zapisanego klanu lub licznika. Postęp zachowany — otwórz jego listę i kontynuuj.");return@readClanHeader}
      // Members left or joined since the saved run: that side's list is read anew (profiles stay).
      if(count!=expected){(if(fullSide)enemies else ours).clear();fullStore.log("roster-changed",JSONObject().put("enemy",fullSide).put("saved",expected).put("now",count))}
      expected=count!!;if(fullSide)enemyExpected=expected else ourExpected=expected
      screenFlow.observe(eu.komars.forgescanner.model.GameNavigationGraph.Screen.ROSTER)
      fullTarget=null
      fullProcessed.removeAll {it.startsWith("$fullSide|")}
      rosterTop=RosterEndGate(1,6);rosterEnd=RosterEndGate();manualListBoundary=false
      saveFull("resume-sweep",true)
      rewindRoster(fullSide,r)
     }
    }else if(profile) {
     screenFlow.observe(eu.komars.forgescanner.model.GameNavigationGraph.Screen.PROFILE)
     fullProfileAnchor=close?.let {MemberProfileAnchor(fullTarget.orEmpty(),width,height,eu.komars.forgescanner.model.PixelRegion(it.left,it.top,it.right,it.bottom))}
     closeFullProfile(knownProfile=true) {resumeFullWork()}
    }else if("researchModal" in seen.states||"modal" in seen.states){closeFullProfile {resumeFullWork()}}
    else if("war" in seen.states){openSection="clan";screenFlow.observe(eu.komars.forgescanner.model.GameNavigationGraph.Screen.WAR);warFlags()}
    else if("home" in seen.states){openSection=null;screenFlow.observe(eu.komars.forgescanner.model.GameNavigationGraph.Screen.HOME);warScreen()}
    else if(seen.states.any {it in setOf("researchTabs","tree","categories")}){openSection="science";returnHome {warScreen()}}
    else fail("Nie potwierdzono bieżącego okna. Postęp zachowany — zamknij szczegóły lub otwórz listę klanu i wybierz Kontynuuj.")
   }
  }
 }

 private fun scanFullRows(enemy:Boolean,r:Rect,rows:List<RosterObservation>,next:()->Unit) {
  val list=if(enemy)enemies else ours;val indices=list.visibleIndices(rows.size);val clan=if(enemy)enemyClan!!else ownClan!!;val g=generation
  val preferred=fullTarget?.let {target->indices.indexOfFirst {index->list.partial(index).name?.let(BattleText::normalize)==BattleText.normalize(target)}}
  val order=rows.indices.sortedBy {if(it==preferred)-1 else it}
  if(preferred==null||preferred<0)fullTarget=null
  fun visit(position:Int) {
   if(!active||g!=generation)return
   // Before opening anyone on this page: ask the bot server backup for members this phone has nothing for.
   if(position==0&&app.mobile.paired) {
    val missing=order.mapNotNull {idx->indices.getOrNull(idx)?.takeUnless {it in list.unresolvedIndices}?.let {list.partial(it)}?.let {p->if(p.name!=null&&p.power!=null)WarMember(p.name,p.power)else null}}
     .filter {fullStore.entry(enemy,clan,it)?.optBoolean("complete")!=true&&fullId(enemy,it) !in fullProcessed&&fullId(enemy,it) !in serverAsked}
    if(missing.isNotEmpty()) {serverAsked+=missing.map {fullId(enemy,it)};say("Sprawdzam profile zapisane na serwerze klanu…");fetchShared(enemy,clan,missing) {if(active&&g==generation)visit(0)};return}
   }
   val at=order.getOrNull(position)?:run {next();return}
   val partial=indices.getOrNull(at)?.takeUnless {it in list.unresolvedIndices}?.let {list.partial(it)}
   val member=partial?.let {if(it.name!=null&&it.power!=null)WarMember(it.name,it.power)else null}
   if(member==null){visit(position+1);return}
   if(fullStore.entry(enemy,clan,member)?.optBoolean("complete")==true||fullId(enemy,member) in fullProcessed){if(fullTarget?.let {BattleText.normalize(it)==BattleText.normalize(member.name)}==true)fullTarget=null;visit(position+1);return}
   fun defer(reason:String) {
    fullStore.log("member-deferred",JSONObject().put("name",member.name).put("reason",reason))
    fullProcessed+=fullId(enemy,member)
    fullTarget=null
    saveFull("roster")
    visit(position+1)
   }
   val target=rows[at];fullProfileAnchor=null;fullSide=enemy;fullTarget=member.name;saveFull("member")
   fullStore.log("member-start",JSONObject().put("enemy",enemy).put("name",member.name).put("index",indices[at]))
   say("${if(enemy)"Rywal"else"Nasz klan"}: profil ${indices[at]+1}/$expected · ${member.name}. Nie dotykaj gry.")
   step(6,"${if(enemy)"Rywal"else"Nasi"} · profil ${indices[at]+1}/$expected · ${member.name}")
   retryStep={resumeFullWork()};repairKey=null
   away(r) {shield.hide();frame {image ->
    val pixels=IntArray(r.width()*r.height());image.getPixels(pixels,0,r.width(),r.left,r.top,r.width(),r.height())
    val matches=RosterCardLayout.fullCards(r.width(),r.height(),pixels).filter {it.visual.distance(target.visual)!=null}
    if(matches.size!=1) {
     runCatching {val crop=Bitmap.createBitmap(image,r.left,r.top,r.width(),r.height());java.io.File(context.cacheDir,"full-war-last-rejected.png").outputStream().use {crop.compress(Bitmap.CompressFormat.PNG,100,it)};if(crop!==image)crop.recycle()};image.recycle()
     fullStore.log("member-not-visible",JSONObject().put("name",member.name).put("anchor",target.anchorY?:-1))
     defer("Nie potwierdzono awatara w świeżej klatce.");return@frame
    }
    image.recycle()
    if(!WarResumePolicy.safe(matches.single().avatar,r.height())) {
     if(fullCentered.add(fullId(enemy,member))) {
      if(matches.single().avatar.top<r.height()/2)restoreRosterOverlap(r,rows){readRoster(enemy,r)}
      else centerFullTarget(r){readRoster(enemy,r)}
     } else defer("Karta przy krawędzi pola.")
     return@frame
    }
    val avatar=matches.single().avatar;val box=Rect(r.left+avatar.left,r.top+avatar.top,r.left+avatar.right,r.top+avatar.bottom)
    verifiedTap("memberAvatar",box) {
     val savedDraft=fullStore.entry(enemy,clan,member)?.optJSONObject("draft")
     shield.hide();app.battleOverlay.scanBatch(panel!!,status,{chrome?.dragging==true},resume=savedDraft,expectedName=member.name,navigation={close ->if(active&&g==generation)fullProfileAnchor=MemberProfileAnchor(member.name,width,height,eu.komars.forgescanner.model.PixelRegion(close.left,close.top,close.right,close.bottom))},checkpoint={draft ->
      if(active&&g==generation){fullStore.save(enemy,clan,member,null,draft,"Odczyt w toku");saveFull("profile");fullStore.log("profile-fields",JSONObject().put("name",member.name).put("sections",draft.optJSONObject("_sections")).put("skills",draft.optJSONArray("skillScans")?.length()?:0).put("fairy",draft.optJSONObject("fairy")))}
     }) {result ->
      if(!active||g!=generation)return@scanBatch
      val draft=result.getOrNull()?:(result.exceptionOrNull() as? BatchScanException)?.draft?:savedDraft
      val accepted=if(draft!=null&&result.isSuccess)runCatching {FullProfileCodec.accept(member.name,draft)}else Result.failure(result.exceptionOrNull()?:IllegalStateException("Brak profilu."))
      runCatching {fullStore.save(enemy,clan,member,accepted.getOrNull(),draft,accepted.exceptionOrNull()?.message)}.onFailure {fail(it.message?:"Nie zapisano profilu.");return@scanBatch}
      if(accepted.isSuccess&&fullStore.pendingShare().length()>=10)flushShared()
      val skills=draft?.optJSONArray("skillScans")
      val quickSkills=if(skills==null)0 else (0 until skills.length()).count {skills.optJSONObject(it)?.optBoolean("fromProfile")==true}
      fullStore.log("member-saved",JSONObject().put("name",member.name).put("complete",accepted.isSuccess)
       .put("skills_from_profile",quickSkills).put("reason",accepted.exceptionOrNull()?.message?:""))
      val reason=result.exceptionOrNull()?.message.orEmpty()
      if(CaptureConnection.state!=CaptureState.READY||!GestureConnection.isConnected||reason.contains("dotknię")||reason.contains("Gest został przerwany")){fail("Skan wstrzymany. Zapisane profile zachowane. $reason");return@scanBatch}
      fun closeAndNext() {GestureConnection.requestArm(width,height) {armed ->if(active&&g==generation){session=armed.getOrNull();if(session==null)fail(armed.exceptionOrNull()?.message?:"Ponownie włącz dostępność.")else {saveFull("closing-profile");closeFullProfile(knownProfile=result.isSuccess) {fullProcessed+=fullId(enemy,member);fullTarget=null;saveFull("roster");later(180){visit(position+1)}}}}}}
      // Automatic reading failed again in the last retry round: the profile is still open in the game, so ask the player to fill it in.
      if(accepted.isFailure&&fullAutoRetry>=maxAutoRetry) {
       fullStore.log("member-manual",JSONObject().put("name",member.name).put("reason",accepted.exceptionOrNull()?.message?:""))
       say("Nie odczytano profilu ${member.name} po $maxAutoRetry próbach. Uzupełnij go ręcznie — profil jest otwarty w grze.")
       fullEditor.reviewExternal(draft?:JSONObject().put("name",member.name),onCancelled={if(active&&g==generation)closeAndNext()}) {profile ->
        if(!active||g!=generation)return@reviewExternal
        profile.put("name",member.name)
        runCatching {fullStore.save(enemy,clan,member,profile,profile,null)}.onFailure {fail(it.message?:"Nie zapisano profilu.");return@reviewExternal}
        fullStore.log("member-manual-saved",JSONObject().put("name",member.name));fullResult=null;uploadRequestId=null
        closeAndNext()
       }
       return@scanBatch
      }
      closeAndNext()
     }
    }
   }}
  };visit(0)
 }
 private fun closeFullProfile(attempt:Int=0,knownProfile:Boolean=false,next:()->Unit) {
  val g=generation
  val anchored=fullProfileAnchor?.closeFor(fullTarget.orEmpty(),width,height,knownProfile)
  if(anchored!=null){fullStore.log("member-close-anchor",JSONObject().put("target",fullTarget?:JSONObject.NULL));screenFlow.observe(eu.komars.forgescanner.model.GameNavigationGraph.Screen.PROFILE);verifiedTap("memberProfileClose",Rect(anchored.left,anchored.top,anchored.right,anchored.bottom)){fullProfileAnchor=null;next()};return}
  if(attempt>3){fail("Nie potwierdzono powrotu do listy klanu. Zamknij szczegóły i wybierz Kontynuuj. Profile zapisane.");return}
  cleanFrame {image ->
   val observedScene=scene(image);val roster=observedScene.states.contains("roster")
   if(roster){image.recycle();fullProfileAnchor=null;screenFlow.observe(eu.komars.forgescanner.model.GameNavigationGraph.Screen.ROSTER);next();return@cleanFrame}
   val observed=memberDetector.observe(image)
   val close=eu.komars.forgescanner.vision.CloseButtonDetector().detect(image);image.recycle()
   val detail=observed.panel
   if(observed.profile&&close!=null) {screenFlow.observe(eu.komars.forgescanner.model.GameNavigationGraph.Screen.PROFILE);verifiedTap("memberProfileClose",close){fullProfileAnchor=null;next()};return@cleanFrame}
   val outside=if(detail!=null&&!observed.profile&&detail.width>detail.height*1.5)WeaponDetailLayout.outsideArea(detail,width,height)else null
   val target=outside?.let {Rect(it.left,it.top,it.right,it.bottom)}?:close?.takeIf {observedScene.states.any {it in listOf("researchModal","bonus")}}
   if(target==null){fail("Nie znaleziono bezpiecznego zamknięcia szczegółów. Zapis profili zachowany.");return@cleanFrame}
   away(target) {GestureConnection.tap(requireNotNull(session),target.exactCenterX(),target.exactCenterY()) {result ->if(active&&g==generation)result.fold({later(220){closeFullProfile(attempt+1,next=next)}},{fail(it.message?:"Przerwano zamknięcie szczegółów.")})}}
  }
 }
 /** Sides whose member list was already rebuilt in this run after an identity mismatch (once per side). */
 private val rosterRebuilt=mutableSetOf<Boolean>()
 /** Members already asked from the server backup in this run (each member at most once). */
 private val serverAsked=mutableSetOf<String>()
 private fun fetchShared(enemy:Boolean,clan:String,members:List<WarMember>,done:()->Unit) {
  val handler=android.os.Handler(android.os.Looper.getMainLooper())
  Thread {
   val got=runCatching {
    val want=JSONArray().apply {members.forEach {put(JSONObject().put("enemy",enemy).put("clan",clan).put("name",it.name).put("power",it.power))}}
    val response=app.mobile.profilesBlocking(JSONObject().put("want",want))
    val list=response.optJSONArray("profiles")?:JSONArray();var n=0
    for(i in 0 until list.length()){val o=list.getJSONObject(i);val member=members.firstOrNull {BattleText.normalize(it.name)==BattleText.normalize(o.optString("name"))}?:continue
     o.optJSONObject("profile")?.let {fullStore.importShared(enemy,clan,member,it,o.optLong("savedAt"));n++}}
    n
   }.getOrDefault(0)
   fullStore.log("shared-profiles",JSONObject().put("enemy",enemy).put("asked",members.size).put("received",got))
   handler.post(done)
  }.apply {isDaemon=true}.start()
 }
 /** Sends complete profiles to the bot server backup (best effort, in the background). */
 private fun flushShared() {
  if(!app.mobile.paired)return
  Thread {runCatching {
   val batch=fullStore.pendingShare();if(batch.length()==0)return@runCatching
   val slice=JSONArray();for(i in 0 until minOf(120,batch.length()))slice.put(batch.get(i))
   app.mobile.profilesBlocking(JSONObject().put("save",slice));fullStore.clearShared(slice.length())
  }}.apply {isDaemon=true}.start()
 }
 private fun fullEntries():List<Triple<Boolean,WarMember,JSONObject?>> = ours.values.map {Triple(false,it,fullStore.entry(false,ownClan!!,it))}+enemies.values.map {Triple(true,it,fullStore.entry(true,enemyClan!!,it))}
 private fun addFullRecovery() {
  val entries=fullEntries();val missing=entries.filter {it.third?.optBoolean("complete")!=true}
  if(entries.isNotEmpty())recovery?.addView(ScannerAppearance.overlayAction(context,"Profile: ${entries.size-missing.size}/${entries.size} · sprawdź / popraw") {
   fullEditor.choose(entries.sortedBy {it.third?.optBoolean("complete")==true}.map {(enemy,member,entry)->"${if(enemy)"Rywal"else"Nasi"} · ${member.name}: ${entry?.optString("error").orEmpty().take(70)}" to {
    fullEditor.reviewExternal(entry?.optJSONObject("profile")?:entry?.optJSONObject("draft")?:JSONObject().put("name",member.name)) {profile ->
     profile.put("name",member.name);fullStore.save(enemy,if(enemy)enemyClan!!else ownClan!!,member,profile,profile,null);fullResult=null;uploadRequestId=null;retryStep={computeFullWar()};clearRecoveryActions();addFullRecovery()
    }
   }})
  })
  // Only offered when something is actually missing; a complete list has nothing to resume.
  if(missing.isNotEmpty())recovery?.addView(ScannerAppearance.overlayAction(context,"Doczytaj brakujące profile (${missing.size})") {
   missing.firstOrNull()?.let {(enemy,member,_)->fullSide=enemy;fullTarget=member.name}
   missing.forEach {(enemy,member,_)->fullProcessed.remove(fullId(enemy,member))};saveFull("member",true)
   if(fullComputing){context.startActivity(android.content.Intent(context,BattleActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));stop()}
   else recoverAction {resumeFullWork()}
  })
  recovery?.addView(ScannerAppearance.overlayAction(context,"Przelicz zapisane profile") {active=true;generation++;fullCancel=java.util.concurrent.atomic.AtomicBoolean(false);computeFullWar()})
 }
 /**
  * Keep reading until every profile is complete: missing members are revisited automatically
  * (bounded rounds) before any simulation. Power is only the last resort for what still fails.
  */
 private fun retryMissingAutomatically(ownOnly:Boolean):Boolean {
  if(fullAutoRetry>=maxAutoRetry||CaptureConnection.state!=CaptureState.READY)return false
  val missing=fullEntries().filter {(enemy,_,entry)->(!ownOnly||!enemy)&&entry?.optBoolean("complete")!=true}
  if(missing.isEmpty())return false
  fullAutoRetry++
  fullStore.log("auto-retry",JSONObject().put("round",fullAutoRetry).put("missing",JSONArray(missing.map {it.second.name})))
  say("Doczytuję brakujące profile: ${missing.size}. Próba $fullAutoRetry/$maxAutoRetry.")
  val first=missing.firstOrNull {!it.first}?:missing.first()
  fullSide=first.first;fullTarget=first.second.name
  missing.forEach {(enemy,member,_)->fullProcessed.remove(fullId(enemy,member))}
  saveFull("member",true)
  later(300) {resumeFullWork()}
  return true
 }
 private fun computeFullWar() {
  retryStep={computeFullWar()};repairKey=null;setRosterControls(0);clearRecoveryActions()
  if(retryMissingAutomatically(ownOnly=false))return
  val own=ours.values;val rival=enemies.values
  val complete=fullEntries().count {it.third?.optBoolean("complete")==true}
  if(fullEntries().none { !it.first&&it.third?.optBoolean("complete")==true }||fullEntries().none {it.first&&it.third?.optBoolean("complete")==true}) {
   fail("Zapisano ${own.size+rival.size} kart, ale tylko $complete kompletnych profili. Popraw braki lub ponów ich skan; pozostałe profile są zapisane.");addFullRecovery();return
  }
  fullComputing=true;shield.hide();app.setScannerBusy("war",false);CaptureConnection.stop()
  warning?.text="OBLICZAM ZAPISANE PROFILE";warning?.visibility=View.VISIBLE;step(7,"Symulacje wszystkich par")
  val g=generation;val cancel=fullCancel;val capturedOwnClan=ownClan!!;val capturedEnemyClan=enemyClan!!
  val ownBonuses=WarBonus(ownDamage,ownHealth);val enemyBonuses=WarBonus(enemyDamage,enemyHealth)
  fullWorker.execute {
   val outcome=runCatching {FullWarComputation(fullStore).run(own,rival,{enemy,member->fullStore.entry(enemy,if(enemy)capturedEnemyClan else capturedOwnClan,member)?.optJSONObject("profile")},ownBonuses,enemyBonuses,{cancel.get()}) {at,total->if(at%5==0||at==total)h.post {if(active&&g==generation)say("Symulacje $at/$total · kompletne profile: $complete/${own.size+rival.size}. Odczyt ekranu wyłączony.")}}}
   h.post {if(active&&g==generation)outcome.fold({fullResult=it;upload()},{if(it !is java.util.concurrent.CancellationException){fail(it.message?:"Nie udało się przeliczyć par.");addFullRecovery()}})}
  }
 }

 private fun computeRanking(members:List<WarMember>) {
  val clan=ownClan?:run {fail("Brak nazwy naszego klanu.");return}
  retryStep={computeRanking(members)};repairKey=null;setRosterControls(0);clearRecoveryActions()
  if(retryMissingAutomatically(ownOnly=true))return
  data.edit().putString("rankingRoster",JSONObject().put("clan",clan).put("members",JSONArray().apply {members.forEach {put(JSONObject().put("name",it.name).put("power",it.power))}}).toString()).apply()
  val profiles=members.map {fullStore.entry(false,clan,it)?.optJSONObject("profile")}
  val complete=profiles.count {it!=null}
  if(complete<2){fail("Kompletne profile: $complete/${members.size}. Zeskanuj klan ponownie — zapisane profile zostaną użyte.");return}
  fullComputing=true;shield.hide();app.setScannerBusy("war",false);CaptureConnection.stop()
  warning?.text="OBLICZAM RANKING";warning?.visibility=View.VISIBLE;step(7,"Ranking · każdy z każdym")
  val g=generation;val cancel=fullCancel
  fullWorker.execute {
   val outcome=runCatching {FullWarComputation(fullStore).ranking(members,{m->profiles[members.indexOf(m)]},{cancel.get()}) {at,total->if(at%10==0||at==total)h.post {if(active&&g==generation)say("Walki $at/$total · kompletne profile: $complete/${members.size}. Odczyt ekranu wyłączony.")}}}
   h.post {if(active&&g==generation)outcome.fold({uploadRanking(clan,members,profiles,it)},{if(it !is java.util.concurrent.CancellationException){fullComputing=false;fail(it.message?:"Nie udało się policzyć rankingu.")}})}
  }
 }
 private fun uploadRanking(clan:String,members:List<WarMember>,profiles:List<JSONObject?>,simulation:JSONObject) {
  fullComputing=false;retryStep={uploadRanking(clan,members,profiles,simulation)};repairKey=null;step(7,"Ranking klanu")
  val body=JSONObject().put("request_id",UUID.randomUUID().toString()).put("observed_at",Instant.now().toString()).put("clan",clan)
   .put("members",JSONArray().apply {members.forEach {put(JSONObject().put("name",it.name).put("power",it.power))}}).put("member_count",members.size)
   .put("profiles",JSONArray().apply {profiles.forEach {put(it?:JSONObject.NULL)}}).put("simulation",simulation)
  say("Wysyłam ranking: ${simulation.optInt("complete")}/${members.size} kompletnych profili.")
  val g=generation
  app.mobile.sendClanRanking(body) {result ->
   fullStore.log("upload",JSONObject().put("kind","ranking").put("members",members.size).put("ok",result.isSuccess).put("error",result.exceptionOrNull()?.message?:JSONObject.NULL))
   if(active&&g==generation)result.fold({status?.text="Ranking zapisany. Otwórz /report clan-ranking na Discordzie.";warning?.visibility=View.GONE;active=false;addReturnToApp()},{fail(it.message?:"Nie udało się wysłać rankingu.")})}
 }
 private fun confirmListUpdate() {
  val g=generation
  status?.text="Lista na tę wojnę już istnieje na Discordzie."
  val dialog=android.app.AlertDialog.Builder(android.view.ContextThemeWrapper(context,eu.komars.forgescanner.R.style.ScannerDialogTheme))
   .setTitle("Lista na tę wojnę już istnieje — co zrobić?")
   // Four choices: replace with or without @everyone, only refresh the preview (official list stays), or nothing.
   .setItems(arrayOf("Zaktualizuj i pinguj @everyone","Zaktualizuj (bez pingu)","Tylko podgląd — oficjalna lista zostaje","Nie aktualizuj")) {_,which -> if(active&&g==generation){
    uploadRequestId=null
    when(which) {
     0->upload(overwrite=true,notify=true)
     1->upload(overwrite=true,notify=false)
     2->upload(previewOnly=true)
     else->{status?.text="Zostawiono obecną listę na Discordzie. Odczyt zachowany.";warning?.visibility=View.GONE;active=false}
    }
   }}
   .setOnCancelListener {if(active&&g==generation){status?.text="Zostawiono obecną listę na Discordzie.";active=false}}.create()
  dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);dialog.show()
 }
 private fun logRosterState(phase:String) {
  if(eu.komars.forgescanner.BuildConfig.DEBUG)runCatching {
   java.io.File(context.cacheDir,"war-rosters-last.json").writeText(JSONObject().put("phase",phase).put("own_clan",ownClan).put("enemy_clan",enemyClan).put("own_expected",ourExpected).put("enemy_expected",enemyExpected).put("own_cards",ours.size).put("enemy_cards",enemies.size).put("own_complete",ours.resolvedCount).put("enemy_complete",enemies.resolvedCount).toString())
  }
 }
 /** Management (roles from /setup or administrators) may redo the Sunday list; the app asks before replacing it. */
 private var canOverrideList=false
 private fun upload(overwrite:Boolean=false,notify:Boolean=true,previewOnly:Boolean=false) {
  retryStep={upload(overwrite,notify,previewOnly)};repairKey=null;clearRecoveryActions()
  step(7,"Raport wojenny")
  if(!WarRosterProof.complete(ours.values,enemies.values,ourExpected,enemyExpected,ownClan,enemyClan)) {
   if(fullMode) {
    fullSide=ours.resolvedCount==ourExpected&&ourExpected>0
    saveFull("repair-roster",true)
    retryStep={resumeFullWork()}
    fail("Listy wymagają uzupełnienia. Nasi ${ours.resolvedCount}/$ourExpected, rywale ${enemies.resolvedCount}/$enemyExpected. Kontynuuj, aby wrócić do brakującej listy.")
    // Unreadable roster cards: ask the player right away instead of only offering "Kontynuuj".
    val unresolvedSide=if(ours.unresolvedIndices.isNotEmpty())false else if(enemies.unresolvedIndices.isNotEmpty())true else null
    if(unresolvedSide!=null) {
     val count=(if(unresolvedSide)enemies else ours).unresolvedIndices.size
     recovery?.addView(ScannerAppearance.overlayAction(context,"Uzupełnij ręcznie (${count})") {reviewMissingRoster(unresolvedSide)})
     reviewMissingRoster(unresolvedSide)
    }
   } else {
    retryStep={clanRoster(true)}
    fail("Brakuje dwóch kompletnych, różnych list klanów. Raport nie został wysłany. Kontynuuj, aby otworzyć klan rywala.")
   }
   return
  }
  val asPreview=testMode||previewOnly
  val day=if(asPreview)"TEST" else WarPreparationModel.day()?:run {fail("Okno wojenne zakończone.");return}
  if(fullMode&&fullResult==null){computeFullWar();return}
  val body=JSONObject().put("test_mode",asPreview).put("real_tech",previewOnly).put("request_id",uploadRequestId?:UUID.randomUUID().toString().also {uploadRequestId=it}).put("own_clan",ownClan).put("enemy_clan",enemyClan).put("observed_at",Instant.now().toString()).put("war_day",day).put("overwrite",overwrite).put("notify",notify)
  fun list(values:Collection<WarMember>)=JSONArray().apply {values.forEach {put(JSONObject().put("name",it.name).put("power",it.power))}}
  body.put("ours",list(ours.values)).put("enemies",list(enemies.values)).put("our_count",ours.size).put("enemy_count",enemies.size)
   .put("own_bonus",JSONObject().put("damage",ownDamage).put("health",ownHealth)).put("enemy_bonus",JSONObject().put("damage",enemyDamage).put("health",enemyHealth))
  if(fullMode) {
   body.put("simulation",fullResult)
   fun profiles(enemy:Boolean,clan:String,values:List<WarMember>)=JSONArray().apply {values.forEach {put(fullStore.entry(enemy,clan,it)?.optJSONObject("profile")?:JSONObject.NULL)}}
   body.put("profiles",JSONObject().put("ours",profiles(false,ownClan!!,ours.values)).put("enemies",profiles(true,enemyClan!!,enemies.values)))
  }
  logRosterState("upload");say("Wysyłam: $ownClan ${ours.resolvedCount}/$ourExpected → $enemyClan ${enemies.resolvedCount}/$enemyExpected. Nie zamykaj aplikacji.")
  val g=generation
  app.mobile.sendWar(body) {result ->
   if(fullMode)fullStore.log("upload",JSONObject().put("kind",if(asPreview)"war-preview" else "war").put("ok",result.isSuccess).put("error",result.exceptionOrNull()?.message?:JSONObject.NULL))
   if(active && g==generation)result.fold({if(!asPreview)data.edit().putString("uploadedDay",day).apply();status?.text=if(previewOnly)"Podgląd zaktualizowany (z techem rywala). Oficjalna lista bez zmian. Na Discordzie: /report war-list-preview — tam zarząd może ustawić go jako oficjalny." else if(testMode)"Lista poglądowa zapisana (równe bonusy, poza niedzielą). Na Discordzie: /report war-list-preview. Odczyt można powtórzyć." else "Lista zapisana na Discordzie. Zablokowano ponowny zapis w tej wojnie.";warning?.visibility=View.GONE;active=false;if(fullMode&&asPreview)addFullRecovery();addReturnToApp()},{error->
     if(!testMode&&!previewOnly&&!overwrite&&canOverrideList&&error.message.orEmpty().let {it.contains("Potwierdź aktualizację")||it.contains("już istnieje")}){confirmListUpdate();return@fold}
     fail(error.message?:"Nie udało się wysłać listy.");if(fullMode)addFullRecovery()})}
 }
}
