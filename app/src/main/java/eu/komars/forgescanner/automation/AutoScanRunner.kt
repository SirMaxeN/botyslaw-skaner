package eu.komars.forgescanner.automation

import eu.komars.forgescanner.model.ScanSession
import eu.komars.forgescanner.model.ScanTarget

/** Deterministic sequencer; all platform work is injected and tests need no Android runtime. */
class AutoScanRunner(
    private val port: Port,
    private val scheduler: Scheduler,
    private val onState: (State) -> Unit,
    private val onComplete: (Map<ScanTarget, AutoReading>) -> Unit
) {
    interface Port {
        /** Navigation uses setup-confirmed coordinates. Reads must fail on unresolved ambiguity. */
        fun perform(step: AutoScanStep, complete: (Result<AutoReading?>) -> Unit)
        fun cancel()
    }
    fun interface Scheduler {
        /** Return an idempotent cancellation function. Callbacks must run on the caller's event loop. */
        fun after(delayMillis: Long, action: () -> Unit): () -> Unit
    }
    enum class Status { IDLE, RUNNING, FAILED, CANCELLED, COMPLETE }
    data class State(val status: Status, val step: AutoScanStep? = null, val message: String = "")

    private var generation = 0L
    private var active = false
    private var index = 0
    private var cancelTimer: (() -> Unit)? = null
    private var cancelDeadline: (() -> Unit)? = null
    private val pending = linkedMapOf<ScanTarget, AutoReading>()
    var state: State = State(Status.IDLE)
        private set

    fun start() {
        cancelWork(cancelPort = active)
        val token = ++generation
        active = true; index = 0; pending.clear()
        performNext(token)
    }

    fun cancel() {
        val running = active
        generation++; cancelWork(); pending.clear()
        if (running) publish(State(Status.CANCELLED, message = "Automatyczny skan zatrzymany. Nie zapisano częściowych wyników."))
    }

    private fun performNext(token: Long) {
        if (!active || generation != token) return
        val step = AutoScanStep.entries.getOrNull(index)
        if (step == null) {
            if (pending.keys != ScanTarget.entries.toSet()) { fail("Brakuje części wyników. Powtórz skanowanie."); return }
            val complete = pending.toMap()
            active = false; cancelTimer?.invoke(); cancelTimer = null
            cancelDeadline?.invoke(); cancelDeadline = null
            port.cancel()
            publish(State(Status.COMPLETE, message = "Skan zakończony. Sprawdź wyniki przed wysłaniem."))
            if (generation == token) onComplete(complete)
            return
        }
        cancelDeadline?.invoke()
        cancelDeadline = scheduler.after(300_000L) {
            if(active&&generation==token)fail("Przez 5 minut nie ukończono kroku. Zatrzymano skan bez wyłączania dostępności.")
        }
        publish(State(Status.RUNNING, step, "${index + 1}/${AutoScanStep.entries.size}: ${step.title}"))
        if (!active || generation != token) return
        var accepted = false
        cancelTimer = scheduler.after(step.timeoutMillis) {
            if (!accepted && active && generation == token) {
                accepted = true; fail("Nie udało się ukończyć kroku: ${step.title}. Ustaw ten krok ponownie.")
            }
        }
        try {
            port.perform(step) { result ->
                if (accepted || !active || generation != token) return@perform
                accepted = true; cancelTimer?.invoke(); cancelTimer = null
                val problem = result.exceptionOrNull()
                if (problem != null) { fail(problem.message ?: "Odczyt przerwany. Sprawdź bieżący krok."); return@perform }
                val reading = result.getOrNull()
                val target = step.target
                if (target == null && reading != null || target != null && reading == null) {
                    fail("Wynik nie pasuje do kroku: ${step.title}."); return@perform
                }
                if (target != null && reading != null) {
                    if (reading.value !in 0..ScanSession.MAX_VALUE || target.field.isCost && reading.value <= 0) {
                        fail("Niepewny wynik: ${step.title}. Sprawdź lub ustaw obszar ponownie."); return@perform
                    }
                    pending[target] = reading
                }
                index++; performNext(token)
            }
        } catch (problem: Exception) {
            if (active && generation == token) fail(problem.message ?: "Nie udało się wykonać kroku: ${step.title}.")
        }
    }

    private fun fail(message: String) {
        val failedStep = AutoScanStep.entries.getOrNull(index)
        generation++; cancelWork(); pending.clear()
        publish(State(Status.FAILED, failedStep, message))
    }
    private fun cancelWork(cancelPort: Boolean = true) {
        active = false; cancelTimer?.invoke(); cancelTimer = null
        cancelDeadline?.invoke(); cancelDeadline = null
        if (cancelPort) port.cancel()
    }
    private fun publish(next: State) { state = next; onState(next) }
}
