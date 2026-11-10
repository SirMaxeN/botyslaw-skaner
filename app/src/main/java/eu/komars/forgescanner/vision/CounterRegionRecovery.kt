package eu.komars.forgescanner.vision

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import eu.komars.forgescanner.automation.AutoScanStep
import eu.komars.forgescanner.capture.CaptureConnection
import eu.komars.forgescanner.model.ScanSession
import eu.komars.forgescanner.model.ScanTarget
import java.util.concurrent.Executors

data class RecoveryResult(val analysis: OcrAnalysis, val region: Rect, val repaired: Boolean)

/**
 * Bounded automatic recovery before asking the user to redraw a numeric field.
 * Every candidate gets its own three-source-frame consensus. Contrast passes and
 * repeated candidates are never treated as additional votes. No setting is saved here.
 */
class CounterRegionRecovery(private val ocr: OcrAnalyzer) {
    private val main = Handler(Looper.getMainLooper())

    /** Owns first and every subsequently requested bitmap; callbacks run on the main thread. */
    fun read(first: Bitmap, target: ScanTarget, original: Rect, isActive: () -> Boolean,
             onAttempt: (String) -> Unit, onRegion: (Rect) -> Unit = {}, complete: (RecoveryResult) -> Unit) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post { read(first, target, Rect(original), isActive, onAttempt, onRegion, complete) }; return
        }
        if (!isActive()) { release(first); return }
        val initialRegion = Rect(original)
        val width = first.width; val height = first.height
        val step = step(target)
        if (step == null || target.isInventory) {
            release(first)
            complete(RecoveryResult(error("Lista wymaga skanowania ikon, a nie naprawy pola z liczbą."), initialRegion, false))
            return
        }
        // Detection runs on a retained copy. CounterBurstReader is free to recycle the
        // original after copying its crop; neither reader accesses a recycled bitmap.
        val retained = runCatching { first.copy(Bitmap.Config.ARGB_8888, false) }.getOrNull()
        WORKER.execute {
            val detection = try {
                if (retained != null && isActive()) runCatching { GameUiDetector().detect(retained, step) }.getOrNull() else null
            } finally { retained?.let(::release) }
            main.post {
                if (!isActive()) { release(first); return@post }
                val plan = CounterRecoveryPlan.create(initialRegion.bounds(), detection?.region?.bounds(),
                    detection?.container?.bounds(), width, height, detection?.confidence ?: 0f)
                if (plan.candidates.isEmpty()) {
                    release(first)
                    complete(RecoveryResult(error("Nie rozpoznaję pola na tym ekranie. Zaznacz je ponownie."), initialRegion, false))
                    return@post
                }
                val attempts = mutableListOf<OcrAnalysis>()
                val agreement = CounterRecoveryAgreement()
                var finished = false

                fun unresolved(): OcrAnalysis {
                    val candidates = OcrChoices.unique(attempts.flatMap { it.candidates }.filter { it.field == target.field }, target.field.isCost)
                    val message = when {
                        agreement.conflicting -> "Różne obszary dały różne zgodne wartości. Automat nie wybiera liczby; sprawdź pole ręcznie."
                        candidates.isEmpty() -> "Nie odczytano poprawnie pola po automatycznych próbach. Zaznacz same cyfry, bez ikony."
                        else -> "Nie udało się potwierdzić całej liczby po automatycznych próbach. Sprawdź obszar pola."
                    }
                    return OcrAnalysis(candidates, attempts.flatMap { it.lines }, error = message, requiresConfirmation = true)
                }
                fun finish(analysis: OcrAnalysis, candidate: CounterRecoveryCandidate? = null) {
                    if (finished) return
                    finished = true
                    if (!isActive()) return
                    val destination = candidate?.persistence?.rect() ?: initialRegion
                    val changed = candidate != null && (candidate.crop != initialRegion.bounds() || candidate.persistence != initialRegion.bounds())
                    complete(RecoveryResult(analysis, Rect(destination), changed))
                }
                fun attempt(index: Int, initial: Bitmap? = null) {
                    if (finished || !isActive()) { initial?.let(::release); return }
                    val candidate = plan.candidates.getOrNull(index)
                    if (candidate == null) { initial?.let(::release); finish(unresolved()); return }
                    val label = if (index == 0 && !candidate.anchored) "Sprawdzam zapisane pole"
                        else "Automatycznie dopasowuję pole"
                    onAttempt("${target.title}: $label (${index + 1}/${plan.candidates.size})…")
                    onRegion(candidate.persistence.rect())
                    if (!isActive()) { initial?.let(::release); return }
                    fun readCandidate(bitmap: Bitmap) {
                        if (finished || !isActive()) { release(bitmap); return }
                        if (bitmap.width != width || bitmap.height != height) {
                            release(bitmap); finish(error("Rozmiar ekranu się zmienił. Ustaw pole ponownie.")); return
                        }
                        CounterBurstReader(ocr).read(bitmap, target, candidate.crop.rect(), { !finished && isActive() }) { analysis ->
                            if (finished || !isActive()) return@read
                            attempts += analysis
                            val choices = OcrChoices.unique(analysis.candidates.filter { it.field == target.field }, target.field.isCost)
                            val stable = analysis.error == null && !analysis.requiresConfirmation && choices.size == 1 &&
                                choices.single().value in (if (target.field.isCost) 1 else 0)..ScanSession.MAX_VALUE
                            if (stable) {
                                // A tight/out-of-container old crop may consistently read only a
                                // suffix of the number. Require the independently anchored crop too.
                                val needsAnchor = !candidate.anchored && plan.verifyOriginal
                                val compatible = agreement.observeStable(choices.single().value, trusted = !needsAnchor)
                                if (compatible && !needsAnchor) { finish(analysis, candidate); return@read }
                            }
                            attempt(index + 1)
                        }
                    }
                    if (initial != null) readCandidate(initial)
                    else main.postDelayed({
                        if (!finished && isActive()) CaptureConnection.requestFreshFrame { bitmap, issue ->
                            if (finished || !isActive()) { bitmap?.let(::release); return@requestFreshFrame }
                            if (bitmap != null) readCandidate(bitmap)
                            else {
                                attempts += error(issue ?: "Nie udało się pobrać nowej klatki.")
                                attempt(index + 1)
                            }
                        }
                    }, 130)
                }
                attempt(0, first)
            }
        }
    }

    private fun step(target: ScanTarget): AutoScanStep? = when (target) {
        ScanTarget.HAMMERS -> AutoScanStep.HAMMERS
        ScanTarget.MOUNT_KEYS -> AutoScanStep.MOUNT_KEYS
        ScanTarget.MOUNT_COST -> AutoScanStep.MOUNT_COST
        ScanTarget.TICKETS -> AutoScanStep.SKILL_TICKETS
        ScanTarget.SKILL_COST -> AutoScanStep.SKILL_COST
        ScanTarget.COINS -> AutoScanStep.COINS
        ScanTarget.GEMS -> AutoScanStep.GEMS
        ScanTarget.SHELLS -> AutoScanStep.SHELLS
        ScanTarget.RED_VIALS -> AutoScanStep.RED_VIALS
        ScanTarget.GREEN_VIALS -> AutoScanStep.GREEN_VIALS
        else -> null
    }
    private fun Rect.bounds() = CounterBounds(left, top, right, bottom)
    private fun CounterBounds.rect() = Rect(left, top, right, bottom)
    private fun error(message: String) = OcrAnalysis(emptyList(), emptyList(), error = message, requiresConfirmation = true)

    companion object {
        private val WORKER = Executors.newSingleThreadExecutor { task -> Thread(task, "CounterRegionRecovery").apply { isDaemon = true } }
        private fun release(bitmap: Bitmap) { if (!bitmap.isRecycled) bitmap.recycle() }
    }
}
