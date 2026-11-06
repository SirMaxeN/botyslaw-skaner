package eu.komars.forgescanner

import android.app.Activity
import android.app.Instrumentation
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.EditText
import eu.komars.forgescanner.model.InventoryTracker
import eu.komars.forgescanner.model.ResourceField
import eu.komars.forgescanner.model.ScanStep
import eu.komars.forgescanner.vision.GridCalibration
import eu.komars.forgescanner.vision.InventoryScanner
import eu.komars.forgescanner.vision.OcrAnalysis
import eu.komars.forgescanner.vision.OcrAnalyzer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.io.File
import eu.komars.forgescanner.sync.MobileSyncClient
import org.json.JSONObject
import eu.komars.forgescanner.vision.InventoryRecording
import eu.komars.forgescanner.model.SavedCalibration
import eu.komars.forgescanner.model.ScrollAnalysis

/** Device smoke test using generated pixels only. It never opens or reads another application. */
class ScannerSmokeInstrumentation : Instrumentation() {
    private var fairyGlyphFixture = false
    private var memberCloseFixture = false
    private var gestureStrokeOnly = false
    private var resumePhase:String?=null
    private var warResumeOnly = false
    private var fullWarOnly = false
    private var warChromeOnly = false
    private var rosterFixture = false
    private var demoOnly = false
    private var uiOnly = false
    private var hammerExpected: Long? = null
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        fairyGlyphFixture = arguments?.getString("fairyGlyphFixture") == "true"
        memberCloseFixture = arguments?.getString("memberCloseFixture") == "true"
        gestureStrokeOnly = arguments?.getString("gestureStrokeOnly") == "true"
        resumePhase=arguments?.getString("resumePhase")
        warResumeOnly = arguments?.getString("warResumeOnly") == "true"
        fullWarOnly = arguments?.getString("fullWarOnly") == "true"
        warChromeOnly = arguments?.getString("warChromeOnly") == "true"
        rosterFixture = arguments?.getString("rosterFixture") == "true"
        demoOnly = arguments?.getString("demoOnly") == "true"
        uiOnly = arguments?.getString("uiOnly") == "true"
        hammerExpected = arguments?.getString("hammerExpected")?.toLongOrNull()
        start()
    }

    override fun onStart() {
        val results = Bundle()
        try {
            if(fairyGlyphFixture){results.putString("summary",verifyFairyGlyph());results.putString("status","passed");finish(Activity.RESULT_OK,results);return}
            if(memberCloseFixture){results.putString("summary",verifyMemberClose());results.putString("status","passed");finish(Activity.RESULT_OK,results);return}
            if(warResumeOnly){results.putString("summary",verifyWarResume());results.putString("status","passed");finish(Activity.RESULT_OK,results);return}
            if(fullWarOnly) {
                results.putString("summary",verifyFullWar());results.putString("status","passed")
                finish(Activity.RESULT_OK,results);return
            }
            if(warChromeOnly) {
                results.putString("summary",verifyWarChrome());results.putString("status","passed")
                finish(Activity.RESULT_OK,results);return
            }
            if(gestureStrokeOnly) {
                results.putString("summary",verifyGestureEndings());results.putString("status","passed")
                finish(Activity.RESULT_OK,results);return
            }
            if(rosterFixture) {
                results.putString("summary",verifyPrivateRoster());results.putString("status","passed")
                finish(Activity.RESULT_OK,results);return
            }
            hammerExpected?.let { expected ->
                results.putString("summary", verifyPrivateHammerFrames(expected))
                results.putString("status", "passed"); results.putInt("checks", 3)
                finish(Activity.RESULT_OK, results); return
            }
            val demoSummary = verifyDemoCannotSend()
            if (demoOnly) { results.putString("status","passed");results.putString("summary",demoSummary);finish(Activity.RESULT_OK,results);return }
            if (uiOnly) {
                val navigation = verifyPermissionFirstNavigation()
                val hammer = verifyHammerBurst()
                results.putString("summary", "$demoSummary; $navigation; $hammer")
                results.putInt("checks", 3)
                results.putString("status", "passed")
                finish(Activity.RESULT_OK, results); return
            }
            val ocrSummary = verifyBundledOcr()
            val gridSummary = verifyGridPixels()
            val navigationSummary = verifyPermissionFirstNavigation()
            val clipboardSummary = verifyReviewedClipboardExport()
            val syncSummary = verifyEncryptedSyncCredential()
            val recordingSummary = verifyRecordingPipeline()
            val shortListSummary = verifyShortListAutoStop()
            val pairedCounterSummary = verifyPairedCounters()
            val fittedGridSummary = verifyAutomaticGrid()
            val dragSummary = verifyPanelDrag()
            val chromeSummary = verifyOverlayChrome()
            val restartSummary = verifyClosedSessionRestarts()
            val firstPassSummary = verifyFirstMountPass()
            val disconnectSummary = verifyImmediateDisconnect()
            val hammerSummary = verifyWideHammerScreenshot()
            results.putString("summary", "$demoSummary; $ocrSummary; $gridSummary; $navigationSummary; $clipboardSummary; $syncSummary; $recordingSummary; $shortListSummary; $pairedCounterSummary; $fittedGridSummary; $dragSummary; $chromeSummary; $restartSummary; $firstPassSummary; $disconnectSummary; $hammerSummary")
            results.putInt("checks", 16)
            results.putString("status", "passed")
            finish(Activity.RESULT_OK, results)
        } catch (failure: Throwable) {
            results.putString("status", "failed")
            results.putString("stack", failure.stackTraceToString())
            results.putString("summary", failure.javaClass.simpleName + ": " + (failure.message ?: "Unknown failure"))
            finish(Activity.RESULT_CANCELED, results)
        }
    }

    private fun verifyFairyGlyph():String {
        val bytes=ParcelFileDescriptor.AutoCloseInputStream(uiAutomation.executeShellCommand("cat /data/local/tmp/botyslaw-fairy-three.jpg")).use {it.readBytes()}
        val image=android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size)?:error("Missing fairy fixture")
        val ocr=com.google.mlkit.vision.text.TextRecognition.getClient(com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS)
        val memory=eu.komars.forgescanner.battle.FairyLevelMemory(targetContext,"instrumentation_fairy_glyphs")
        targetContext.getSharedPreferences("instrumentation_fairy_glyphs",0).edit().clear().commit()
        try {
            val layout=eu.komars.forgescanner.battle.BattleFieldDetector(ocr).observe(image)
            val q=layout.regions.getValue("fairy")
            val three=memory.capture(image,Rect(q.left,q.top,q.right,q.bottom))?:error("No numeric fairy glyph: $q")
            check(three.digits==1)
            val first=layout.regions.getValue("weapon");val pitch=(first.width/.84).toInt()
            val templates=org.json.JSONArray();val samples=mutableListOf<Pair<Int,eu.komars.forgescanner.battle.FairyLevelGlyph.Signature>>()
            for((level,signature) in listOf(3 to three,5 to memory.capture(image,Rect(first.left+pitch,first.top,first.right+pitch,first.bottom)),8 to memory.capture(image,Rect(first.left+pitch*2,first.top,first.right+pitch*2,first.bottom)))) {
                val glyph=signature?:error("No reference glyph for $level")
                check(glyph.digits==1){"Reference $level has ${glyph.digits} digits"}
                samples+=level to glyph;templates.put(JSONObject().put("level",level).put("digits",glyph.digits).put("aspect",glyph.aspect).put("bits",glyph.bits))
            }
            samples.forEach {(level,glyph)->check(eu.komars.forgescanner.battle.FairyLevelGlyph.match(glyph,samples)==level){"Reference confusion $level"};memory.remember(level,glyph)}
            check(memory.recognize(three)==3)
            val fresh=eu.komars.forgescanner.battle.FairyLevelMemory(targetContext,"instrumentation_fairy_glyphs")
            check(fresh.recognize(three)==3)
            val region=Rect(q.left,q.top,q.right,q.bottom);val crop=Bitmap.createBitmap(image,region.left,region.top,region.width(),region.height())
            val doubled=Bitmap.createScaledBitmap(crop,crop.width*2,crop.height*2,false)
            val scaled=memory.capture(doubled,Rect(0,0,doubled.width,doubled.height));check(memory.recognize(scaled)==3){"Changed screen scale lost confirmed 3"}
            crop.recycle();doubled.recycle()
            File(targetContext.cacheDir,"instrumentation-fairy-glyphs.json").writeText(templates.toString())
            return "Real fairy level 3 and game-font 5/8 separated by numeric pixels; learned state survives recreation and 2x scaling; isolated memory; no game input"
        }finally {image.recycle();ocr.close();targetContext.getSharedPreferences("instrumentation_fairy_glyphs",0).edit().clear().commit()}
    }

    private fun verifyMemberClose():String {
        fun bytes(path:String)=ParcelFileDescriptor.AutoCloseInputStream(uiAutomation.executeShellCommand("cat $path")).use {it.readBytes()}
        val imageBytes=bytes("/data/local/tmp/botyslaw-member-clean.png")
        val clean=android.graphics.BitmapFactory.decodeByteArray(imageBytes,0,imageBytes.size)?:error("Missing clean member fixture")
        val failureBytes=bytes("/data/local/tmp/botyslaw-member-failure.jpg")
        val failure=android.graphics.BitmapFactory.decodeByteArray(failureBytes,0,failureBytes.size)?:error("Missing failure fixture")
        val ocr=com.google.mlkit.vision.text.TextRecognition.getClient(com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val detector=eu.komars.forgescanner.battle.BattleFieldDetector(ocr)
            check(detector.observe(clean).profile){"Clean fixture must be a real member profile"}
            val close=eu.komars.forgescanner.vision.CloseButtonDetector().detect(clean)?:error("No clean profile X")
            check(eu.komars.forgescanner.vision.CloseButtonDetector().detect(failure)==null){"Actual failure must reproduce the hidden X"}
            val anchor=eu.komars.forgescanner.battle.MemberProfileAnchor("Fixture",clean.width,clean.height,eu.komars.forgescanner.model.PixelRegion(close.left,close.top,close.right,close.bottom))
            val covered=clean.copy(Bitmap.Config.ARGB_8888,true)
            val canvas=Canvas(covered);val paint=Paint().apply {color=Color.rgb(16,33,43)}
            canvas.drawRect(0f,(close.top-20).coerceAtLeast(0).toFloat(),covered.width.toFloat(),covered.height.toFloat(),paint)
            check(eu.komars.forgescanner.vision.CloseButtonDetector().detect(covered)==null){"Overlay fixture must cover X"}
            check(anchor.closeFor("Fixture",clean.width,clean.height,true)!=null){"A completed scan can use its own observed X"}
            check(anchor.closeFor("Other",clean.width,clean.height,true)==null&&anchor.closeFor("Fixture",clean.width,clean.height,false)==null)
            covered.recycle()
            return "Actual failure reproduces overlay-hidden X; clean profile supplied observed anchor; occlusion does not invalidate that completed member's return; other member/unfinished details rejected; no game input"
        }finally {clean.recycle();failure.recycle();ocr.close()}
    }

    /** Native Android construction only: no touch is injected and no Activity/game is opened. */
    private fun verifyGestureEndings():String {
        var checks=0
        for(duration in listOf(180L,360L))for(end in listOf(1041.3f,1695.5f)) {
            val x=kotlin.math.round(540.5f);val y=kotlin.math.round(end)
            val path=android.graphics.Path().apply {moveTo(x,1900f);lineTo(x,y)}
            val moving=android.accessibilityservice.GestureDescription.StrokeDescription(path,0,350,true)
            val finish=eu.komars.forgescanner.automation.HeldStrokeRelease.create(moving,x,y,duration)
            check(!finish.willContinue()&&finish.duration==duration){"The final stroke must release the pointer"}
            val measure=android.graphics.PathMeasure(moving.path,false);val endPoint=FloatArray(2);check(measure.getPosTan(measure.length,endPoint,null))
            val hold=finish.path;check(android.graphics.PathMeasure(hold,false).length==0f)
            hold.lineTo(-1f,-1f);val start=FloatArray(2);check(android.graphics.PathMeasure(hold,false).getPosTan(0f,start,null))
            check(kotlin.math.round(endPoint[0])==kotlin.math.round(start[0])&&kotlin.math.round(endPoint[1])==kotlin.math.round(start[1])){"Continuation endpoints differ"}
            val built=android.accessibilityservice.GestureDescription.Builder().addStroke(finish).build()
            check(built.strokeCount==1){"Release must not add an intermediate held gesture"};checks++
        }
        return "$checks native Android release constructions passed (180/360 ms, same endpoints); no gestures dispatched"
    }

    private fun verifyWarResume():String {
        val context=targetContext
        val store=eu.komars.forgescanner.battle.FullWarStore(context,"instrumentation_resume")
        if(resumePhase!="read")store.clear()
        try {
            if(resumePhase=="read") {
                check(store.state()?.getString("target")=="SirMaxeN"&&store.state()?.getInt("own_expected")==50)
                check(store.state()?.getString("phase")=="fairy"&&store.roster(false).single().reading.name=="SirMaxeN")
                return "Actual process restart restored member SirMaxeN, count 50, fairy phase and roster identity from isolated durable storage"
            }
            val random=kotlin.random.Random(73)
            val visual=eu.komars.forgescanner.battle.RosterVisual(IntArray(1024){random.nextInt()},IntArray(4096){random.nextInt(256)},130,2.0)
            val registry=eu.komars.forgescanner.battle.RosterRegistry()
            registry.addUnorderedView(listOf(eu.komars.forgescanner.battle.RosterObservation(visual,listOf(eu.komars.forgescanner.battle.RosterReading("SirMaxeN",791e6),eu.komars.forgescanner.battle.RosterReading("SirMaxeN",791e6)))),50).getOrThrow()
            val state=JSONObject().put("target","SirMaxeN").put("own_expected",50).put("phase","profile")
            store.checkpoint(state,registry.savedRows(),emptyList())
            val fresh=eu.komars.forgescanner.battle.FullWarStore(context,"instrumentation_resume")
            check(fresh.state()?.getInt("own_expected")==50&&fresh.state()?.getString("target")=="SirMaxeN")
            val restored=eu.komars.forgescanner.battle.RosterRegistry();restored.restore(fresh.roster(false))
            check(restored.values.single().name=="SirMaxeN"&&restored.values.single().power==791e6)
            check(restored.savedRows().single().visual.avatar.contentEquals(visual.avatar))
            store.checkpoint(JSONObject(state.toString()).put("phase","fairy"),emptyList(),emptyList(),false)
            check(fresh.roster(false).size==1&&fresh.state()?.getString("phase")=="fairy")
            val previousDate=java.time.LocalDate.now(java.time.ZoneId.of("Europe/Warsaw")).minusDays(1).toString()
            val acrossMidnight=JSONObject(fresh.state().toString()).put("date",previousDate)
            val db=android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath("instrumentation_resume_pairs_v1.db").path,null,android.database.sqlite.SQLiteDatabase.OPEN_READWRITE)
            try { db.execSQL("UPDATE run_state SET payload=? WHERE id=1",arrayOf(acrossMidnight.toString())) }finally {db.close()}
            store.checkpoint(JSONObject().put("target","SirMaxeN").put("own_expected",50).put("phase","fairy"),emptyList(),emptyList(),false)
            check(fresh.state()?.getString("date")==previousDate)
            store.log("resume-test",JSONObject().put("target","SirMaxeN"))
            check(java.io.File(context.cacheDir,"instrumentation_resume-events.jsonl").readText().contains("resume-test"))
            return "Native restart restored target SirMaxeN, counter 50, roster identity/power and field phase; stage-only checkpoint kept roster; persistent log passed; isolated test namespace"
        }finally {if(resumePhase!="write")store.clear()}
    }

    private fun verifyFullWar():String {
        val store=eu.komars.forgescanner.battle.FullWarStore(targetContext,"instrumentation_full_war")
        store.clear()
        val profilePrefs=targetContext.getSharedPreferences("battle_profiles_v1",0)
        val selfBefore=profilePrefs.getString("self",null)
        fun draft(name:String)=JSONObject().put("name",name).put("damage","1m").put("health","10m").put("weapon","1m Obrażenia (dystans)")
            .put("pets",3).put("mounted",true).put("bonuses",JSONObject()).put("raw","").put("fairy",JSONObject().put("present",false))
            .put("skillScans",org.json.JSONArray().put(JSONObject().put("empty",true)).put(JSONObject().put("empty",true)).put(JSONObject().put("empty",true))).put("scannedAt",System.currentTimeMillis())
        try {
            val a=eu.komars.forgescanner.battle.WarMember("FixtureA",100.0);val b=eu.komars.forgescanner.battle.WarMember("FixtureB",90.0)
            val first=draft(a.name);val accepted=eu.komars.forgescanner.battle.FullProfileCodec.accept(a.name,first)
            check(runCatching {eu.komars.forgescanner.battle.FullProfileCodec.accept("Wrong person",first)}.isFailure)
            val missing=JSONObject(first.toString());missing.remove("pets")
            check(runCatching {eu.komars.forgescanner.battle.FullProfileCodec.accept(a.name,missing)}.isFailure)
            store.save(false,"Fixture clan A",a,accepted,first,null)
            val resumed=eu.komars.forgescanner.battle.FullWarStore(targetContext,"instrumentation_full_war")
            check(resumed.entry(false,"Fixture clan A",a)?.optBoolean("complete")==true)
            check(resumed.entry(true,"Fixture clan A",a)==null)
            store.pair("native-sentinel",JSONObject().put("trials",128).put("savedAt",System.currentTimeMillis()))
            check(resumed.pair("native-sentinel")?.getInt("trials")==128)
            check(resumed.entry(false,"Fixture clan A",a.copy(power=101.0))==null)
            val opponent=eu.komars.forgescanner.battle.FullProfileCodec.accept(b.name,draft(b.name))
            resumed.save(true,"Fixture clan B",b,opponent,draft(b.name),null)
            val engine=eu.komars.forgescanner.battle.FullWarComputation(resumed)
            val start=SystemClock.elapsedRealtime()
            fun run()=engine.run(listOf(a),listOf(b),{enemy,member->resumed.entry(enemy,if(enemy)"Fixture clan B"else"Fixture clan A",member)?.getJSONObject("profile")},eu.komars.forgescanner.battle.WarBonus(),eu.komars.forgescanner.battle.WarBonus(),{false},{_,_->})
            val outcome=run();val sample=outcome.getJSONArray("matrix").getJSONObject(0)
            check(sample.getInt("trials")==1024&&sample.getInt("wins")+sample.getInt("losses")+sample.getInt("draws")==1024)
            val second=run().getJSONArray("matrix").getJSONObject(0)
            check(second.getInt("wins")==sample.getInt("wins")&&second.getInt("trials")==sample.getInt("trials"))
            check(profilePrefs.getString("self",null)==selfBefore)
            return "Native full-war storage resume, identity/missing-data rejection, 1x1 matrix and cached recompute passed in ${SystemClock.elapsedRealtime()-start} ms; accepted self profile unchanged, no game input"
        } finally {store.clear()}
    }

    /** Real Android layout measurements, detached from WindowManager. No activity or game input. */
    private fun verifyWarChrome():String {
        onMain {
            var stopped=false;var boundary=false
            val chrome=eu.komars.forgescanner.ui.WarOverlayChrome(targetContext,{stopped=true},{boundary=true},{})
            val status=android.widget.TextView(targetContext).apply {text=(1..80).joinToString("\n") {"Wiersz $it"}}
            chrome.content.addView(status);chrome.content.addView(chrome.recovery)
            fun button(label:String)=descendants(chrome.root).single {it.contentDescription==label}
            val density=targetContext.resources.displayMetrics.density
            fun layout(width:Int) {chrome.root.measure(View.MeasureSpec.makeMeasureSpec((width*density).toInt(),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));chrome.root.layout(0,0,chrome.root.measuredWidth,chrome.root.measuredHeight)}
            layout(250)
            val scroll=descendants(chrome.root).filterIsInstance<android.widget.ScrollView>().single()
            check(scroll.height<=360*density+1&&scroll.height<status.height){"Long recovery content must scroll inside a bounded panel"}
            button("Zwiń do ikon").performClick();check(chrome.compact)
            chrome.listMode(true,false);layout(180)
            val icons=descendants(chrome.root).filterIsInstance<Button>().filter {it.visibility==View.VISIBLE&&it.parent is android.widget.LinearLayout&&it.contentDescription in listOf("Rozwiń nakładkę","Zatrzymaj listę","Zakończ odczyt ekranu")}.toList()
            check(icons.size==3&&icons.all {it.width==it.height&&it.width>0}){"Compact controls must be square"}
            check(icons.last().right<=(icons.last().parent as View).width){"Compact stop is clipped"}
            button("Zatrzymaj listę").performClick();check(boundary)
            button("Rozwiń nakładkę").performClick();check(!chrome.compact)
            button("Zakończ odczyt ekranu").performClick();check(stopped)
        }
        return "Detached native war panel: bounded scrolling, compact square icons, expand, boundary and STOP callbacks passed; no windows or gestures opened"
    }

    /** Reads only the explicitly supplied cached fixture. Never opens or navigates the game. */
    private fun verifyPrivateRoster():String {
        fun bytes(path:String):ByteArray = ParcelFileDescriptor.AutoCloseInputStream(uiAutomation.executeShellCommand("cat $path")).use {it.readBytes()}
        val expected=JSONObject(String(bytes("/data/local/tmp/botyslaw-roster-fixture.json"),Charsets.UTF_8))
        val input=bytes("/data/local/tmp/botyslaw-roster.png")
        val image=android.graphics.BitmapFactory.decodeByteArray(input,0,input.size) ?: error("Missing roster fixture")
        val recognizer=com.google.mlkit.vision.text.TextRecognition.getClient(com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS)
        val latch=CountDownLatch(1);val answer=AtomicReference<Result<List<eu.komars.forgescanner.battle.RosterObservation>>>()
        try {
            if(expected.optBoolean("home_proof")) {
                check(eu.komars.forgescanner.battle.BattleHomeProof.isHome(image)){eu.komars.forgescanner.battle.BattleHomeProof.diagnostic(image)}
                return "Actual supplied HOME screenshot confirmed without OCR or close calibration"
            }
            if(expected.has("header_expected")) {
                val w=360;val h=image.height*w/image.width;val small=Bitmap.createScaledBitmap(image,w,h,true)
                val pixels=IntArray(w*h);small.getPixels(pixels,0,w,0,0,w,h);small.recycle()
                val list=eu.komars.forgescanner.model.WarScreenGeometry.observe(w,h,pixels).roster?:error("No clan viewport")
                val header=eu.komars.forgescanner.battle.RosterHeaderRegion.above(w,h,list)?:error("No bounded header")
                val configured=expected.optJSONArray("header_region")
                val bounds=if(configured!=null)Rect(configured.getInt(0),configured.getInt(1),configured.getInt(2),configured.getInt(3))else Rect(header.left*image.width/w,header.top*image.height/h,header.right*image.width/w,header.bottom*image.height/h)
                val crop=Bitmap.createBitmap(image,bounds.left,bounds.top,bounds.width(),bounds.height())
                val readDone=CountDownLatch(1);val text=AtomicReference<String>();var attempts=0
                onMain {eu.komars.forgescanner.battle.RosterHeaderReader(recognizer).read(crop,{true},firstPass=expected.optInt("header_pass",0)) {reading->text.set(reading.text);attempts=reading.attempts;readDone.countDown()}}
                check(readDone.await(20,TimeUnit.SECONDS)){"Header OCR timeout"}
                val count=eu.komars.forgescanner.battle.WarPreparationModel.memberCount(text.get().orEmpty())
                val expectedCount=expected.getInt("header_expected").takeIf {it>0}
                check(count==expectedCount){"Header count $count: ${text.get()}"}
                if(expected.has("attempts"))check(attempts==expected.getInt("attempts")){"Unexpected attempts: $attempts"}
                if(expected.has("clan_expected")) {
                    val clan=eu.komars.forgescanner.battle.WarRosterProof.clan(text.get().orEmpty())
                    check(clan==expected.getString("clan_expected")){"Wrong clan header $clan: ${text.get()}"}
                }
                return "Clan header only: $count members; bounds=$bounds; attempts=$attempts; text=${text.get()}"
            }
            onMain {eu.komars.forgescanner.battle.RosterFrameReader(recognizer,targetContext.cacheDir,expected.optInt("scale",3),expected.optInt("name_threshold",244),expected.optInt("dilation",1)).read(image,Rect(0,0,image.width,image.height),{true}) {answer.set(it);latch.countDown()}}
            check(latch.await(35,TimeUnit.SECONDS)){"Roster OCR timeout"}
            val rows=answer.get().getOrThrow();val registry=eu.komars.forgescanner.battle.RosterRegistry()
            val repairIndex=expected.optInt("repair_index",-1)
            val inputRows=if(repairIndex in rows.indices)rows.mapIndexed {i,row ->if(i==repairIndex)row.copy(readings=listOf(eu.komars.forgescanner.battle.RosterReading(null,null)))else row}else rows
            registry.addView(inputRows,expected.getJSONArray("powers").length()).getOrThrow()
            if(repairIndex in rows.indices) {
                check(registry.size==rows.size&&registry.resolvedCount==rows.size-1){"Wrong pending count before cached repair"}
                val targets=registry.repairTargets;check(targets.size==1&&targets.single().index==repairIndex){"Repair must read exactly the missing person"}
                val repaired=AtomicReference<Result<List<List<eu.komars.forgescanner.battle.RosterReading>>>>();val repairDone=CountDownLatch(1)
                onMain {eu.komars.forgescanner.battle.RosterFrameReader(recognizer).retry(targets.map {it.snapshot},0,{true}) {repaired.set(it);repairDone.countDown()}}
                check(repairDone.await(35,TimeUnit.SECONDS)){"Cached repair timeout"}
                registry.repair(repairIndex,repaired.get().getOrThrow().single())
                check(registry.size==rows.size){"Cached repair appended a person"}
            }
            if(expected.optBoolean("repair_missing")) {
                for(attempt in 0..1) {
                    val targets=registry.repairTargets;if(targets.isEmpty())break
                    val repaired=AtomicReference<Result<List<List<eu.komars.forgescanner.battle.RosterReading>>>>();val done=CountDownLatch(1)
                    onMain {eu.komars.forgescanner.battle.RosterFrameReader(recognizer).retry(targets.map {it.snapshot},attempt,{true}) {repaired.set(it);done.countDown()}}
                    check(done.await(35,TimeUnit.SECONDS)){"Pending-only repair timeout"}
                    targets.forEachIndexed {i,target ->registry.repair(target.index,repaired.get().getOrThrow()[i])}
                }
            }
            val members=registry.values
            val diagnostic=rows.mapIndexed {i,row ->"$i: ${row.readings}"}.joinToString("; ")
            check(rows.size==expected.getJSONArray("powers").length()){"Wrong card count: ${rows.size}; $diagnostic"}
            check(members.size==rows.size){"Unresolved cards; $diagnostic"}
            members.forEachIndexed {i,m ->check(kotlin.math.abs(m.power-expected.getJSONArray("powers").getDouble(i))<1){"Wrong power row $i: ${m.power}; $diagnostic"}}
            if(expected.has("names"))members.forEachIndexed {i,m ->check(m.name.lowercase()==expected.getJSONArray("names").getString(i).lowercase()){"Wrong name row $i: ${m.name}"}}
            return "Static roster fixture: ${members.size} cards and exact powers; cachedRepair=${repairIndex>=0}; " + members.joinToString {"${it.name}: ${it.power}"}
        } finally {onMain {recognizer.close();if(!image.isRecycled)image.recycle()}}
    }

    private fun verifyPrivateHammerFrames(expected: Long): String {
        val cases = listOf(Rect(510, 1915, 635, 1995), Rect(445, 1895, 655, 2000), Rect(300, 1770, 1000, 2025))
        val summaries = mutableListOf<String>()
        for ((caseIndex, region) in cases.withIndex()) {
            val ocr = OcrAnalyzer()
            val frames = (1..3).map { index ->
                val descriptor = uiAutomation.executeShellCommand("cat /data/local/tmp/botyslaw-hammer-$index.png")
                ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { android.graphics.BitmapFactory.decodeStream(it) ?: error("Missing private hammer frame") }
            }
            val done = CountDownLatch(1)
            val result = AtomicReference<OcrAnalysis>()
            val remaining = java.util.ArrayDeque(frames.drop(1))
            try {
                onMain {
                    eu.komars.forgescanner.vision.HammerCounterReader(ocr, freshFrame = { callback -> callback(remaining.removeFirst(), null) })
                        .read(frames.first(), region, { true }) { result.set(it); done.countDown() }
                }
                check(done.await(25, TimeUnit.SECONDS)) { "Private hammer OCR timed out" }
                val answer = result.get()
                check(answer.candidates.any { it.value == expected }) { "Case $caseIndex lost the expected value; candidates=" + answer.candidates.map { it.value } }
                check(answer.requiresConfirmation || answer.candidates.single().value == expected) { "Case $caseIndex auto-accepted a wrong value" }
                summaries += "case $caseIndex: ${answer.candidates.map { it.value }}, confirm=${answer.requiresConfirmation}"
            } finally { onMain { ocr.close(); frames.filterNot { it.isRecycled }.forEach { it.recycle() } } }
        }
        return "Real source frames, numeric-only / icon included / broad crop: " + summaries.joinToString("; ")
    }
    private fun verifyHammerBurst(): String {
        fun image(): Bitmap {
            val bitmap = Bitmap.createBitmap(640, 200, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.rgb(155, 155, 155))
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 100f; typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
                color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 8f
            }
            canvas.drawText("3888", 70f, 140f, paint)
            paint.style = Paint.Style.FILL; paint.color = Color.WHITE
            canvas.drawText("3888", 70f, 140f, paint)
            return bitmap
        }
        val ocr = OcrAnalyzer()
        val requests = java.util.concurrent.atomic.AtomicInteger()
        val owned = mutableListOf<Bitmap>()
        val done = CountDownLatch(1)
        val answer = AtomicReference<OcrAnalysis>()
        try {
            onMain {
                val first = image().also(owned::add)
                eu.komars.forgescanner.vision.HammerCounterReader(ocr, freshFrame = { callback ->
                    requests.incrementAndGet()
                    callback(image().also(owned::add), null)
                }).read(first, Rect(0, 0, 640, 200), { true }) { result -> answer.set(result); done.countDown() }
            }
            check(done.await(25, TimeUnit.SECONDS)) { "Hammer burst timed out" }
            check(requests.get() == 2 && owned.all { it.isRecycled }) { "Burst must own/release exactly three source images" }
            val result = answer.get()
            check(result.candidates.any { it.value == 3888L }) { "Outlined hammer fixture lost the correct reading" }
            check(result.requiresConfirmation || result.candidates.single().value == 3888L) { "A conflicting value was auto-accepted" }
            return "Hammer burst used three source frames and contrast variants, retained 3888 and released bitmaps"
        } finally { onMain { ocr.close(); owned.filterNot { it.isRecycled }.forEach { it.recycle() } } }
    }
    private fun verifyDemoCannotSend(): String {
        val calls = java.util.concurrent.atomic.AtomicInteger()
        val client = MobileSyncClient(targetContext, demoMode = { true }, testTransport = { _, _, _ ->
            calls.incrementAndGet(); error("Demo must never reach transport")
        })
        val latch = CountDownLatch(2)
        client.pair("ANYCODE") { check(it.isSuccess && it.getOrThrow().contains("DEMO")); latch.countDown() }
        client.send(ResourceField.entries.associateWith { if (it.isCost) 50L else 100L }) {
            check(it.isSuccess && it.getOrThrow().contains("DEMO")); latch.countDown()
        }
        check(latch.await(5, TimeUnit.SECONDS)); check(calls.get() == 0)
        val activity = launchActivity(review = false)
        val app = activity.application as ScannerApplication
        val previous = onMain { app.session.snapshot() }
        onMain {
            app.setDemo(true)
            check(app.session.snapshot().value(ResourceField.HAMMERS) == 11026L && app.mobile.name!!.contains("DEMO"))
            activity.javaClass.getDeclaredMethod("refreshConnectionScreen").apply { isAccessible=true }.invoke(activity)
        }
        waitForIdleSync(); uiAutomation.waitForIdle(250, 5000)
        onMain { saveViewPreview(activity, "demo-home.png"); activity.javaClass.getDeclaredMethod("showReview").apply { isAccessible=true }.invoke(activity) }
        waitForIdleSync(); uiAutomation.waitForIdle(250, 5000)
        onMain {
            saveViewPreview(activity, "demo-results.png")
            app.setDemo(false)
            check(app.session.snapshot().values == previous.values)
            activity.javaClass.getDeclaredMethod("refreshConnectionScreen").apply { isAccessible=true }.invoke(activity)
        }
        return "demo pair/send never invoke transport; sample session restores real data"
    }

    private fun saveViewPreview(activity: Activity, name: String) {
        val bitmap=uiAutomation.takeScreenshot() ?: error("Screenshot unavailable")
        val file=File(targetContext.filesDir,"verification/$name"); file.parentFile!!.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle()
    }

    private fun verifyImmediateDisconnect(): String {
        val activity=launchActivity(review=false)
        val app=activity.application as ScannerApplication
        val client=MobileSyncClient(targetContext) { _, _, _ -> JSONObject().put("token","a".repeat(43)).put("name","SirMaxeN") }
        val latch=CountDownLatch(1)
        client.pair("ABC234") { check(it.isSuccess);latch.countDown() }
        check(latch.await(10,TimeUnit.SECONDS))
        onMain { activity.javaClass.getDeclaredMethod("refreshConnectionScreen").apply { isAccessible=true }.invoke(activity) }
        check(onMain { descendants(activity.window.decorView).filterIsInstance<android.widget.TextView>().any { it.text.contains("Połączono: SirMaxeN") } })
        onMain {
            app.mobile.disconnect()
            activity.javaClass.getDeclaredMethod("refreshConnectionScreen").apply { isAccessible=true }.invoke(activity)
        }
        check(!app.mobile.paired && app.mobile.name==null)
        check(onMain { descendants(activity.window.decorView).filterIsInstance<android.widget.TextView>().none { it.text.contains("Połączono: SirMaxeN") } })
        val entered=CountDownLatch(1);val release=CountDownLatch(1);val finished=CountDownLatch(1)
        val cancelled=AtomicReference<Boolean>()
        val slow=MobileSyncClient(targetContext) { _, _, _ ->
            entered.countDown();check(release.await(5,TimeUnit.SECONDS))
            JSONObject().put("token","b".repeat(43)).put("name","Old request")
        }
        slow.pair("ABC234") { cancelled.set(it.isFailure);finished.countDown() }
        check(entered.await(5,TimeUnit.SECONDS));slow.disconnect();release.countDown()
        check(finished.await(10,TimeUnit.SECONDS) && cancelled.get()==true && !slow.paired)
        onMain { activity.javaClass.getDeclaredMethod("confirmEraseApplicationData").apply { isAccessible=true }.invoke(activity) }
        val cancel=waitForOwnButton("Anuluj",3000) ?: error("Full erase must offer cancellation")
        cancel.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        return "Disconnect immediately clears the connection card; full erase requires confirmation and can be cancelled"
    }

    private fun verifyWideHammerScreenshot(): String {
        val file=File(targetContext.filesDir,"verification/hammers-wide.png")
        check(file.exists()) { "Private hammer screenshot fixture missing" }
        val bitmap=android.graphics.BitmapFactory.decodeFile(file.path)
        val reader=OcrAnalyzer()
        val latch=CountDownLatch(1)
        val answer=AtomicReference<OcrAnalysis>()
        onMain { reader.analyze(bitmap,ScanStep.HAMMERS,Rect(230,1600,1000,2060)) { answer.set(it);latch.countDown() } }
        try {
            check(latch.await(25,TimeUnit.SECONDS)) { "Wide hammer OCR timed out" }
            check(answer.get().candidates.any { it.value==2997L }) {
                "Expected actual hammer counter2997; got " + answer.get().candidates.map { it.value }
            }
            return "Actual phone screenshot: wide mixed-content hammer crop contains2997"
        } finally { bitmap.recycle();reader.close() }
    }

    private fun verifyFirstMountPass(): String {
        val app = targetContext.applicationContext as ScannerApplication
        val bitmap = Bitmap.createBitmap(1080,2340,Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply { drawColor(Color.WHITE); drawRect(100f,735f,220f,850f,Paint().apply { color=Color.BLACK }) }
        val state = eu.komars.forgescanner.capture.CaptureConnection.javaClass.getDeclaredField("state").apply { isAccessible=true }
        val original = state.get(null)
        var overlay: eu.komars.forgescanner.ui.OverlayController? = null
        try {
            setOverlayPermission(true)
            onMain {
                app.startNewSession();app.flow.start()
                app.flow.acceptNumber(eu.komars.forgescanner.model.ScanTarget.HAMMERS,11026);app.flow.next()
                app.flow.acceptPair(eu.komars.forgescanner.model.ScanTarget.MOUNT_KEYS,mapOf(
                    eu.komars.forgescanner.model.ScanTarget.MOUNT_KEYS to eu.komars.forgescanner.model.GuidedScanFlow.CounterReading(2840),
                    eu.komars.forgescanner.model.ScanTarget.MOUNT_COST to eu.komars.forgescanner.model.GuidedScanFlow.CounterReading(50)))
                app.flow.next()
                state.set(null,eu.komars.forgescanner.capture.CaptureState.READY)
                overlay=eu.komars.forgescanner.ui.OverlayController(targetContext,{app.session},app.flow,app.calibrations,
                    inventoryFrameSource={ callback -> callback(bitmap.copy(Bitmap.Config.ARGB_8888,false),null) })
                val method=overlay!!.javaClass.getDeclaredMethod("beginRecording",Bitmap::class.java,
                    eu.komars.forgescanner.model.ScanTarget::class.java,SavedCalibration::class.java).apply { isAccessible=true }
                method.invoke(overlay,bitmap.copy(Bitmap.Config.ARGB_8888,false),eu.komars.forgescanner.model.ScanTarget.MOUNTS,
                    SavedCalibration(Rect(45,708,1035,1575),5,198))
            }
            val deadline=SystemClock.uptimeMillis()+3000
            var visibleCount=false
            while (SystemClock.uptimeMillis()<deadline && !visibleCount) {
                visibleCount=onMain { overlay!!.javaClass.getDeclaredField("liveItems").apply { isAccessible=true }.getLong(overlay)==1L }
                if (!visibleCount) SystemClock.sleep(50)
            }
            check(visibleCount) { "The one-mount first pass did not update the live count" }
            val confirm=waitForOwnButton("Potwierdź",5000) ?: error("Inventory result confirmation was not shown")
            check(confirm.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            // Accessibility ACTION_CLICK is queued; idle on the instrumentation thread alone
            // does not guarantee that the overlay's click callback has been dispatched.
            val acceptedBy = SystemClock.uptimeMillis() + 2000
            while (SystemClock.uptimeMillis() < acceptedBy && onMain {
                    app.flow.currentTarget != eu.komars.forgescanner.model.ScanTarget.TICKETS
                }) SystemClock.sleep(25)
            check(onMain { app.flow.inventoryRawTotals()[eu.komars.forgescanner.model.ScanTarget.MOUNTS]==1L }) {
                onMain { "Unexpected raw mount result: ${app.flow.inventoryRawTotals()}, target=${app.flow.currentTarget}" }
            }
            check(onMain { app.flow.currentTarget==eu.komars.forgescanner.model.ScanTarget.TICKETS })
            return "First mount pass starts in the real panel, displays one item, confirms the result and advances to tickets"
        } finally {
            onMain { overlay?.hide();state.set(null,original) }
            bitmap.recycle()
        }
    }

    private fun verifyClosedSessionRestarts(): String {
        val app = targetContext.applicationContext as ScannerApplication
        onMain {
            app.startNewSession(); app.flow.start()
            app.flow.acceptNumber(eu.komars.forgescanner.model.ScanTarget.HAMMERS, 11026)
            app.flow.next()
            app.calibrations.save(eu.komars.forgescanner.model.ScanTarget.HAMMERS,
                Rect(10,10,100,100), 200,300,1,90)
            app.closeScanner()
            app.prepareOverlaySession()
            check(app.flow.currentTarget == eu.komars.forgescanner.model.ScanTarget.HAMMERS)
            check(app.session.snapshot().values.isEmpty())
            check(app.calibrations.has(eu.komars.forgescanner.model.ScanTarget.HAMMERS))
            app.flow.acceptNumber(eu.komars.forgescanner.model.ScanTarget.HAMMERS, 123)
            app.flow.next()
            app.prepareOverlaySession()
            check(app.flow.currentTarget == eu.komars.forgescanner.model.ScanTarget.MOUNT_KEYS) {
                "Returning without closing must resume, not reset"
            }
        }
        return "Closing restarts at hammers while retaining calibration; ordinary return resumes"
    }

    private fun verifyOverlayChrome(): String {
        val activity = launchActivity(review = false)
        waitForIdleSync()
        fun screenshot(name: String) {
            val image = uiAutomation.takeScreenshot() ?: error("Screenshot unavailable")
            val file = File(targetContext.filesDir, "verification/$name.png")
            file.parentFile!!.mkdirs()
            file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
            image.recycle()
        }
        screenshot("home10")
        onMain {
            val app = activity.application as ScannerApplication
            app.startNewSession(); app.flow.start()
            val overlay = app.overlay
            overlay.javaClass.getDeclaredField("recordingTarget").apply { isAccessible = true }.set(overlay, eu.komars.forgescanner.model.ScanTarget.PETS)
            overlay.javaClass.getDeclaredField("liveItems").apply { isAccessible = true }.setLong(overlay, 125)
            val panel = overlay.javaClass.getDeclaredMethod("panel").apply { isAccessible = true }.invoke(overlay) as View
            val container = android.widget.FrameLayout(activity).apply {
                setBackgroundColor(Color.rgb(7, 16, 29))
                addView(panel, android.widget.FrameLayout.LayoutParams((264*resources.displayMetrics.density).toInt(), -2).apply {
                    leftMargin=30;topMargin=100
                })
            }
            activity.setContentView(container)
            val views = descendants(panel).toList()
            check(views.any { it.contentDescription == "Menu skanera" })
            check(views.any { it.contentDescription == "Zamknij skaner" })
            check(views.filterIsInstance<android.widget.TextView>().any { it.text.contains("125 pozycji") })
        }
        waitForIdleSync(); SystemClock.sleep(350); screenshot("overlay10")
        onMain { (activity.application as ScannerApplication).startNewSession() }
        return "Live count, close/menu controls and home/overlay visual previews verified"
    }

    private fun verifyPanelDrag(): String {
        val activity = launchActivity(review = false)
        var moves = 0; var clicks = 0
        val panel = onMain {
            eu.komars.forgescanner.ui.DraggableOverlayLayout(activity) { _, _ -> moves++ }.also { panel ->
                panel.addView(Button(activity).apply { text = "Scan"; setOnClickListener { clicks++ } },
                    android.widget.LinearLayout.LayoutParams(400, 120))
                activity.setContentView(panel)
            }
        }
        waitForIdleSync()
        val time = SystemClock.uptimeMillis()
        fun event(action: Int, x: Float, y: Float) = onMain {
            val motion = android.view.MotionEvent.obtain(time, SystemClock.uptimeMillis(), action, x, y, 0)
            panel.dispatchTouchEvent(motion); motion.recycle()
        }
        event(android.view.MotionEvent.ACTION_DOWN, 50f, 50f)
        event(android.view.MotionEvent.ACTION_MOVE, 180f, 50f)
        event(android.view.MotionEvent.ACTION_UP, 180f, 50f)
        waitForIdleSync()
        check(onMain { moves > 0 && clicks == 0 }) { "Dragging a button must move the overlay without clicking" }
        event(android.view.MotionEvent.ACTION_DOWN, 50f, 50f)
        event(android.view.MotionEvent.ACTION_UP, 50f, 50f)
        waitForIdleSync()
        check(onMain { clicks == 1 }) { "A normal button tap must still work" }
        val before = onMain { moves }
        event(android.view.MotionEvent.ACTION_DOWN, 50f, 240f)
        event(android.view.MotionEvent.ACTION_MOVE, 180f, 240f)
        event(android.view.MotionEvent.ACTION_UP, 180f, 240f)
        check(onMain { moves > before }) { "The empty panel area must also drag" }
        return "Dragging buttons/body moves the panel without firing actions; short taps still click"
    }

    private fun verifyAutomaticGrid(): String {
        val bitmap = Bitmap.createBitmap(200, 300, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap); canvas.drawColor(Color.WHITE)
        val paint = Paint().apply { color = Color.BLACK }
        for (row in 0 until 4) for (col in 0 until 5) {
            canvas.drawRect((12 + col * 36).toFloat(), (42 + row * 36).toFloat(),
                (36 + col * 36).toFloat(), (68 + row * 36).toFloat(), paint)
        }
        canvas.drawRect(4f, 198f, 194f, 228f, paint)
        try {
            val fitted = eu.komars.forgescanner.vision.InventoryGridCalibrator().refine(bitmap, Rect(20, 48, 170, 185))
            check(fitted.rowHeightPx == 36 && fitted.columns == 5 && fitted.region.bottom == 196)
            return "Rough viewport automatically fitted five columns and row pitch, excluding the fixed bottom panel"
        } finally { bitmap.recycle() }
    }

    private fun verifyPairedCounters(): String {
        val bitmap = Bitmap.createBitmap(600, 400, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap); canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 64f }
        canvas.drawText("2840", 40f, 100f, paint); canvas.drawText("50", 40f, 280f, paint)
        val latch = CountDownLatch(1)
        val answer = AtomicReference<Result<Map<eu.komars.forgescanner.model.ScanTarget, List<eu.komars.forgescanner.vision.OcrCandidate>>>>()
        val recognizer = OcrAnalyzer()
        runOnMainSync {
            eu.komars.forgescanner.vision.PairedCounterReader(recognizer).read(bitmap,
                eu.komars.forgescanner.model.ScanTarget.MOUNT_KEYS, mapOf(
                    eu.komars.forgescanner.model.ScanTarget.MOUNT_KEYS to Rect(0, 0, 600, 150),
                    eu.komars.forgescanner.model.ScanTarget.MOUNT_COST to Rect(0, 170, 600, 380))) { result ->
                answer.set(result); latch.countDown()
            }
        }
        try {
            check(latch.await(15, TimeUnit.SECONDS)) { "Paired OCR did not finish" }
            val result = answer.get().getOrThrow()
            check(result.getValue(eu.komars.forgescanner.model.ScanTarget.MOUNT_KEYS).single().value == 2840L)
            check(result.getValue(eu.komars.forgescanner.model.ScanTarget.MOUNT_COST).single().value == 50L)
            return "One screenshot read separate resource and cost regions without a second capture"
        } finally { recognizer.close() }
    }

    private fun verifyShortListAutoStop(): String {
        fun frame(): Bitmap {
            val pixels = IntArray(80 * 120) { Color.WHITE }
            for (y in 8 until 30) for (x in 4 until 12) {
                val gray = if ((x + y) % 2 == 0) 30 else 170
                pixels[y * 80 + x] = Color.rgb(gray, gray, gray)
            }
            return Bitmap.createBitmap(pixels, 80, 120, Bitmap.Config.ARGB_8888)
        }
        val latch = CountDownLatch(1)
        val answer = AtomicReference<Result<ScrollAnalysis>>()
        lateinit var recording: InventoryRecording
        runOnMainSync {
            recording = InventoryRecording(SavedCalibration(Rect(0, 0, 80, 120), 5, 40), 80, 120,
                {}, {}, { result -> answer.set(result); latch.countDown() }, { callback -> callback(frame(), null) })
            val first = frame(); recording.start(first); first.recycle()
        }
        try {
            check(latch.await(10, TimeUnit.SECONDS)) { "Single-mount list did not finish automatically" }
            check(answer.get().getOrThrow().count == 1L) { "Single-mount list must count one raw item" }
            return "One-item list stopped automatically after a stable empty footer, without scrolling"
        } finally { runOnMainSync { recording.cancel() } }
    }

    private fun verifyRecordingPipeline(): String {
        val random = java.util.Random(123)
        val pixels = IntArray(80 * 320) { val gray = random.nextInt(256); Color.rgb(gray, gray, gray) }
        fun frame(offset: Int) = Bitmap.createBitmap(pixels, offset * 80, 80, 80, 120, Bitmap.Config.ARGB_8888)
        val latch = CountDownLatch(1)
        val answer = AtomicReference<Result<ScrollAnalysis>>()
        var offset = 20
        lateinit var recording: InventoryRecording
        runOnMainSync {
            recording = InventoryRecording(SavedCalibration(Rect(0, 0, 80, 120), 5, 40), 80, 120,
                { count -> if (count == 11) recording.stop() }, {},
                { result -> answer.set(result); latch.countDown() },
                { callback -> val bitmap = frame(offset); offset += 20; callback(bitmap, null) })
            val first = frame(0)
            recording.start(first); first.recycle()
        }
        try {
            check(latch.await(20, TimeUnit.SECONDS)) { "Recording analysis did not finish" }
            val result = answer.get().getOrThrow()
            check(result.count == 40L && result.uncertainFrames == 0) { "Recording count ${result.count}, uncertain ${result.uncertainFrames}" }
            return "Continuous frame sampling and post-recording analysis counted 40 scrolling slots"
        } finally { runOnMainSync { recording.cancel() } }
    }

    private fun verifyEncryptedSyncCredential(): String {
        val token = "A".repeat(43)
        var sent = false
        val client = MobileSyncClient(targetContext) { route, body, authorization ->
            if (route == "pair") JSONObject().put("token", token).put("name", "Test Player")
            else {
                check(authorization == token)
                check(body.getJSONObject("values").length() == 7)
                check(body.getJSONObject("values").getLong("pets") == 28L)
                sent = true
                JSONObject().put("status", "saved").put("name", "Test Player")
            }
        }
        fun await(operation: ((Result<String>) -> Unit) -> Unit) {
            val latch = CountDownLatch(1)
            val answer = AtomicReference<Result<String>>()
            operation { result -> answer.set(result); latch.countDown() }
            check(latch.await(15, TimeUnit.SECONDS))
            answer.get().getOrThrow()
        }
        try {
            await { callback -> client.pair("B".repeat(32), callback) }
            val stored = targetContext.getSharedPreferences("mobile_sync", 0).getString("credential", "")!!
            check(stored.isNotEmpty() && !stored.contains(token))
            await { callback -> client.send(ResourceField.entries.associateWith { field -> if (field == ResourceField.PETS) 28L else 50L }, callback) }
            check(sent)
            return "AndroidKeyStore encrypted the device credential and sync sent all seven scoped values"
        } finally { client.disconnect() }
    }

    private fun verifyBundledOcr(): String {
        val bitmap = Bitmap.createBitmap(1000, 460, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 80f }
        canvas.drawText("Keys 2840", 50f, 170f, paint)
        canvas.drawText("Summon cost 50", 50f, 350f, paint)
        val latch = CountDownLatch(1)
        val answer = AtomicReference<OcrAnalysis>()
        val error = AtomicReference<Throwable>()
        val analyzer = AtomicReference<OcrAnalyzer>()
        Handler(Looper.getMainLooper()).post {
            try {
                val instance = OcrAnalyzer()
                analyzer.set(instance)
                instance.analyze(bitmap, ScanStep.MOUNT_CURRENCY) {
                    answer.set(it)
                    latch.countDown()
                }
            } catch (failure: Throwable) {
                error.set(failure)
                latch.countDown()
            }
        }
        try {
            check(latch.await(45, TimeUnit.SECONDS)) { "Bundled OCR did not complete within 45 seconds" }
            error.get()?.let { throw it }
            val result = checkNotNull(answer.get()) { "OCR returned no result" }
            check(result.error == null) { result.error ?: "OCR failure" }
            val values = result.candidates.map { it.value }.toSet()
            check(2840L in values && 50L in values) {
                "Expected OCR values 2840 and 50; received $values; lines=" + result.lines.joinToString { it.text }
            }
            return "Bundled OCR recognized keys=2840 and cost=50"
        } finally {
            runOnMainSync { analyzer.get()?.close() }
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    private fun verifyGridPixels(): String {
        val bitmap = Bitmap.createBitmap(500, 200, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            // Seven textured occupied cells, followed by three perfectly blank slots.
            for (index in 0 until 7) {
                val x = (index % 5) * 100f + 50f
                val y = (index / 5) * 100f + 50f
                paint.color = Color.BLACK
                canvas.drawCircle(x, y, 35f, paint)
                paint.color = Color.WHITE
                canvas.drawRect(x - 9f, y - 30f, x + 9f, y + 30f, paint)
            }
            val frame = InventoryScanner().captureFrame(bitmap, GridCalibration(Rect(0, 0, 500, 200), 5, 100))
            val visible = frame.rows.sumOf { it.occupiedCount }
            check(frame.rows.size == 2 && visible == 7) { "Expected 7 occupied slots in 2 rows; received $visible" }
            val tracker = InventoryTracker(5)
            check(tracker.append(frame).totalObservedCount == 7L) { "Tracker initial count differs" }
            check(tracker.append(frame).addedCount == 0) { "Repeated frame was counted twice" }
            return "Grid detected 7 occupied slots and ignored repeated frame"
        } finally {
            bitmap.recycle()
        }
    }

    private fun verifyPermissionFirstNavigation(): String {
        setOverlayPermission(false)
        val permissionActivity = launchActivity(review = true)
        val permissionButtons = onMain {
            descendants(permissionActivity.window.decorView).filterIsInstance<Button>().filter { it.visibility == View.VISIBLE }.toList()
        }
        check(permissionButtons.count { it.id == MainActivity.ID_ALLOW_OVERLAY } == 1 && permissionButtons.any { it.id == 0x00f10011 }) {
            "Fresh launch must show only the overlay permission action; found " + permissionButtons.joinToString { it.text }
        }
        check(onMain { descendants(permissionActivity.window.decorView).none { it is EditText } }) {
            "Permission screen unexpectedly includes resource inputs"
        }
        onMain {
            check(permissionActivity.findViewById<Button>(MainActivity.ID_DEMO) == null) { "Demo must not be on the permission screen" }
            permissionActivity.findViewById<Button>(MainActivity.ID_OPTIONS).performClick()
            checkNotNull(permissionActivity.findViewById<Button>(MainActivity.ID_DEMO)) { "Demo must be available in Options before overlay permission" }
            permissionActivity.findViewById<Button>(MainActivity.ID_HELP).performClick()
            check(descendants(permissionActivity.window.decorView).filterIsInstance<android.widget.TextView>().any { it.text.toString() == "Pomoc" }) { "Help must be a readable page" }
            permissionActivity.findViewById<Button>(MainActivity.ID_BACK).performClick()
            val app = permissionActivity.application as ScannerApplication
            val previous = app.session.snapshot().values
            permissionActivity.findViewById<Button>(MainActivity.ID_DEMO).performClick()
            check(app.demo && permissionActivity.findViewById<EditText>(MainActivity.FIELD_ID_BASE) != null) { "Options must open demo results without overlay consent" }
            permissionActivity.findViewById<Button>(MainActivity.ID_BACK).performClick()
            permissionActivity.findViewById<Button>(MainActivity.ID_OPTIONS).performClick()
            permissionActivity.findViewById<Button>(MainActivity.ID_DEMO).performClick()
            check(!app.demo && app.session.snapshot().values == previous) { "Leaving demo must restore real results" }
        }
        onMain { permissionActivity.finish() }
        waitForIdleSync()

        setOverlayPermission(true)
        val homeActivity = launchActivity(review = false)
        val homeButtons = onMain {
            descendants(homeActivity.window.decorView).filterIsInstance<Button>().filter { it.visibility == View.VISIBLE }.map { it.id }.toSet()
        }
        check(setOf(MainActivity.ID_START_OVERLAY, MainActivity.ID_RESULTS, MainActivity.ID_OPTIONS).all { it in homeButtons }) {
            "Home must provide overlay, results and options actions; found $homeButtons"
        }
        check(onMain { descendants(homeActivity.window.decorView).none { it is EditText } }) {
            "Home must not include the seven-field review form"
        }
        check(onMain { homeActivity.findViewById<Button>(MainActivity.ID_DEMO) == null &&
            homeActivity.findViewById<android.widget.TextView>(MainActivity.ID_CAPTURE_DISCLOSURE)?.text?.contains("Obrazy") == true }) {
            "Home must keep capture disclosure before Start and move demo to Options"
        }
        onMain { homeActivity.findViewById<Button>(MainActivity.ID_RESULTS).performClick() }
        val draftValue = "9,88k"
        onMain {
            val ticketInput = homeActivity.findViewById<EditText>(MainActivity.FIELD_ID_BASE + ResourceField.TICKETS.ordinal)
            checkNotNull(ticketInput) { "Results action did not open the review form" }
            ticketInput.setText(draftValue)
            homeActivity.findViewById<Button>(MainActivity.ID_BACK).performClick()
        }
        check(onMain { descendants(homeActivity.window.decorView).none { it is EditText } }) {
            "Review back action did not restore the simple home screen"
        }
        onMain { homeActivity.findViewById<Button>(MainActivity.ID_RESULTS).performClick() }
        check(onMain {
            homeActivity.findViewById<EditText>(MainActivity.FIELD_ID_BASE + ResourceField.TICKETS.ordinal).text.toString() == draftValue
        }) { "Returning to the review form lost an unfinished value" }
        onMain { homeActivity.finish() }
        waitForIdleSync()
        return "Permission-first home kept review separate and preserved unfinished values when navigating back"
    }

    private fun setOverlayPermission(allowed: Boolean) {
        val mode = if (allowed) "allow" else "deny"
        val descriptor = uiAutomation.executeShellCommand("appops set ${targetContext.packageName} SYSTEM_ALERT_WINDOW $mode")
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { input ->
            val output = input.readText()
            check(!output.contains("Error", ignoreCase = true)) { "Could not set emulator test overlay permission: $output" }
        }
    }

    private fun launchActivity(review: Boolean): MainActivity {
        val intent = Intent(targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra("review", review)
        return (startActivitySync(intent) as MainActivity).also { waitForIdleSync() }
    }

    private fun verifyReviewedClipboardExport(): String {
        val activity = launchActivity(review = true)
        val app = activity.application as ScannerApplication
        val inputs = onMain {
            descendants(activity.window.decorView).filterIsInstance<EditText>().toList()
        }
        check(inputs.size == ResourceField.entries.size) { "Expected seven resource inputs; found ${inputs.size}" }
        val copy = onMain {
            descendants(activity.window.decorView).filterIsInstance<Button>().firstOrNull { it.text.toString() == "Skopiuj komendę" }
        }
        checkNotNull(copy) { "Clipboard review button not found" }

        // Empty values must fail validation rather than silently become zero or produce a report.
        onMain {
            inputs.forEach { it.setText("") }
            copy.performClick()
        }
        val blankSnapshot = onMain { app.session.snapshot() }
        check(!blankSnapshot.readyToExport && blankSnapshot.missingFields.isNotEmpty()) { "Blank inputs were incorrectly accepted" }

        val values = linkedMapOf(
            ResourceField.MOUNT_KEYS to 2840L,
            ResourceField.MOUNT_COST to 50L,
            ResourceField.MOUNTS to 95L,
            ResourceField.TICKETS to 9880L,
            ResourceField.SKILL_COST to 188L,
            ResourceField.PETS to 28L,
            ResourceField.HAMMERS to 11026L
        )
        val serviceInfo = uiAutomation.serviceInfo
        serviceInfo.flags = serviceInfo.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        uiAutomation.serviceInfo = serviceInfo
        try {
            uiAutomation.executeAndWaitForEvent({
                onMain {
                    ResourceField.entries.forEachIndexed { index, field -> inputs[index].setText(values.getValue(field).toString()) }
                    copy.performClick()
                }
            }, { event ->
                event.packageName?.toString() == targetContext.packageName &&
                    (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
            }, 5000)
        } catch (_: java.util.concurrent.TimeoutException) {
            // The confirmation may already be attached before the accessibility window event arrives.
        }
        waitForIdleSync()

        val report = onMain {
            activity.getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.coerceToText(activity)?.toString()
        }
        checkNotNull(report) { "Confirmed report was not placed in clipboard" }
        check(report.startsWith("/import dane:"))
        val imported = JSONObject(report.removePrefix("/import dane:"))
        check(imported.length() == 7 && imported.getLong("hammers") == 11026L && imported.getLong("pets") == 28L && imported.getLong("skill_cost_x5") == 188L)
        check(onMain { app.session.snapshot().readyToExport }) { "Confirmed resource state is not ready for export" }
        return "UI rejected blank fields and copied all seven fields as a Discord import command without a second dialog"
    }

    private fun ownRoots(): List<AccessibilityNodeInfo> {
        val roots = uiAutomation.windows.mapNotNull { it.root }.toMutableList()
        uiAutomation.rootInActiveWindow?.let(roots::add)
        return roots.filter { it.packageName?.toString() == targetContext.packageName }
    }

    private fun waitForOwnButton(text: String, timeoutMillis: Long): AccessibilityNodeInfo? {
        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        do {
            for (root in ownRoots()) {
                val found = root.findAccessibilityNodeInfosByText(text).firstOrNull {
                    it.text?.toString()?.equals(text, ignoreCase = true) == true && it.isClickable && it.isVisibleToUser
                }
                if (found != null) return found
            }
            try { uiAutomation.waitForIdle(100, 400) } catch (_: java.util.concurrent.TimeoutException) { }
        } while (SystemClock.uptimeMillis() < deadline)
        return null
    }

    private fun ownWindowDiagnostics(): String {
        fun describe(root: AccessibilityNodeInfo): List<String> {
            val result = mutableListOf<String>()
            fun visit(node: AccessibilityNodeInfo) {
                if (result.size >= 40) return
                val text = node.text?.toString()
                if (!text.isNullOrBlank()) {
                    val bounds = Rect()
                    node.getBoundsInScreen(bounds)
                    result += "${text.take(150)} [visible=${node.isVisibleToUser}, clickable=${node.isClickable}, bounds=$bounds]"
                }
                for (index in 0 until node.childCount) node.getChild(index)?.let(::visit)
            }
            visit(root)
            return result
        }
        return ownRoots().flatMap(::describe).joinToString(" | ").take(6000)
    }

    private fun saveClipboardFailureScreenshot(): String = try {
        val screenshot = uiAutomation.takeScreenshot()
        if (screenshot == null) "unavailable" else {
            val output = File(targetContext.cacheDir, "test-clipboard-failure.png")
            try { output.outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) } }
            finally { screenshot.recycle() }
            output.absolutePath
        }
    } catch (failure: Exception) { "unavailable:${failure.javaClass.simpleName}" }

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) yieldAll(descendants(view.getChildAt(index)))
    }

    /** Propagate test assertions back to the instrumentation thread instead of crashing Android's main thread. */
    private fun <T> onMain(action: () -> T): T {
        val result = AtomicReference<Result<T>>()
        runOnMainSync { result.set(runCatching(action)) }
        return result.get().getOrThrow()
    }
}
