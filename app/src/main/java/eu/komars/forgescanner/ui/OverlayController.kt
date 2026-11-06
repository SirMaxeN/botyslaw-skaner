package eu.komars.forgescanner.ui

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import eu.komars.forgescanner.MainActivity
import eu.komars.forgescanner.ScannerApplication
import eu.komars.forgescanner.capture.CaptureConnection
import eu.komars.forgescanner.capture.CaptureState
import eu.komars.forgescanner.model.CalibrationStore
import eu.komars.forgescanner.model.GameNumberParser
import eu.komars.forgescanner.model.GuidedScanFlow
import eu.komars.forgescanner.model.InventoryTracker
import eu.komars.forgescanner.model.SavedCalibration
import eu.komars.forgescanner.model.ScanSession
import eu.komars.forgescanner.model.ScanTarget
import eu.komars.forgescanner.model.ScanDiagnostics
import eu.komars.forgescanner.vision.OcrAnalysis
import eu.komars.forgescanner.vision.OcrAnalyzer
import eu.komars.forgescanner.vision.OcrCandidate
import eu.komars.forgescanner.vision.InventoryRecording
import eu.komars.forgescanner.vision.PairedCounterReader
import eu.komars.forgescanner.vision.InventoryGridCalibrator
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/** A small user-driven panel: current task, short feedback and at most two buttons. */
class OverlayController(
    private val context: Context,
    private val session: () -> ScanSession,
    private val flow: GuidedScanFlow,
    private val calibrations: CalibrationStore,
    private val inventoryFrameSource: (((Bitmap?, String?) -> Unit) -> Unit)? = null
) {
    private val manager = context.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val ocr by lazy { OcrAnalyzer() }
    private val number = NumberFormat.getIntegerInstance(Locale.forLanguageTag("pl-PL"))
    private var root: View? = null
    private var selector: ScreenSelectionWindow? = null
    private var selectionBitmap: Bitmap? = null
    private var dialog: AlertDialog? = null
    private var compact = false
    private var busy = false
    private var generation = 0
    private var lastMessage = ""
    private var x = 16
    private var y = 90
    private val inventories = mutableMapOf<ScanTarget, InventoryDraft>()
    private val pendingCalibration = mutableListOf<ScanTarget>()
    private val setupReadings = linkedMapOf<ScanTarget, GuidedScanFlow.CounterReading>()
    private val baseChoices = mutableMapOf<ScanTarget, Boolean>()
    private var recording: InventoryRecording? = null
    private var recordingTarget: ScanTarget? = null
    private var recordingHint = "Przewijaj od samej góry"
    private var recordedFrames = 0
    private var liveItems = 0L
    private var countView: TextView? = null
    private var recordingTitleView: TextView? = null
    private var recordingHintView: TextView? = null
    private var analyzing = false
    private var compactRecording = false
    private var windowInsetY = 0
    private var recordingScreenRegion: Rect? = null

    private class InventoryDraft(val columns: Int) {
        val tracker = InventoryTracker(columns)
        var manualOffset = 0L
        var approximate = false
        val count: Long get() = tracker.count + manualOffset
    }

    fun show() {
        if (!allowed()) return
        removePanel()
        val view = if (compact) bubble() else panel()
        val screen = context.resources.displayMetrics
        val params = WindowManager.LayoutParams(
            if (compact) ScannerIconBar.width(context, 3) else minOf(dp(264), screen.widthPixels - dp(16)),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = this@OverlayController.x.coerceIn(0, (screen.widthPixels - width).coerceAtLeast(0))
            y = this@OverlayController.y.coerceIn(0, (screen.heightPixels - dp(150)).coerceAtLeast(0))
        }
        root = view
        runCatching {
            manager.addView(view, params)
            view.post { val location = IntArray(2); view.getLocationOnScreen(location); windowInsetY = location[1] - params.y }
        }.onFailure { root = null }
    }

    fun minimize() { compact = true; show() }
    fun hide() {
        generation++
        pendingCalibration.clear(); setupReadings.clear()
        busy = false
        recording?.cancel(); recording = null; recordingTarget = null; analyzing = false
        removePanel()
        dialog?.dismiss(); dialog = null
        selector?.close(); selector = null
        selectionBitmap?.let(::recycle); selectionBitmap = null
    }
    fun dispose() { hide(); ocr.close() }
    fun resetSession() { hide(); inventories.clear(); baseChoices.clear(); lastMessage = ""; compact = false }
    private fun allowed() = Settings.canDrawOverlays(context) && CaptureConnection.state == CaptureState.READY
    private fun removePanel() { countView = null; recordingTitleView = null; recordingHintView = null; root?.let { runCatching { manager.removeView(it) } }; root = null }

    private fun bubble(): View = ScannerIconBar.create(context,listOf(
        Triple("Rozwiń skaner","⛶",{ compact=false;show() }),
        Triple("Wyniki","≡",{ openReview() }),
        Triple("Zakończ odczyt","✕",{ (context.applicationContext as ScannerApplication).closeScanner() })
    )).apply { draggable(this) { compact=false;show() } }

    private fun panel(): View {
        if (recordingTarget != null) return recordingPanel()
        val box = DraggableOverlayLayout(context, ::movePanel).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = background(ScannerAppearance.PANEL, 18)
        }
        val target = flow.currentTarget
        val title = when {
            flow.isComplete -> "✓ Skanowanie zakończone"
            target == null -> "⌖ Skaner"
            else -> "TERAZ ${target.position}/4: ${target.displayTitle}  ⌄"
        }
        box.addView(header(title))
        box.addView(stepIndicator())
        val draft = target?.let(inventories::get)
        val instruction = when {
            flow.isComplete -> "Sprawdź wyniki i skopiuj raport."
            target == null -> "Skanuj zasoby krok po kroku."
            draft != null -> "Sprawdź wynik analizy. Nagraj ponownie od góry, jeśli trzeba."
            target.isInventory -> "Otwórz ${target.title.lowercase()}. Zacznij od góry listy."
            else -> target.instruction
        }
        box.addView(label(instruction, 12f, true, ACCENT).apply { setPadding(0, dp(3), 0, dp(3)) })
        if (draft != null) box.addView(label("${if (draft.approximate) "≈ Estymacja" else "Odczyt"}: ${number.format(draft.count)} · sprawdź", 12f, true, ACCENT))
        else if (target != null && !configured(target)) box.addView(label("Najpierw wskaż obszar: Ustaw.", 10f, color = MUTED))
        if (lastMessage.isNotBlank()) box.addView(label(lastMessage, 10f, color = MUTED).apply { maxLines = 2 })
        val actions = LinearLayout(context).apply { setPadding(0, dp(4), 0, 0) }
        when {
            flow.isComplete -> {
                actions.addView(button("Reset", false) { (context.applicationContext as ScannerApplication).startNewSession(); show() }, smallButtonParams())
                actions.addView(button("Wyniki", true) { openReview() }, largeButtonParams())
            }
            target == null -> {
                actions.addView(button("×", false) { CaptureConnection.stop() }, smallButtonParams())
                actions.addView(button("Skanuj", true) { flow.start(); lastMessage = ""; show() }, largeButtonParams())
            }
            draft != null -> {
                actions.addView(button("Nagraj od nowa", false) { inventories.remove(target); lastMessage = "Wróć na górę listy."; show() }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginEnd = dp(5) })
                actions.addView(button("Potwierdź", true) { finishInventory(target) }, LinearLayout.LayoutParams(0, dp(40), 1f))
            }
            !configured(target) -> actions.addView(button("Ustaw", true) { configure(target) }, largeButtonParams())
            else -> {
                actions.addView(button("Ustaw", false) { configure(target) }, smallButtonParams())
                actions.addView(button("Skanuj", true) { scan(target) }, largeButtonParams())
            }
        }
        box.addView(actions)
        return box
    }

    /** No polling: only an explicit tap obtains a frame, with the panel hidden first. */
    private fun capture(action: (Bitmap) -> Unit) {
        if (busy || !allowed()) return
        busy = true
        val token = generation
        removePanel()
        handler.postDelayed({
            if (token != generation) return@postDelayed
            CaptureConnection.requestFrame { bitmap, error ->
                if (token != generation) { bitmap?.let(::recycle); return@requestFrame }
                busy = false
                if (bitmap == null) fail(error ?: "Brak obrazu. Otwórz grę i spróbuj ponownie.")
                else try { action(bitmap) } catch (_: Exception) {
                    recycle(bitmap); fail("Nie udało się odczytać. Ustaw obszar ponownie.")
                }
            }
        }, 250)
    }

    private fun configured(target: ScanTarget) = target.counters.all { calibrations.has(it) }

    private fun configure(target: ScanTarget) {
        pendingCalibration.clear(); setupReadings.clear()
        if (target.counters.size == 2) {
            pendingCalibration.addAll(target.counters)
            selectTarget(pendingCalibration.first())
            return
        }
        if (inventories[target] != null) {
            showDialog(AlertDialog.Builder(context).setTitle("Liczyć tę listę od nowa?")
                .setMessage("Wróć na górę listy. Usuniemy wstępny wynik tej listy.")
                .setPositiveButton("Ustaw od nowa") { _, _ -> inventories.remove(target); selectTarget(target) }
                .setNegativeButton("Wróć", null).create())
        } else selectTarget(target)
    }

    private fun selectTarget(target: ScanTarget) = capture { bitmap ->
        val token = generation
        val width = bitmap.width
        val height = bitmap.height
        selectionBitmap = bitmap
        selector = ScreenSelectionWindow(context, bitmap, target.isInventory, { rect, columns, rowHeight ->
            selector = null; selectionBitmap = null
            if (token != generation || flow.currentTarget != target.owner) { recycle(bitmap); return@ScreenSelectionWindow }
            try {
                if (target.isInventory) {
                    val fitted = InventoryGridCalibrator().refine(bitmap, rect, columns)
                    calibrations.save(target, fitted.region, width, height, fitted.columns, fitted.rowHeightPx)
                    recycle(bitmap)
                    // Wait for the selection window to leave the projection before recording.
                    scan(target)
                } else analyze(bitmap, target, rect) { result -> chooseCandidate(result, target, rect, width, height, setup = true) }
            } catch (_: Exception) { recycle(bitmap); fail("Nie udało się ustawić. Spróbuj ponownie.") }
        }, {
            selector = null; selectionBitmap = null; pendingCalibration.clear(); setupReadings.clear(); recycle(bitmap); show()
        }, titleText = if (target.counters.size == 2) "Ustaw ${target.counters.indexOf(target) + 1}/2: ${target.title}" else "Ustaw: ${target.title}")
        try { selector?.show() }
        catch (_: Exception) {
            selector?.close(); selector = null; selectionBitmap = null
            recycle(bitmap); fail("Sprawdź zgodę na nakładkę.")
        }
    }

    private fun scan(target: ScanTarget) = capture { bitmap ->
        val width = bitmap.width
        val height = bitmap.height
        if (target.counters.size == 2) { scanPair(bitmap, target); return@capture }
        val calibration = calibrations.get(target, width, height)
        if (calibration == null) {
            recycle(bitmap); calibrations.clear(target); inventories.remove(target)
            fail("Rozmiar ekranu się zmienił. Wybierz Ustaw.")
        } else if (target.isInventory) {
            val fitted = InventoryGridCalibrator().refine(bitmap, calibration.region, calibration.columns)
            calibrations.save(target, fitted.region, width, height, fitted.columns, fitted.rowHeightPx)
            beginRecording(bitmap, target, fitted)
        } else analyze(bitmap, target, calibration.region) { result ->
            chooseCandidate(result, target, calibration.region, width, height, setup = false)
        }
    }

    private fun analyze(bitmap: Bitmap, target: ScanTarget, region: Rect, callback: (OcrAnalysis) -> Unit) {
        val token = generation
        busy = true
        eu.komars.forgescanner.vision.CounterBurstReader(ocr).read(bitmap, target, region,
            isActive = { token == generation && flow.currentTarget == target.owner }) { result ->
            if (token == generation && flow.currentTarget == target.owner) { busy = false; callback(result) }
        }
    }

    private fun chooseCandidate(result: OcrAnalysis, target: ScanTarget, region: Rect, width: Int, height: Int, setup: Boolean) {
        val candidates = eu.komars.forgescanner.vision.OcrChoices.unique(result.candidates, target.field.isCost)
        show()
        if (candidates.isEmpty()) { fail("Brak czytelnej liczby. Wybierz Ustaw."); return }
        if (!setup && candidates.size == 1 && !result.requiresConfirmation) { acceptNumber(target, candidates.single()); return }
        val title = when {
            setup -> "${target.title}: wybierz poprawną wartość"
            result.requiresConfirmation -> "${target.title}: sprawdź niepewny odczyt"
            else -> "${target.title}: który odczyt?"
        }
        showDialog(AlertDialog.Builder(context).setTitle(title)
            .setItems(candidates.map { "${it.sourceText}  =  ${number.format(it.value)}${if (it.approximate) " ≈" else ""}" }.toTypedArray()) { _, index ->
                val selected = candidates[index]
                if (setup) {
                    val saved = selected.bounds.run {
                        Rect(left, top, right, bottom).apply {
                            inset(-max(12, width() / 4), -max(10, height() / 3)); intersect(region)
                        }
                    }
                    val readable = if (saved.width() < 32 || saved.height() < 32) region else saved
                    calibrations.save(target, readable, width, height, 1, readable.height().coerceAtLeast(2))
                    setupReadings[target] = GuidedScanFlow.CounterReading(selected.value, selected.approximate, selected.sourceText)
                    pendingCalibration.remove(target)
                    if (pendingCalibration.isNotEmpty()) selectTarget(pendingCalibration.first())
                    else if (target.counters.size == 2) {
                        flow.acceptPair(target.owner, setupReadings.toMap())
                        setupReadings.clear()
                        advance()
                    } else {
                        setupReadings.clear()
                        acceptNumber(target, selected)
                    }
                } else acceptNumber(target, selected)
            }.setNegativeButton("Wróć", null).create())
    }

    private fun scanPair(bitmap: Bitmap, target: ScanTarget) {
        val regions = target.counters.associateWith { calibrations.get(it, bitmap.width, bitmap.height)?.region }
        if (regions.values.any { it == null }) {
            recycle(bitmap)
            fail("Ustaw oba obszary: zasób i koszt.")
            return
        }
        val token = generation
        busy = true
        PairedCounterReader(ocr).read(bitmap, target, regions.mapValues { requireNotNull(it.value) }, isActive = { token == generation && flow.currentTarget == target }) { result ->
            if (token != generation || flow.currentTarget != target) return@read
            busy = false
            result.fold({ candidates ->
                val selected = linkedMapOf<ScanTarget, OcrCandidate>()
                fun choose(index: Int) {
                    val counter = target.counters[index]
                    val choices = candidates.getValue(counter)
                    fun accept(candidate: OcrCandidate) {
                        selected[counter] = candidate
                        if (index + 1 < target.counters.size) choose(index + 1)
                        else {
                            flow.acceptPair(target, selected.mapValues { (_, value) ->
                                GuidedScanFlow.CounterReading(value.value, value.approximate, value.sourceText)
                            })
                            advance()
                        }
                    }
                    if (choices.size == 1 && !choices.single().approximate) accept(choices.single())
                    else {
                        show()
                        showDialog(AlertDialog.Builder(context).setTitle("${counter.title}: wybierz wartość")
                            .setItems(choices.map { "${it.sourceText} = ${number.format(it.value)}" }.toTypedArray()) { _, index -> accept(choices[index]) }
                            .setNegativeButton("Wróć", null).create())
                    }
                }
                choose(0)
            }, { fail(it.message ?: "Nie udało się odczytać obu wartości.") })
        }
    }

    private fun acceptNumber(target: ScanTarget, candidate: OcrCandidate) {
        flow.acceptNumber(target, candidate.value, candidate.approximate, candidate.sourceText)
        advance()
    }

    private fun recordingPanel(): View = DraggableOverlayLayout(context, ::movePanel).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(12), dp(14), dp(12))
        background = background(ScannerAppearance.PANEL, 18)
        addView(header(if (analyzing) "Analiza listy" else "Skanowanie"))
        if (!compactRecording) addView(stepIndicator())
        recordingTitleView = label(recordingTarget?.title ?: "Lista", 13f, true)
        addView(recordingTitleView)
        countView = label("≈ ${number.format(liveItems)} pozycji", 25f, true, ACCENT)
        addView(countView)
        if (!compactRecording) addView(label("Wstępny wynik · dokładna analiza po zakończeniu", 10f, color = MUTED))
        if (analyzing) addView(label("Poczekaj na wynik.", 10f))
        else {
            recordingHintView = label(recordingHint, 11f, true).apply { minLines = if (compactRecording) 1 else 2; maxLines = if (compactRecording) 1 else 2 }
            addView(recordingHintView)
        }
        if (!analyzing) addView(LinearLayout(context).apply {
            addView(button("Anuluj", false) {
                recording?.cancel(); recording = null; recordingTarget = null; lastMessage = "Nagrywanie anulowane."; show()
            }, smallButtonParams())
            addView(button("Zakończ", true) { recording?.stop() }, largeButtonParams())
        })
    }

    /** Keep the same WindowManager surface and buttons while recording, including during touches. */
    private fun updateRecordingText() {
        countView?.text = "≈ ${number.format(liveItems)} pozycji"
        recordingHintView?.text = recordingHint
    }

    private fun beginRecording(bitmap: Bitmap, target: ScanTarget, calibration: SavedCalibration) {
        val screen = if (android.os.Build.VERSION.SDK_INT >= 30) manager.maximumWindowMetrics.bounds
            else Rect(0, 0, context.resources.displayMetrics.widthPixels, context.resources.displayMetrics.heightPixels)
        val top = calibration.region.top.toDouble() * screen.height() / bitmap.height
        val bottom = calibration.region.bottom.toDouble() * screen.height() / bitmap.height
        recordingScreenRegion = Rect(0, top.toInt(), screen.width(), bottom.toInt())
        val token = generation
        recordingTarget = target; recordedFrames = 0; liveItems = 0; analyzing = false
        recordingHint = "Poczekaj — sprawdzam listę"
        fun measuredPosition(compact: Boolean): Int? {
            compactRecording = compact
            val preview = recordingPanel()
            preview.measure(View.MeasureSpec.makeMeasureSpec(minOf(dp(264), screen.width()-dp(16)), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            return eu.komars.forgescanner.model.OverlayPlacement.find(screen.height(), top.toInt(), bottom.toInt(),
                preview.measuredHeight, windowInsetY.coerceAtLeast(0), dp(24), dp(6))
        }
        val position = measuredPosition(false) ?: measuredPosition(true)
        if (position == null) {
            recordingTarget = null; recycle(bitmap)
            fail("Brak miejsca na przyciski obok listy. Zaznacz sam obszar listy, bez dolnego menu gry.")
            return
        }
        x = dp(8); y = position
        val width = bitmap.width; val height = bitmap.height
        recording = InventoryRecording(calibration, width, height, { count ->
            if (token == generation) { recordedFrames = count; updateRecordingText() }
        }, {
            if (token == generation) { analyzing = true; busy = true; show() }
        }, { result ->
            if (token == generation) {
                busy = false; analyzing = false; recordingTarget = null; recording = null
                result.fold({ analysis ->
                    inventories[target] = InventoryDraft(calibration.columns).apply { manualOffset = analysis.count; approximate = analysis.approximate }
                    lastMessage = analysis.message
                    show(); finishInventory(target)
                }, { fail(it.message ?: "Analiza nie powiodła się. Nagraj listę ponownie.") })
            }
        }, guidance = { hint ->
            if (token == generation && recordingTarget != null && recordingHint != hint) {
                recordingHint = hint; updateRecordingText()
            }
        }, frameSource = inventoryFrameSource, diagnostic = { result -> ScanDiagnostics(context).save(target, calibration, result) }, countProgress = { count ->
            if (token == generation && recordingTarget != null) { liveItems = count; updateRecordingText() }
        })
        try { recording!!.start(bitmap) } finally { recycle(bitmap) }
        lastMessage = ""; show()
    }

    private fun finishInventory(target: ScanTarget) {
        val draft = inventories[target]
        val content = vertical()
        content.addView(label("$lastMessage\nSprawdź wynik całej listy i ostatni niepełny rząd.", 13f))
        val initialCount = draft?.count ?: flow.inventoryRawTotals()[target] ?: 0L
        val value = input("Łącznie na liście").apply { setText(initialCount.toString()) }
        content.addView(value)
        val subtract = CheckBox(context).apply {
            text = if (target.baseCount == 1) "Odjąć 1 bazowego wierzchowca" else "Odjąć 3 bazowe zwierzaki"
            isChecked = baseChoices[target] ?: (initialCount >= target.baseCount)
        }
        if (target.baseCount > 0) content.addView(subtract)
        val alert = AlertDialog.Builder(context).setTitle(if (draft?.approximate == true) "≈ ${target.title}: estymacja" else "${target.title}: koniec listy")
            .setView(content).setPositiveButton("Potwierdź", null).setNegativeButton("Wróć", null).create()
        if (!showDialog(alert)) return
        alert.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val raw = GameNumberParser.parse(value.text.toString())?.wholeValueOrNull()
            if (raw == null) { value.error = "Podaj pełną, nieujemną liczbę"; return@setOnClickListener }
            try {
                flow.completeInventory(target, raw, target.baseCount > 0 && subtract.isChecked, approximate = draft?.approximate == true && raw == initialCount)
                baseChoices[target] = subtract.isChecked
                draft?.let { it.manualOffset = raw - it.tracker.count }
                alert.dismiss(); advance()
            } catch (error: IllegalArgumentException) { value.error = error.message }
        }
    }

    private fun advance() {
        flow.next(); lastMessage = ""
        if (flow.isComplete) openReview() else {
            show()
            root?.announceForAccessibility("Teraz: ${flow.currentTarget?.displayTitle}. ${flow.currentTarget?.instruction}")
            root?.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        }
    }

    /** Secondary operations live behind the draggable title, rather than extra panel buttons. */
    private fun showOptions() {
        val target = flow.currentTarget
        val choices = mutableListOf<Pair<String, () -> Unit>>()
        if (target != null) {
            choices += "Wpisz wartość ręcznie" to { manualValue(target) }
            if (target.isInventory && inventories[target] != null) choices += "Dodaj nowe rzędy ręcznie" to { addRows(target) }
            choices += "Ustaw obszar ponownie" to { configure(target) }
            if (target.position > 1) choices += "Poprzedni krok" to { flow.back(); lastMessage = ""; show() }
        }
        choices += "Wyniki" to { openReview() }
        choices += "Zwiń" to { minimize() }
        choices += "Nowe skanowanie" to { (context.applicationContext as ScannerApplication).startNewSession(); show() }
        choices += "Wróć do aplikacji skanera" to { returnToApplication() }
        choices += "Zamknij skaner" to { closeScanner() }
        showDialog(AlertDialog.Builder(context).setTitle(target?.title ?: "Skaner")
            .setItems(choices.map { it.first }.toTypedArray()) { _, index -> choices[index].second() }
            .setNegativeButton("Wróć", null).create())
    }

    private fun manualValue(target: ScanTarget) {
        if (target.isInventory) { finishInventory(target); return }
        if (target.counters.size == 2) { manualPair(target); return }
        val edit = input(target.title)
        session().snapshot().value(target.field)?.let { edit.setText(it.toString()) }
        val alert = AlertDialog.Builder(context).setTitle(target.title).setView(edit)
            .setPositiveButton("Zapisz", null).setNegativeButton("Wróć", null).create()
        if (!showDialog(alert)) return
        alert.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val parsed = GameNumberParser.parse(edit.text.toString())
            val value = parsed?.wholeValueOrNull()
            if (value == null || (target.field.isCost && value == 0L)) { edit.error = "Wpisz poprawną liczbę"; return@setOnClickListener }
            flow.acceptNumber(target, value, parsed.abbreviated)
            alert.dismiss(); advance()
        }
    }

    private fun manualPair(target: ScanTarget) {
        val content = vertical()
        val edits = target.counters.associateWith { counter ->
            content.addView(label(counter.title, 12f))
            input(counter.title).also { edit ->
                session().snapshot().value(counter.field)?.let { edit.setText(it.toString()) }
                content.addView(edit)
            }
        }
        val alert = AlertDialog.Builder(context).setTitle(target.displayTitle).setView(content)
            .setPositiveButton("Zapisz", null).setNegativeButton("Wróć", null).create()
        if (!showDialog(alert)) return
        alert.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val readings = linkedMapOf<ScanTarget, GuidedScanFlow.CounterReading>()
            for ((counter, edit) in edits) {
                val parsed = GameNumberParser.parse(edit.text.toString())
                val value = parsed?.wholeValueOrNull()
                if (value == null || (counter.field.isCost && value == 0L)) {
                    edit.error = "Wpisz poprawną liczbę"; return@setOnClickListener
                }
                readings[counter] = GuidedScanFlow.CounterReading(value, parsed.abbreviated)
            }
            flow.acceptPair(target, readings)
            alert.dismiss(); advance()
        }
    }

    private fun addRows(target: ScanTarget) {
        val draft = inventories[target] ?: return
        val content = vertical()
        content.addView(label("Tylko nowe, jeszcze niedodane pozycje. Bez wspólnego rzędu.", 13f))
        val rows = input("Nowe pełne rzędy")
        val rest = input("Dodatkowe pozycje")
        content.addView(rows); content.addView(rest)
        val alert = AlertDialog.Builder(context).setTitle("Dodaj ręcznie").setView(content)
            .setPositiveButton("Dodaj", null).setNegativeButton("Wróć", null).create()
        if (!showDialog(alert)) return
        alert.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val a = if (rows.text.isBlank()) 0L else GameNumberParser.parse(rows.text.toString())?.wholeValueOrNull()
            val b = if (rest.text.isBlank()) 0L else GameNumberParser.parse(rest.text.toString())?.wholeValueOrNull()
            if (a == null || b == null || a > 1_000_000 || b > 1_000_000) { rows.error = "Wpisz pełną liczbę do 1 000 000"; return@setOnClickListener }
            val count = a * draft.columns + b
            draft.tracker.addManually(count.toInt())
            lastMessage = "Dodano ręcznie $count."
            alert.dismiss(); show()
        }
    }

    private fun closeScanner() {
        (context.applicationContext as ScannerApplication).closeScanner()
    }

    private fun returnToApplication() {
        context.startActivity(Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("home", true)
        })
        closeScanner()
    }

    private fun openReview() {
        context.startActivity(Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("review", true)
        })
        closeScanner()
    }
    private fun stepColor(): Int = when (flow.currentTarget?.position) {
        1 -> 0xFFFFCB69.toInt()
        2 -> 0xFF83B8FF.toInt()
        3 -> 0xFFCAAAFF.toInt()
        else -> ACCENT
    }

    private fun stepIndicator(): View = LinearLayout(context).apply {
        setPadding(0, dp(8), 0, dp(10))
        val active = flow.currentTarget?.position ?: if (flow.isComplete) 4 else 0
        for (step in 1..4) addView(label("$step", 10f, true,
            if (step == active) ScannerAppearance.ON_ACCENT else MUTED).apply {
                gravity = Gravity.CENTER
                background = background(if (step == active) stepColor() else ScannerAppearance.CONTROL, 5)
            }, LinearLayout.LayoutParams(0, dp(18), 1f).apply { if (step < 4) marginEnd = dp(5) })
    }

    private fun header(title: String): View = LinearLayout(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        addView(label(title, 12f, true, stepColor()).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(Button(context).apply {
            text = "☰"; textSize = 16f; contentDescription = "Menu skanera"; setPadding(0,0,0,0)
            background = background(ScannerAppearance.CONTROL, 10); setTextColor(ScannerAppearance.FOREGROUND)
            setOnClickListener { showQuickMenu() }
        }, LinearLayout.LayoutParams(dp(34), dp(34)).apply { marginEnd = dp(5) })
        addView(Button(context).apply {
            text = "×"; textSize = 23f; contentDescription = "Zamknij skaner"; setPadding(0,0,0,0)
            background = background(ScannerAppearance.DANGER_SURFACE, 10); setTextColor(ScannerAppearance.DANGER_TEXT)
            setOnClickListener { closeScanner() }
        }, LinearLayout.LayoutParams(dp(34), dp(34)))
    }

    private fun showQuickMenu() {
        if (recordingTarget == null && !busy) { showOptions(); return }
        showDialog(AlertDialog.Builder(context).setTitle("Skanowanie")
            .setItems(arrayOf("Wróć do skanowania", "Wróć do aplikacji skanera", "Zamknij skaner")) { _, index ->
                if (index == 1) returnToApplication()
                if (index == 2) closeScanner()
            }.create())
    }

    private fun fail(message: String) { busy = false; lastMessage = message; show() }
    private fun recycle(bitmap: Bitmap) { if (!bitmap.isRecycled) bitmap.recycle() }
    private fun showDialog(alert: AlertDialog): Boolean {
        dialog?.dismiss()
        if (!allowed()) return false
        dialog = alert
        alert.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        alert.setOnDismissListener { if (dialog === alert) dialog = null }
        return runCatching { alert.show(); true }.getOrElse { dialog = null; false }
    }
    private fun vertical() = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(4), dp(18), dp(4))
    }
    private fun input(hintValue: String) = EditText(context).apply {
        hint = hintValue; setSingleLine(true); textSize = 16f
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        filters = arrayOf(android.text.InputFilter.LengthFilter(32))
        setSelectAllOnFocus(true)
    }
    private fun label(value: String, size: Float, bold: Boolean = false, color: Int = Color.WHITE) = TextView(context).apply {
        text = value; textSize = size; setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }
    private fun button(value: String, primary: Boolean, action: () -> Unit) = Button(context).apply {
        text = value; isAllCaps = false; textSize = 11f
        minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
        setPadding(dp(4), 0, dp(4), 0)
        setTextColor(if (primary) ScannerAppearance.ON_ACCENT else ScannerAppearance.FOREGROUND)
        background = background(if (primary) ACCENT else ScannerAppearance.CONTROL, 10)
        contentDescription = value
        setOnClickListener { if (!busy) action() }
    }
    private fun smallButtonParams() = LinearLayout.LayoutParams(dp(60), dp(40)).apply { marginEnd = dp(6) }
    private fun largeButtonParams() = LinearLayout.LayoutParams(0, dp(40), 1f)
    private fun movePanel(dx: Float, dy: Float) {
        val panel = root ?: return
        val params = panel.layoutParams as WindowManager.LayoutParams
        val metrics = context.resources.displayMetrics
        params.x = (params.x + dx).toInt().coerceIn(0, (metrics.widthPixels - panel.width).coerceAtLeast(0))
        var nextY = (params.y + dy).toInt().coerceIn(0, (metrics.heightPixels - panel.height).coerceAtLeast(0))
        if (recordingTarget != null) recordingScreenRegion?.let { region ->
            val above = region.top - panel.height - windowInsetY - dp(6)
            val below = region.bottom - windowInsetY + dp(6)
            if (nextY > above && nextY < below) {
                nextY = listOf(above, below).filter { it >= 0 && it + panel.height <= metrics.heightPixels }
                    .minByOrNull { abs(it - nextY) } ?: params.y
            }
        }
        params.y = nextY; x = params.x; y = params.y
        runCatching { manager.updateViewLayout(panel, params) }
    }

    private fun draggable(view: View, action: () -> Unit) {
        var startX = 0f; var startY = 0f; var originalX = 0; var originalY = 0
        view.setOnClickListener { action() }
        view.setOnTouchListener { _, event ->
            val panel = root ?: return@setOnTouchListener false
            val params = panel.layoutParams as WindowManager.LayoutParams
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { startX = event.rawX; startY = event.rawY; originalX = params.x; originalY = params.y }
                MotionEvent.ACTION_MOVE -> {
                    params.x = (originalX + event.rawX - startX).toInt().coerceIn(0, (context.resources.displayMetrics.widthPixels - panel.width).coerceAtLeast(0))
                    params.y = (originalY + event.rawY - startY).toInt().coerceIn(0, (context.resources.displayMetrics.heightPixels - panel.height).coerceAtLeast(0))
                    x = params.x; y = params.y
                    runCatching { manager.updateViewLayout(panel, params) }
                }
                MotionEvent.ACTION_UP -> if (abs(event.rawX - startX) < dp(8) && abs(event.rawY - startY) < dp(8)) view.performClick()
                else -> return@setOnTouchListener false
            }
            true
        }
    }
    private fun background(color: Int, radius: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
    companion object {
        private val ACCENT = ScannerAppearance.ACCENT
        private val MUTED = ScannerAppearance.MUTED
    }
}
