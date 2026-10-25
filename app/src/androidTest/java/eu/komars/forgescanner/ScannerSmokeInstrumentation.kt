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
    private var demoOnly = false
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        demoOnly = arguments?.getString("demoOnly") == "true"
        start()
    }

    override fun onStart() {
        val results = Bundle()
        try {
            val demoSummary = verifyDemoCannotSend()
            if (demoOnly) { results.putString("status","passed");results.putString("summary",demoSummary);finish(Activity.RESULT_OK,results);return }
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
