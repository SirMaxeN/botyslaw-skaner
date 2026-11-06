package eu.komars.forgescanner.ui

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import eu.komars.forgescanner.MainActivity
import eu.komars.forgescanner.ScannerApplication
import eu.komars.forgescanner.automation.*
import eu.komars.forgescanner.capture.CaptureConnection
import eu.komars.forgescanner.capture.CaptureState
import eu.komars.forgescanner.capture.FrameCallback
import eu.komars.forgescanner.model.*
import eu.komars.forgescanner.vision.*
import java.text.NumberFormat
import java.util.Locale

/** Separate deterministic mode: setup and live scanning never share an armed run. */
class AutoScanOverlay(private val context: Context, private val calibrations: CalibrationStore) : AutoScanRunner.Port {
    private val manager = context.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val store = AutoScanStore(context)
    private val checkpoints = AutoReadCheckpoints(context)
    private val screenFlow=GameNavigationGraph.Cursor()
    private var pendingSetupDestination:AutoScanStep?=null
    private val ocr = OcrAnalyzer()
    private val touchShield = AutomationTouchShield(context) {
        if (running) stop("Dotyk w czasie automatycznego skanu — zatrzymano dla bezpieczeństwa.")
        else if (validating) cancelValidation("Dotyk poza panelem — zatrzymano ustawianie.")
    }
    private var root: View? = null
    private var selector: ScreenSelectionWindow? = null
    private var selectedBitmap: Bitmap? = null
    private var dialog: AlertDialog? = null
    private var gesture: GestureSession? = null
    private var driver: AutoInventoryDriver? = null
    private var navigator: NavigationCoordinator? = null
    private var savedNavigator: SavedNavigationRunner? = null
    private var pendingNavigationFrame: FrameCallback? = null
    private var navigationFrameGeneration = 0L
    private val navigationWorker = java.util.concurrent.Executors.newSingleThreadExecutor { task ->
        Thread(task, "ScannerNavigation").apply { isDaemon = true }
    }
    private val counterCache = mutableMapOf<ScanTarget, AutoReading>()
    private var generation = 0
    private val calibrationFlow = CalibrationFlow()
    private val setupIndex: Int? get() = calibrationFlow.index
    private var setupRequested=false
    private var automaticSetupAttempted: AutoScanStep? = null
    private var validating = false
        set(value) {field=value;reportIndicator()}
    private var proposing = false
        set(value) {field=value;reportIndicator()}
    private var selecting = false
        set(value) {field=value;reportIndicator()}
    private var running = false
        set(value) {field=value;reportIndicator()}
    private var returningHome = false
        set(value) {field=value;reportIndicator()}
    private var status = "Rozpocznij na ekranie młotków. Nie dotykaj gry podczas automatycznego skanu."
    private var activeStep: AutoScanStep? = null
    private var panelWidth = 0
    private var panelHeight = 0
    private var panelBounds = Rect()
    private var protectedRegion: Rect? = null
    private var completed = false
    private var liveLabel: TextView? = null
    private var label: TextView? = null
    private var runtimePanel = false
    private var finishListButton: Button? = null
    private val runner = AutoScanRunner(this, AutoScanRunner.Scheduler { delay, action ->
        val task = Runnable(action); handler.postDelayed(task, delay); { handler.removeCallbacks(task) }
    }, { state ->
        running = state.status == AutoScanRunner.Status.RUNNING
        activeStep = state.step; status = state.message
        if (!running) {
            protectedRegion = null
            dialog?.dismiss(); dialog = null
        }
        if (state.status == AutoScanRunner.Status.FAILED) state.step?.let {
            // A delayed transition does not imply the saved button rectangle is wrong.
            if (it.isNavigation) { calibrationFlow.clear(); automaticSetupAttempted = null }
            else { calibrationFlow.beginRepair(it); automaticSetupAttempted = it }
        }
        show()
    }, { values ->
        val app = context.applicationContext as ScannerApplication
        // Only a complete route replaces the old session; every value still needs user review.
        app.acceptAutomaticReadings(values)
        completed = true
        openApp(true)
    })

    init {
        GestureConnection.addStateListener { _, foreground, detail ->
            if (running && !foreground) stop(detail ?: "Wyjście z gry — automatyczny skan zatrzymany.")
            else if (validationUsesGestures() && !foreground) cancelValidation(detail ?: "Gra nie jest widoczna — zatrzymano gesty.")
        }
    }

    private var compactIcons=false
    private fun reportIndicator() {(context.applicationContext as ScannerApplication).setScannerBusy("resources",proposing || ((running||validating||returningHome) && !selecting))}
    fun show() {
        if (CaptureConnection.state != CaptureState.READY || selecting) return
        if (!running && !validationUsesGestures()) { touchShield.hide(); GestureConnection.beforeGesture = null; GestureConnection.afterGesture = null; GestureConnection.inputSettleMillis = null }
        if(compactIcons && !running && !validating && !returningHome) {showIconBar();return}
        if (running && reuseRunningPanel()) return
        removePanel()
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = ScannerAppearance.overlayPanel(context, stepColor())
        }
        if (running || returningHome) box.addView(TextView(context).apply {
            text = "NIE DOTYKAJ EKRANU\nTRWA AUTOMATYCZNY ODCZYT"
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(ScannerAppearance.WARNING)
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(5), dp(4), dp(7))
        })
        val setup = setupIndex?.let { AutoScanStep.entries.getOrNull(it) }
        label = text(if (returningHome) "Powrót do ekranu młotków" else if (running) "▶ ${activeStep?.title ?: "Skan"}" else if (setup != null && validating) "Przejście · ${setup.title}" else if (setup != null) "Konfiguracja · ${ResourceSetupScreens.forStep(setup).title}" else "⌖ Automatyczny skan", 14f, true)
            .also { if (running) { it.minLines = 2; it.maxLines = 2 }; box.addView(it) }
        liveLabel = text(status, 11f).also {
            it.maxLines = if (running || returningHome) 3 else 4
            if (running || returningHome) it.minLines = 3
            box.addView(it)
        }
        if (setup != null && !running && !validating) box.addView(text(ResourceSetupScreens.forStep(setup).instruction, 11f))
        val actions = LinearLayout(context)
        if(!running && !validating && !returningHome) box.addView(button("Zwiń do ikon") {compactIcons=true;show()})
        if (validating) {
            actions.addView(button(if (returningHome) "STOP" else "Anuluj") { if (returningHome) (context.applicationContext as ScannerApplication).closeScanner() else cancelValidation() }, weight())
        } else if (running) {
            actions.addView(button("STOP") { (context.applicationContext as ScannerApplication).closeScanner() }, weight())
            finishListButton = button("Zakończ listę") { driver?.finishManually() }.also {
                // Reserve the slot so step changes do not resize the STOP panel.
                it.visibility = if (driver != null) View.VISIBLE else View.INVISIBLE
                actions.addView(it, weight())
            }
        } else if (completed) {
            actions.addView(button("Ponowny odczyt") { start() }, weight())
            actions.addView(button("Wyniki") { openApp(true) }, weight())
        } else if (setup != null) {
            actions.addView(button("Popraw pola ekranu") { configure(setup) }, weight())
            actions.addView(button("Menu") { menu() }, weight())
        } else {
            actions.addView(button(if (ready()) "Skanuj" else "Ustaw pola") {
                if (ready()) start() else { setupRequested=true;screenFlow.reset();pendingSetupDestination=null;calibrationFlow.beginAll(); automaticSetupAttempted = null; status = "Zacznij od ekranu młotków."; show() }
            }, weight())
            actions.addView(button("Menu") { menu() }, weight())
        }
        box.addView(actions)
        panelWidth = minOf(dp(282), context.resources.displayMetrics.widthPixels - dp(16))
        box.measure(View.MeasureSpec.makeMeasureSpec(panelWidth, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        panelHeight = box.measuredHeight
        val screen = physicalBounds()
        val forbidden = protectedRegion
        val desiredY = if (forbidden == null) dp(40) else OverlayPlacement.find(screen.height(), forbidden.top, forbidden.bottom, panelHeight, 0, dp(20), dp(8))
        if (desiredY == null) {
            if (running) stop("Brak miejsca na STOP poza listą. Zaznacz obszar samych ikon.")
            else if (returningHome) fail("Brak miejsca na przycisk STOP. Popraw obszar zamknięcia ekranu.")
            return
        }
        val params = WindowManager.LayoutParams(panelWidth, -2, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT).apply {
            if (Build.VERSION.SDK_INT >= 30) { setFitInsetsTypes(0); layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS }
            gravity = Gravity.TOP or Gravity.START; x = dp(8); y = desiredY
        }
        panelBounds = Rect(params.x, params.y, params.x + panelWidth, params.y + panelHeight)
        root = box; runtimePanel = running
        runCatching { manager.addView(box, params) }.onFailure {
            root = null
            if (running) stop("Nie można wyświetlić przycisku STOP.")
            else if (returningHome) fail("Nie można wyświetlić przycisku STOP.")
        }
        if ((running || validationUsesGestures()) && root != null) {
            if (!touchShield.show(panelBounds)) { if (running) stop("Nie można włączyć osłony dotyku. Automat zatrzymany.") else cancelValidation("Nie można włączyć osłony dotyku."); return }
            GestureConnection.beforeGesture = { region -> (running || validationUsesGestures()) && touchShield.openGesture(region) }
            GestureConnection.afterGesture = { touchShield.closeGesture() }
            GestureConnection.inputSettleMillis = { touchShield.inputSettleMillis() }
        }

            if(setupRequested && !running && !validating && setup!=null && automaticSetupAttempted!=setup) {
            automaticSetupAttempted=setup
            handler.post {if(calibrationFlow.current==setup && !running && !selecting && !validating)configure(setup)}
        }
    }

    /** Keep the live window and input holes stable; changing labels needs no WindowManager rebuild. */
    private fun reuseRunningPanel(): Boolean {
        val panel = root ?: return false
        if (!runtimePanel) return false
        val params = panel.layoutParams as? WindowManager.LayoutParams ?: return false
        val screen = physicalBounds()
        val width = minOf(dp(282), context.resources.displayMetrics.widthPixels - dp(16))
        if (width != panelWidth) return false
        label?.text = "▶ ${activeStep?.title ?: "Skan"}"
        label?.setTextColor(stepColor())
        liveLabel?.text = status
        (panel.background as? GradientDrawable)?.setStroke(dp(2), stepColor())
        finishListButton?.visibility = if (driver != null) View.VISIBLE else View.INVISIBLE
        val desiredY = if(protectedRegion==null || !Rect.intersects(panelBounds,protectedRegion!!)) params.y else protectedRegion?.let {
            OverlayPlacement.find(screen.height(), it.top, it.bottom, panelHeight, 0, dp(20), dp(8))
        }
        if (desiredY == null) {
            stop("Brak miejsca na STOP poza listą. Zaznacz obszar samych ikon.")
            return true
        }
        val nextBounds = Rect(dp(8), desiredY, dp(8) + panelWidth, desiredY + panelHeight)
        if (nextBounds != panelBounds) {
            params.x = nextBounds.left; params.y = nextBounds.top
            if (runCatching { manager.updateViewLayout(panel, params) }.isFailure) {
                stop("Nie można wyświetlić przycisku STOP.")
                return true
            }
            panelBounds = nextBounds
        }
        if (!touchShield.show(panelBounds)) stop("Nie można włączyć osłony dotyku. Automat zatrzymany.")
        return true
    }

    fun hide() {
        removePreviewReturn();setupRequested=false;screenFlow.reset();pendingSetupDestination=null
        generation++; runner.cancel(); cancel(); removePanel()
        selector?.close(); selector = null
        selectedBitmap?.let(::recycle); selectedBitmap = null
        dialog?.dismiss(); dialog = null; proposing = false; selecting = false; validating = false; returningHome = false
    }
    fun resetSession() {
        hide(); calibrationFlow.clear(); automaticSetupAttempted = null; completed = false; activeStep = null
        status = "Rozpocznij na ekranie młotków."
    }
    fun clearSettings() { hide(); store.clear(); checkpoints.clear(); calibrationFlow.clear(); automaticSetupAttempted = null; completed = false }
    private fun ready() = store.isReady(calibrations)
    private fun start() {
        if (running || validating || selecting) return
        if (!ready()) { status = "Uzupełnij brakujące pola ekranów w konfiguracji."; show(); return }
        generation++; counterCache.clear(); calibrationFlow.clear(); automaticSetupAttempted = null; completed = false
        returningHome = true; validating = true
        status = "Przygotowuję odczyt i wracam do ekranu młotków…"; show()
        val bounds = physicalBounds()
        val token = generation
        GestureConnection.requestArm(bounds.width(), bounds.height()) { result ->
            if (token != generation || !returningHome || CaptureConnection.state != CaptureState.READY) { GestureConnection.cancel(); return@requestArm }
            result.fold({ gesture = it; returnHome(token) }, { fail(it.message ?: "Nie można uruchomić gestów.") })
        }
    }

    /** Screen detection is used once to recover the start, never at each route step. */
    private fun returnHome(token: Int, closeAttempted: Boolean = false, attempt: Int = 0) {
        if (token != generation || !returningHome || !validating) return
        capture { bitmap ->
            show() // The clean frame is retained; restore STOP before classifying it.
            if (token != generation || !returningHome || !validating) { recycle(bitmap); return@capture }
            val kind = GameUiDetector().screenKind(bitmap)
            val width = bitmap.width; val height = bitmap.height
            recycle(bitmap)
            when (val action = HomeReturnPlan.next(kind, closeAttempted)) {
                HomeReturnPlan.Action.Ready -> {
                    returningHome = false; validating = false; protectedRegion = null
                    screenFlow.observe(GameNavigationGraph.Screen.HOME);runner.start()
                }
                is HomeReturnPlan.Action.Close -> {
                    val saved = store.navigation(action.step, width, height)
                    val session = gesture
                    if (saved?.after == null || session == null) {
                        fail("Ustaw przycisk zamknięcia: ${action.step.title}."); return@capture
                    }
                    protectedRegion = saved.region
                    status = "Zamykam otwarty ekran i wracam do młotków…"; show()
                    GestureConnection.tap(session, saved.x, saved.y) { result ->
                        if (token != generation || !returningHome || !validating) return@tap
                        if (result.isFailure) fail(result.exceptionOrNull()?.message ?: "Nie udało się zamknąć ekranu.")
                        else handler.postDelayed({if(token==generation && returningHome && validating) {
                            returningHome=false;validating=false;protectedRegion=null;screenFlow.observe(GameNavigationGraph.Screen.HOME);runner.start()
                        }}, 350)
                    }
                }
                HomeReturnPlan.Action.Unsupported -> {
                    if (attempt < 2) {
                        status = "Czekam na główny ekran gry…"; show()
                        handler.postDelayed({ returnHome(token, closeAttempted, attempt + 1) }, 250)
                    } else fail("Nie rozpoznaję głównego ekranu. Zamknij dodatkowe okna i ponów odczyt.")
                }
            }
        }
    }

    override fun perform(step: AutoScanStep, complete: (Result<AutoReading?>) -> Unit) {
        val started = android.os.SystemClock.elapsedRealtime()
        val token = generation
        fun finish(result: Result<AutoReading?>) {
            if(token==generation && running) result.getOrNull()?.let {reading ->step.target?.let {target ->
                (context.applicationContext as ScannerApplication).session.setValue(target.field,reading.value,confirmed=false,approximate=reading.approximate,sourceText=reading.source)
            }}
            if (eu.komars.forgescanner.BuildConfig.DEBUG) android.util.Log.d("ScannerStep",
                "step=${step.name} ms=${android.os.SystemClock.elapsedRealtime() - started} success=${result.isSuccess}")
            complete(result)
        }
        if (step.isNavigation) {
            navigate(step, token, ::finish); return
        }
        val target = requireNotNull(step.target)
        counterCache.remove(target)?.let { finish(Result.success(it)); return }
        val bounds = physicalBounds()
        val saved = calibrations.get(target, bounds.width(), bounds.height())
        if (saved == null) { finish(failure("Ustaw ponownie obszar: ${step.title}.")); return }
        protectedRegion = saved.region
        show()
        capture { bitmap ->
            val calibration = calibrations.get(target, bitmap.width, bitmap.height)
            if (calibration == null) { recycle(bitmap); finish(failure("Ustaw ponownie obszar: ${step.title}.")); return@capture }
            if (target.isInventory) {
                val session = gesture
                if (session == null) { recycle(bitmap); finish(failure("Sterowanie gestami zostało zatrzymane.")); return@capture }
                // Use the same saved grid/recording pipeline as manual mode.
                // Artwork and inventory contents can change between scans.
                protectedRegion = calibration.region
                driver = AutoInventoryDriver()
                show()
                val center = calibration.region.exactCenterX().toInt()
                val strip = Rect((center - 24).coerceAtLeast(0), calibration.region.top,
                    (center + 25).coerceAtMost(bitmap.width), calibration.region.bottom)
                if (!touchShield.pinScrollCorridor(strip)) {
                    driver = null; recycle(bitmap)
                    finish(failure("Nie można przygotować bezpiecznego przewijania listy.")); return@capture
                }
                driver!!.start(bitmap, target, calibration, session, { token == generation && running }, { hint, count ->
                    if (token == generation) liveLabel?.text = "$hint\nOdczytano: ${NumberFormat.getIntegerInstance(Locale.forLanguageTag("pl-PL")).format(count)}"
                }) { result ->
                    if (token == generation && running) { driver = null; touchShield.releaseScrollCorridor(); protectedRegion = null; finish(result.map { it }) }
                }
            } else readCounter(step, target, calibration, bitmap, token, ::finish)
        }
    }

    private fun readCounter(step: AutoScanStep, target: ScanTarget, calibration: SavedCalibration,
                            bitmap: Bitmap, token: Int, complete: (Result<AutoReading?>) -> Unit, attempt: Int = 0,
                            screenRepairAttempted: Boolean = false, emptyCompleteBursts: Int = 0) {
        // Read both independently marked counters from the same three source
        // frames. A verified cost is reused by the immediately following step.
        val regions = linkedMapOf(target to Rect(calibration.region))
        val paired = when (target) {
            ScanTarget.MOUNT_KEYS -> ScanTarget.MOUNT_COST
            ScanTarget.TICKETS -> ScanTarget.SKILL_COST
            else -> null
        }
        if (paired != null && !counterCache.containsKey(paired)) {
            val other = calibrations.get(paired, bitmap.width, bitmap.height)
            // Keep STOP visible: never include a second crop covered by our panel.
            if (other != null && !Rect.intersects(panelBounds, other.region)) regions[paired] = other.region
        }
        var sourceFrames = 0
        CounterBurstReader(ocr, diagnostics = { metrics ->
            sourceFrames = metrics.sourceFrames
            if (eu.komars.forgescanner.BuildConfig.DEBUG) android.util.Log.d("ScannerCounter",
                "fields=${metrics.fields} frames=${metrics.sourceFrames} passes=${metrics.ocrPasses} ms=${metrics.elapsedMillis}")
        }).readMany(bitmap, regions, { token == generation && running }) { analyses ->
            if (token != generation || !running) return@readMany
            fun verified(counter: ScanTarget): AutoReading? {
                val analysis = analyses[counter] ?: return null
                val candidates = OcrChoices.unique(analysis.candidates, counter.field.isCost).filter { it.field == counter.field }
                if (analysis.error != null || analysis.requiresConfirmation || candidates.size != 1) return null
                return AutoReading(candidates[0].value, approximate = candidates[0].approximate, source = candidates[0].sourceText)
            }
            paired?.let { other -> verified(other)?.let { counterCache[other] = it } }
            val reading = verified(target)
            val analysis = analyses[target] ?: OcrAnalysis(emptyList(), emptyList(), "Brak odczytu tego pola.", true)
            val choices = OcrChoices.unique(analysis.candidates, target.field.isCost).filter { it.field == target.field }
            val emptyBursts = if (sourceFrames == CounterOcrConsensus.FRAME_COUNT && choices.isEmpty()) emptyCompleteBursts + 1 else 0
            if (reading != null) {
                complete(Result.success(reading))
            } else if (attempt == 0) {
                liveLabel?.text = "${step.title}: ponawiam odczyt zapisanego pola…"
                handler.postDelayed({
                    if (token == generation && running) capture { fresh ->
                        readCounter(step, target, calibration, fresh, token, complete, attempt + 1, screenRepairAttempted, emptyBursts)
                    }
                }, 100)
            } else if (CriticalReadRecoveryPolicy.eligible(emptyBursts, choices.size, sourceFrames,
                    screenRepairAttempted, CaptureConnection.state == CaptureState.READY, GestureConnection.foregroundGame)) {
                repairEmptyCounter(step, target, calibration, analysis, token, complete)
            } else showCounterReadResolution(step, target, calibration, analysis, token, complete)
        }
    }

    /** Screen recognition is a fallback only after both complete OCR bursts found no value. */
    private fun repairEmptyCounter(step: AutoScanStep, target: ScanTarget, calibration: SavedCalibration,
                                   analysis: OcrAnalysis, token: Int, complete: (Result<AutoReading?>) -> Unit) {
        val destination = CriticalReadRecoveryPolicy.expectedNavigation(target)
        fun resolve(problem: String? = null) {
            if (token == generation && running) showCounterReadResolution(step, target, calibration,
                if (problem == null) analysis else analysis.copy(error = problem), token, complete)
        }
        if (destination == null) { resolve(); return }
        liveLabel?.text = "Brak odczytu po dwóch próbach — sprawdzam bieżący ekran…"
        observeNavigation(token) { observed ->
            val kind = observed.getOrNull()?.kind ?: GameScreenKind.UNKNOWN
            // Correct screen + empty OCR is a crop/readability problem, never a reason to navigate.
            if (kind == GameScreenKind.UNKNOWN || NavigationScreenPolicy.accepts(destination, kind)) resolve()
            else recoverNavigation(destination, token) { restored ->
                if (token != generation || !running) return@recoverNavigation
                if (restored.isFailure) resolve(restored.exceptionOrNull()?.message)
                else {
                    val related = when (target) {
                        ScanTarget.MOUNT_KEYS, ScanTarget.MOUNT_COST -> listOf(ScanTarget.MOUNT_KEYS, ScanTarget.MOUNT_COST)
                        ScanTarget.TICKETS, ScanTarget.SKILL_COST -> listOf(ScanTarget.TICKETS, ScanTarget.SKILL_COST)
                        else -> listOf(target)
                    }
                    related.forEach(counterCache::remove)
                    capture { fresh ->
                        if (token != generation || !running) recycle(fresh)
                        else readCounter(step, target, calibration, fresh, token, complete, attempt = 1, screenRepairAttempted = true)
                    }
                }
            }
        }
    }

    /** A readable OCR conflict needs human confirmation, not repeated geometry replacement. */
    private fun showCounterReadResolution(step: AutoScanStep, target: ScanTarget, calibration: SavedCalibration,
                                         analysis: OcrAnalysis, token: Int, complete: (Result<AutoReading?>) -> Unit) {
        fun current() = token == generation && running && activeStep == step &&
            CaptureConnection.state == CaptureState.READY
        if (!current()) return
        val choices = OcrChoices.unique(analysis.candidates, target.field.isCost).filter { it.field == target.field }
        val app=context.applicationContext as ScannerApplication
        val remembered=if(!app.demo && target.field.isCost) eu.komars.forgescanner.model.ConfirmedCostPolicy.choose(
            eu.komars.forgescanner.model.ConfirmedCostMemory(context).get(app.mobile.name.orEmpty(),target.field),choices) else null
        if(remembered!=null) {complete(Result.success(AutoReading(remembered.value,source="Bieżący odczyt zgodny z zatwierdzonym kosztem: ${remembered.sourceText}")));return}
        liveLabel?.text = if (choices.isEmpty()) "Ponów odczyt tego samego pola lub popraw zaznaczenie."
            else "Wybierz liczbę widoczną w grze. Zaznaczenie pozostaje bez zmian."
        var prompt: AlertDialog? = null
        fun activePrompt() = current() && dialog === prompt
        fun dismissPrompt() { prompt?.dismiss(); if (dialog === prompt) dialog = null }
        fun retry() {
            if (!activePrompt()) return
            dismissPrompt()
            liveLabel?.text = "${step.title}: ponawiam odczyt tej samej ramki…"
            capture { fresh ->
                if (!current()) { recycle(fresh); return@capture }
                readCounter(step, target, calibration, fresh, token, complete, attempt = 1)
            }
        }
        fun redraw() {
            if (!activePrompt()) return
            dismissPrompt()
            complete(failure("${step.title}: popraw zaznaczenie tylko tego pola."))
        }
        fun cancelRead() {
            if (!activePrompt()) return
            dismissPrompt()
            stop("Zatrzymano. Nie zapisano częściowych wyników.")
        }
        val builder = AlertDialog.Builder(context).setTitle(
            if (choices.isEmpty()) "${step.title}: brak czytelnej liczby" else "${step.title}: wybierz liczbę z ekranu")
        if (choices.isEmpty()) {
            builder.setMessage(analysis.error ?: "Nie udało się potwierdzić odczytu. Możesz spróbować ponownie bez zmiany ramki.")
        } else {
            val labels = choices.map { candidate ->
                (if (candidate.approximate) "≈ " else "") +
                    NumberFormat.getIntegerInstance(Locale.forLanguageTag("pl-PL")).format(candidate.value)
            }.toTypedArray()
            // Tapping a value is an explicit choice; no value is preselected or auto-accepted.
            builder.setItems(labels) { _, which ->
                if (activePrompt() && which in choices.indices) {
                    val chosen = choices[which]
                    dismissPrompt()
                    complete(Result.success(AutoReading(chosen.value, approximate = chosen.approximate,
                        source = "Potwierdzono ręcznie: ${chosen.sourceText}")))
                }
            }
        }
        val alert = builder.setPositiveButton("Odczytaj ponownie") { _, _ -> retry() }
            .setNeutralButton("Popraw pole") { _, _ -> redraw() }
            .setNegativeButton("Zatrzymaj") { _, _ -> cancelRead() }
            .create()
        prompt = alert
        alert.setOnCancelListener { cancelRead() }
        showDialog(alert,peek=true)
    }

    /** Normal route: publish the step, render the label, tap its saved coordinate once, then settle. */
    private fun navigate(step: AutoScanStep, token: Int, complete: (Result<AutoReading?>) -> Unit) {
        savedNavigator?.cancel()
        var tapEpoch = 0L
        val route = SavedNavigationRunner(object : SavedNavigationRunner.Port {
            override fun tap(step: AutoScanStep, complete: (Result<Unit>) -> Unit) {
                val epoch = tapEpoch
                val action = Runnable {
                    if (epoch == tapEpoch && token == generation && running) {
                        if (activeStep != step) complete(Result.failure(IllegalStateException("Krok nawigacji zmienił się przed kliknięciem.")))
                        else tapNavigation(step, token, recovering = false, complete)
                    }
                }
                // The state is already published by AutoScanRunner. Yield one UI frame
                // so the displayed label can catch up before the confirmed tap begins.
                root?.postOnAnimation { handler.post(action) } ?: handler.post(action)
            }
            override fun cancel() { tapEpoch++ }
        }, SavedNavigationRunner.Scheduler { delay, action ->
            val task = Runnable(action); handler.postDelayed(task, delay); { handler.removeCallbacks(task) }
        })
        savedNavigator = route
        route.start(step) { result ->
            if (token == generation && running) { savedNavigator = null; complete(result.map { null }) }
        }
    }

    /** Only a failed read may enter the expensive screen-aware restoration route. */
    private fun recoverNavigation(step: AutoScanStep, token: Int, complete: (Result<AutoReading?>) -> Unit) {
        navigator?.cancel()
        var navigationStep = step
        val coordinator = NavigationCoordinator(object : NavigationCoordinator.Port {
            override fun inspect(complete: (Result<GameScreenKind>) -> Unit) {
                observeNavigation(token) { result ->
                    if (eu.komars.forgescanner.BuildConfig.DEBUG) android.util.Log.d("ScannerNavigation",
                        "step=${navigationStep.name} screen=${result.getOrNull()?.kind ?: "capture-error"}")
                    complete(result.map { it.kind })
                }
            }
            override fun tap(step: AutoScanStep, recovering: Boolean, complete: (Result<Unit>) -> Unit) {
                tapNavigation(step, token, recovering, complete)
            }
            override fun cancel() = cancelNavigationFrame()
        }, NavigationCoordinator.Scheduler { delay, action ->
            val task = Runnable(action); handler.postDelayed(task, delay); { handler.removeCallbacks(task) }
        }, { android.os.SystemClock.uptimeMillis() }, { current, recovering ->
            navigationStep = current
            if (eu.komars.forgescanner.BuildConfig.DEBUG) android.util.Log.d("ScannerNavigation", "step=${current.name} recovery=$recovering")
            if (token == generation && running) liveLabel?.text =
                if (recovering) "Przywracam właściwy ekran: ${current.title}…"
                else "Czekam na gotowość: ${current.title}…"
        })
        navigator = coordinator
        coordinator.start(step) { result ->
            if (token == generation && running) {
                navigator = null
                complete(result.map { null })
            }
        }
    }

    private fun tapNavigation(step: AutoScanStep, token: Int, recovering: Boolean, complete: (Result<Unit>) -> Unit) {
        val bounds = physicalBounds()
        val saved = store.navigation(step, bounds.width(), bounds.height())
        if (saved?.after == null) { complete(Result.failure(IllegalStateException("Brakuje zapisanego przycisku: ${step.title}."))); return }
        fun tap(point: Rect) {
            if (token != generation || !running) return
            val session = gesture
            if (session == null) { complete(Result.failure(IllegalStateException("Sterowanie gestami zostało zatrzymane."))); return }
            ensurePanelAway(point)
            if (Rect.intersects(panelBounds, point)) { complete(Result.failure(IllegalStateException("Panel zasłania przycisk. Popraw obszar."))); return }
            GestureConnection.tap(session, point.exactCenterX(), point.exactCenterY()) { result ->
                if (token == generation && running) {
                    if(result.isSuccess)GameNavigationGraph.resourceAction(step.name)?.let {action ->
                        if(screenFlow.allows(action))screenFlow.completed(action)else screenFlow.reset()
                    }
                    complete(result)
                }
            }
        }
        if (recovering && isCloseStep(step)) {
            // Recovery uses the X visible now, not an old coordinate from another modal.
            observeNavigation(token, step) { frame ->
                frame.fold({ observed ->
                    if (observed.kind == GameScreenKind.HOME) complete(Result.success(Unit))
                    else if (observed.close == null) complete(Result.failure(IllegalStateException("Nie rozpoznaję pewnego X do zamknięcia okna.")))
                    else tap(observed.close)
                }, { complete(Result.failure(it)) })
            }
        } else tap(saved.region)
    }

    private fun observeNavigation(token: Int, closeStep: AutoScanStep? = null,
                                  complete: (Result<GameUiDetector.Observation>) -> Unit) {
        navigationFrame(token) { frame ->
            frame.fold({ bitmap ->
                val epoch = navigationFrameGeneration
                navigationWorker.execute {
                    val observed = try { runCatching { GameUiDetector().observe(bitmap, closeStep) } }
                        finally { recycle(bitmap) }
                    handler.post {
                        if (token == generation && epoch == navigationFrameGeneration && running) complete(observed)
                    }
                }
            }, { complete(Result.failure(it)) })
        }
    }

    private fun navigationFrame(token: Int, complete: (Result<Bitmap>) -> Unit) {
        val epoch = navigationFrameGeneration
        val coveredPanel=root
        coveredPanel?.visibility=View.INVISIBLE
        lateinit var request: FrameCallback
        request = { bitmap, error ->
            if (pendingNavigationFrame === request) pendingNavigationFrame = null
            if(token==generation&&epoch==navigationFrameGeneration&&root===coveredPanel)coveredPanel?.visibility=View.VISIBLE
            if (token != generation || epoch != navigationFrameGeneration || !running) bitmap?.let(::recycle)
            else if (bitmap == null) complete(Result.failure(IllegalStateException(error ?: "Brak nowej klatki przejścia.")))
            else {
                val bounds = physicalBounds()
                if (bitmap.width != bounds.width() || bitmap.height != bounds.height()) {
                    recycle(bitmap); complete(Result.failure(IllegalStateException("Ekran zmienił rozmiar. Ponów ustawianie pól.")))
                } else complete(Result.success(bitmap))
            }
        }
        pendingNavigationFrame = request
        // Recovery classifies the whole game, so it must not recognize our own panel.
        // Hiding the panel produces a new frame even on a static destination. The independent
        // Bot skanuje indicator remains visible and is masked by the capture service.
        handler.postDelayed({
            if(token==generation&&epoch==navigationFrameGeneration&&running&&pendingNavigationFrame===request)
                CaptureConnection.requestFreshFrame(request)
        },130)
    }
    private fun cancelNavigationFrame() {
        navigationFrameGeneration++
        pendingNavigationFrame?.let { CaptureConnection.cancelFrameRequest(it) }
        pendingNavigationFrame = null
    }

    override fun cancel() {
        generation++; savedNavigator?.cancel(); savedNavigator = null
        navigator?.cancel(); navigator = null; cancelNavigationFrame()
        counterCache.clear(); driver?.cancel(); driver = null; gesture = null
        GestureConnection.beforeGesture = null; GestureConnection.afterGesture = null; GestureConnection.inputSettleMillis = null
        touchShield.hide(); GestureConnection.cancel()
    }
    private fun stop(message: String) { runner.cancel(); cancel(); running = false; returningHome = false; validating = false; status = message; protectedRegion = null; show() }
    private fun fail(message: String) {
        screenFlow.reset();pendingSetupDestination=null
        if (returningHome) { GestureConnection.cancel(); gesture = null }
        returningHome = false; proposing = false; selecting = false; validating = false; protectedRegion = null; status = message; show()
    }

    private fun configure(step: AutoScanStep) {
        setupRequested=true
        if (selecting || running || validating) return
        val token = generation
        selecting = true; proposing = true
        capture { bitmap ->
            if (token != generation || calibrationFlow.current != step) { proposing = false; selecting = false; recycle(bitmap); show(); return@capture }
            val width = bitmap.width; val height = bitmap.height
            pendingSetupDestination?.let {previous ->
                store.saveDestination(previous,ScreenCheckpoint.capture(bitmap,masks(previous,width,height)))
                pendingSetupDestination=null
            }
            selectedBitmap = bitmap
            val savedNavigation=if(step.isNavigation)store.navigation(step,width,height)?.region else null
            val sharedClose=if(step==AutoScanStep.CLOSE_RESEARCH)store.navigation(AutoScanStep.OPEN_RESEARCH,width,height)?.region else null
            if(sharedClose!=null || step.isNavigation && calibrationFlow.isApproved(step) && savedNavigation!=null) {
                proposing=false;selecting=false;selectedBitmap=null
                navigateDuringSetup(step,sharedClose?:requireNotNull(savedNavigation),bitmap,token);return@capture
            }
            val known=GameNavigationGraph.resourceScreen(screenFlow.screen)
            val visible=if(step==AutoScanStep.OPEN_SKILLS || step==AutoScanStep.OPEN_PETS) GameScreenKind.UNKNOWN else known
            if(step==AutoScanStep.OPEN_SKILLS || step==AutoScanStep.OPEN_PETS)screenFlow.observe(GameNavigationGraph.Screen.RESEARCH)
            else if(visible!=GameScreenKind.UNKNOWN)screenFlow.observe(GameNavigationGraph.fromResource(visible))
            val screen=ResourceSetupScreens.forStep(step,visible)
            proposing=false
            showResourceFields(step,screen,bitmap,token)
        }
    }
    private fun showResourceFields(step:AutoScanStep,screen:ResourceSetupScreens.Screen,bitmap:Bitmap,token:Int) {
        val group=screen.fields
        val w=bitmap.width;val h=bitmap.height
        val suggestions=group.associateWith {item ->
            val proposed=ResourceFieldProposal.detect(bitmap,item)
            val saved=if(item.isNavigation)store.navigation(item,w,h)?.region else item.target?.let {calibrations.get(it,w,h)?.region}
            if(calibrationFlow.isApproved(item))saved else proposed?.container?:proposed?.region?:saved
        }
        val fields=group.associate {item->item.name to (item.title to suggestions[item])}.toMutableMap()
        val tabs=step in listOf(AutoScanStep.OPEN_SKILLS,AutoScanStep.OPEN_PETS)
        if(tabs)fields["TECHNOLOGY_TAB"]="Drzewo technologii" to (store.technologyTab(w,h)?:GameUiDetector().technologyTab(bitmap))
        selector=ScreenSelectionWindow(context,bitmap,false,{_,_,_->},{selector=null;selectedBitmap=null;selecting=false;recycle(bitmap);status="Konfiguracja ${screen.title}: anulowano zaznaczanie. Możesz poprawić pola ponownie.";show()},
            titleText="Konfiguracja · ${screen.title}",instructionText=screen.instruction+" Wybierz pole po prawej, popraw ramkę i zatwierdź cały ekran. Liczby odczytamy podczas skanu.",minimumSelectionSize=8,
            fields=fields,onFields={values ->
                selector=null;selectedBitmap=null;selecting=false
                if(token!=generation || calibrationFlow.current!=step){recycle(bitmap);return@ScreenSelectionWindow}
                try {
                    val forbidden=GameUiDetector().forbiddenActionAreas(bitmap)
                    val regions=group.associateWith {values.getValue(it.name)}
                    if(tabs)store.saveTechnologyTab(values.getValue("TECHNOLOGY_TAB"),w,h)
                    // Validate every field before writing any of this screen's configuration.
                    if(group.any {it.isNavigation && forbidden.any {area->area.contains(regions.getValue(it).centerX(),regions.getValue(it).centerY())}}) {
                        recycle(bitmap);fail("Przycisk nawigacji wskazuje miejsce wydawania zasobów. Popraw pola ekranu ${screen.title}.");return@ScreenSelectionWindow
                    }
                    val fitted=group.filter {it.isInventory}.associateWith {item ->
                        val r=regions.getValue(item)
                        InventoryGridCalibrator().refine(bitmap,r,5,r)
                    }
                    group.filter {!it.isNavigation}.forEach {item ->
                        val r=regions.getValue(item);val target=requireNotNull(item.target);val grid=fitted[item]
                        if(grid!=null)calibrations.save(target,grid.region,w,h,grid.columns,grid.rowHeightPx)
                        else calibrations.save(target,r,w,h,1,r.height())
                    }
                    group.forEach {item ->
                        val checkpoint=ScreenCheckpoint.capture(bitmap,masks(item,w,h))
                        if(item.isNavigation) {
                            val previous=store.navigation(item,w,h)
                            if(previous?.region!=regions.getValue(item)) {
                                store.saveNavigation(item,regions.getValue(item),w,h,checkpoint)
                                previous?.after?.let {store.saveDestination(item,it)}
                            }
                        }
                        else if(checkpoint.valid)checkpoints.save(item,checkpoint)
                    }
                    if(screenFlow.screen==GameNavigationGraph.Screen.UNKNOWN) {
                        val observed=when(step) {
                            AutoScanStep.HAMMERS,AutoScanStep.OPEN_MOUNTS,AutoScanStep.OPEN_RESEARCH->GameNavigationGraph.Screen.HOME
                            AutoScanStep.MOUNT_KEYS,AutoScanStep.MOUNT_COST,AutoScanStep.MOUNT_LIST,AutoScanStep.CLOSE_MOUNTS->GameNavigationGraph.Screen.MOUNTS
                            AutoScanStep.SKILL_TICKETS,AutoScanStep.SKILL_COST->GameNavigationGraph.Screen.SKILLS
                            AutoScanStep.PET_LIST->GameNavigationGraph.Screen.PETS
                            else->GameNavigationGraph.Screen.RESEARCH
                        }
                        screenFlow.observe(observed)
                    }
                    calibrationFlow.approve(group)
                    if(step.isNavigation) {
                        // Reuse this same clean frame for the next route action, after all fields are saved.
                        navigateDuringSetup(step,regions.getValue(step),bitmap,token)
                    } else {
                        recycle(bitmap);advanceSetup(step,"Zapisano wszystkie pola: ${screen.title}.")
                    }
                } catch(_:Exception){recycle(bitmap);fail("Nie udało się zapisać pól ekranu ${screen.title}. Popraw ramki.")}
            })
        selector!!.show()
    }
    private fun isCloseStep(step: AutoScanStep) = step == AutoScanStep.CLOSE_MOUNTS || step == AutoScanStep.CLOSE_RESEARCH
    private fun navigateDuringSetup(step: AutoScanStep, rect: Rect, bitmap: Bitmap, token: Int) {
        val width = bitmap.width; val height = bitmap.height
        val action=GameNavigationGraph.resourceAction(step.name)
        if(action==null || !screenFlow.allows(action)) {recycle(bitmap);fail("Bieżący ekran nie pasuje do zapisanego przejścia. Popraw pola tego ekranu.");return}
        val before = ScreenCheckpoint.capture(bitmap, masks(step, width, height))
        val forbiddenCost = when (step) {
            AutoScanStep.CLOSE_MOUNTS -> ScanTarget.MOUNT_COST
            AutoScanStep.OPEN_SKILLS, AutoScanStep.OPEN_PETS, AutoScanStep.CLOSE_RESEARCH -> ScanTarget.SKILL_COST
            else -> null
        }
        val cost = forbiddenCost?.let { calibrations.get(it, width, height)?.region }
        val inSummonButton = GameUiDetector().forbiddenActionAreas(bitmap).any { it.contains(rect.centerX(), rect.centerY()) }
        if (inSummonButton || cost != null && Rect(cost).apply {
                val originalHeight = height()
                inset(-maxOf(12, width() / 5), 0); top -= originalHeight; bottom += originalHeight / 4
            }.contains(rect.centerX(), rect.centerY())) {
            recycle(bitmap); fail("To miejsce może wydawać zasoby. Wskaż przycisk nawigacji."); return
        }
        store.saveNavigation(step, rect, width, height, before)
        recycle(bitmap); validating = true; protectedRegion = rect
        status = if (isCloseStep(step)) "Zamykam zapisanym przyciskiem. Nie dotykaj ekranu." else "Otwieram: ${step.title}. Nie dotykaj ekranu."; show()
        if (Rect.intersects(panelBounds, rect)) { validating = false; fail("Panel zasłania przycisk. Spróbuj ponownie."); return }
        GestureConnection.requestArm(width, height) { result ->
            if (token != generation || !validating) { GestureConnection.cancel(); return@requestArm }
            result.fold({ session ->
                gesture = session
                GestureConnection.tap(session, rect.exactCenterX(), rect.exactCenterY()) { tapped ->
                    if (token != generation || !validating) return@tap
                    gesture = null; GestureConnection.cancel()
                    if (tapped.isFailure) { validating = false; fail(tapped.exceptionOrNull()?.message ?: "Nie udało się przejść do ekranu.") }
                    else {
                        screenFlow.completed(action)
                        handler.postDelayed({
                            if(token==generation && validating && calibrationFlow.current==step) {
                                if(calibrationFlow.isRepair || step==AutoScanStep.CLOSE_RESEARCH)captureSetupDestination(step,token,0)
                                else {pendingSetupDestination=step;validating=false;protectedRegion=null;advanceSetup(step,"Przejście wykonane. Przygotowuję pola kolejnego ekranu.")}
                            }
                        },350)
                    }
                }
            }, { validating = false; fail(it.message ?: "Włącz gesty i ponownie otwórz grę.") })
        }
    }
    private fun captureSetupDestination(step: AutoScanStep, token: Int, attempt: Int) {
        handler.postDelayed({
            if (token != generation || !validating || calibrationFlow.current != step) return@postDelayed
            capture { after ->
                if (token != generation || calibrationFlow.current != step) { recycle(after); return@capture }
                store.saveDestination(step,ScreenCheckpoint.capture(after,masks(step,after.width,after.height)))
                recycle(after);validating=false;protectedRegion=null
                advanceSetup(step,"Przejście wykonane. Pola zapisane.")
            }
        }, 450)
    }
    private fun validationUsesGestures() = validating && (returningHome || calibrationFlow.current?.isNavigation == true)
    private fun cancelValidation(reason: String = "Zatrzymano ustawianie.") {
        screenFlow.reset();pendingSetupDestination=null
        generation++; returningHome = false; validating = false; protectedRegion = null; gesture = null
        GestureConnection.cancel(); status = reason; show()
    }
    private fun advanceSetup(step: AutoScanStep, detail: String = "Zapisano.") {
        if (calibrationFlow.current != step) return
        val repaired = calibrationFlow.isRepair
        calibrationFlow.advance(step); automaticSetupAttempted = null
        status = if (calibrationFlow.isComplete) {
            if (repaired) "$detail Pole poprawione. Wróć do młotków i uruchom skan." else "$detail Ustawienia gotowe. Rozpocznij od młotków."
        } else detail
        show()
    }
    private fun masks(step: AutoScanStep, width: Int, height: Int, replacement: Pair<ScanTarget, SavedCalibration>? = null): List<Rect> {
        val excluded = mutableListOf<Rect>()
        for (target in ScanTarget.entries) {
            val calibration = (if (replacement?.first == target) replacement.second else calibrations.get(target, width, height)) ?: continue
            val region = calibration.region
            if (step.target == target && target.isInventory) {
                // Keep only the already-owned base slots in row 1 as the top anchor.
                val rowBottom = minOf(region.bottom, region.top + calibration.rowHeightPx)
                if (rowBottom < region.bottom) excluded += Rect(region.left, rowBottom, region.right, region.bottom)
                val baseRight = minOf(region.right, region.left + region.width() * target.baseCount / calibration.columns)
                if (baseRight < region.right) excluded += Rect(baseRight, region.top, region.right, rowBottom)
            } else excluded += region
        }
        // Main battle animation is irrelevant to navigation and the hammer counter.
        if (step in listOf(AutoScanStep.HAMMERS, AutoScanStep.OPEN_MOUNTS, AutoScanStep.OPEN_RESEARCH, AutoScanStep.CLOSE_RESEARCH))
            excluded += Rect(0, height / 5, width, height * 54 / 100)
        return excluded
    }

    private fun capture(action: (Bitmap) -> Unit) {
        val token = generation
        // During a run the panel is outside the saved crop; keep STOP visible.
        // Setup captures need a clean image for the editable full-screen preview.
        if (!running) removePanel()
        handler.postDelayed({
            if (token != generation || CaptureConnection.state != CaptureState.READY) return@postDelayed
            CaptureConnection.requestFreshFrame { bitmap, error ->
                if (token != generation) { bitmap?.let(::recycle); return@requestFreshFrame }
                if (bitmap == null) { if (running) stop(error ?: "Brak obrazu.") else fail(error ?: "Brak obrazu."); return@requestFreshFrame }
                val bounds = physicalBounds()
                if (bitmap.width != bounds.width() || bitmap.height != bounds.height()) {
                    recycle(bitmap); stop("Automat wymaga pełnoekranowej gry w pionie. Rozmiar udostępnionego obrazu nie pasuje do ekranu."); return@requestFreshFrame
                }
                try { action(bitmap) } catch (problem: Exception) { recycle(bitmap); if (running) stop(problem.message ?: "Nie udało się wykonać kroku.") else fail(problem.message ?: "Nie udało się ustawić kroku.") }

            }
        }, if (running) 60 else 130)
    }
    private fun ensurePanelAway(region: Rect) { protectedRegion = region; show() }
    private fun physicalBounds(): Rect = if (Build.VERSION.SDK_INT >= 30) Rect(manager.maximumWindowMetrics.bounds) else {
        val metrics = android.util.DisplayMetrics(); @Suppress("DEPRECATION") manager.defaultDisplay.getRealMetrics(metrics); Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
    }
    private fun showIconBar() {
        removePanel()
        val bar=ScannerIconBar.create(context,listOf(
            Triple(if(ready()) "Skanuj zasoby" else "Konfiguracja",if(ready()) "◉" else "⌖",{compactIcons=false;if(ready())start()else {val step=calibrationFlow.current;if(step!=null)configure(step)else {setupRequested=true;screenFlow.reset();pendingSetupDestination=null;calibrationFlow.beginAll();automaticSetupAttempted=null;show()}}}),
            Triple("Rozwiń skaner","⛶",{compactIcons=false;show()}),
            Triple("Wyniki","≡",{openApp(true)}),
            Triple("Zakończ odczyt","✕",{(context.applicationContext as ScannerApplication).closeScanner()})
        ))
        panelWidth=ScannerIconBar.width(context,4);panelHeight=ScannerIconBar.height(context,4)
        val lp=WindowManager.LayoutParams(panelWidth,panelHeight,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT).apply {gravity=Gravity.TOP or Gravity.START;x=dp(8);y=dp(40)}
        root=bar;runtimePanel=false;panelBounds=Rect(lp.x,lp.y,lp.x+panelWidth,lp.y+panelHeight)
        runCatching {manager.addView(bar,lp)}.onFailure {root=null}
    }
    private fun menu() {
        val actions = mutableListOf<Pair<String, () -> Unit>>()
        actions += "Zwiń do ikon" to {compactIcons=true;show()}
        if (ready()) actions += "Ponowny odczyt" to { start() }
        actions += "Ustaw od początku" to { setupRequested=true;screenFlow.reset();pendingSetupDestination=null;calibrationFlow.beginAll(); automaticSetupAttempted = null; status = "Zacznij od ekranu młotków."; show() }
        actions += "Popraw pola wybranego ekranu" to { chooseStep() }
        actions += "Manualny odczyt" to { hide(); val app = context.applicationContext as ScannerApplication; app.automaticMode = false; app.overlay.show() }
        actions += "Wróć do aplikacji" to { openApp(false) }
        actions += "Zamknij" to { (context.applicationContext as ScannerApplication).closeScanner() }
        showDialog(AlertDialog.Builder(context).setTitle("Automatyczny odczyt")
            .setItems(actions.map { it.first }.toTypedArray()) { _, which -> actions[which].second() }.create())
    }
    private fun chooseStep() = showDialog(AlertDialog.Builder(context).setTitle("Wybierz pole — otworzę ustawienia jego ekranu").setItems(AutoScanStep.entries.map { step -> step.title }.toTypedArray()) { _, which ->
        generation++; selecting = false; screenFlow.reset();pendingSetupDestination=null;calibrationFlow.beginRepair(AutoScanStep.entries[which]); automaticSetupAttempted = null; status = "Otwórz ekran właściwy dla tego pola."; show()
    }.create())
    private var previewReturn:View?=null
    private fun removePreviewReturn() {previewReturn?.let {runCatching {manager.removeViewImmediate(it)}};previewReturn=null}
    private fun showDialog(alert: AlertDialog,peek:Boolean=false) {
        removePreviewReturn();dialog?.dismiss();dialog=alert
        if(peek) {
            val title=LinearLayout(context).apply {orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(8),dp(16),0);setBackgroundColor(ScannerAppearance.BACKGROUND)}
            title.addView(TextView(context).apply {text="Sprawdź odczyt";textSize=20f;setTextColor(Color.WHITE)})
            title.addView(button("Podejrzyj grę") {
                alert.hide();root?.visibility=View.GONE;touchShield.hide()
                val restore=button("Wróć do wyboru") {
                    removePreviewReturn();root?.visibility=View.VISIBLE;alert.show()
                    alert.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled=(alert.listView?.checkedItemPosition?:-1)>=0 || alert.listView?.choiceMode!=android.widget.ListView.CHOICE_MODE_SINGLE
                }
                val params=WindowManager.LayoutParams(dp(180),-2,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT).apply {gravity=Gravity.TOP or Gravity.START;x=dp(8);y=dp(40)}
                previewReturn=restore;manager.addView(restore,params)
            })
            alert.setCustomTitle(title)
            alert.setOnDismissListener {removePreviewReturn();root?.visibility=View.VISIBLE}
        }
        alert.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);alert.show()
    }
    private fun openApp(review: Boolean) {
        // Start while our overlay is still visible; newer Android versions gate background launches.
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra(if (review) "review" else "home", true))
        (context.applicationContext as ScannerApplication).closeScanner()
    }
    private fun removePanel() { root?.let { runCatching { manager.removeView(it) } }; root = null; label = null; liveLabel = null; runtimePanel = false; finishListButton = null }
    private fun text(value: String, size: Float, bold: Boolean = false) = TextView(context).apply { text = value; textSize = size; setTextColor(if (bold) stepColor() else Color.WHITE); setPadding(0, dp(3), 0, dp(3)) }
    private fun button(value: String, action: () -> Unit) = ScannerAppearance.overlayAction(context, value) { action() }.apply { textSize = 12f; minHeight = dp(44); minimumHeight = dp(44) }
    private fun weight() = LinearLayout.LayoutParams(0, dp(48), 1f)
    private fun stepColor(): Int {
        val index = if (running) activeStep?.ordinal else setupIndex
        return ScannerAppearance.stepColor(index ?: 0)
    }
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
    private fun recycle(bitmap: Bitmap) { if (!bitmap.isRecycled) bitmap.recycle() }
    private fun failure(message: String): Result<AutoReading?> = Result.failure(IllegalStateException(message))
}
