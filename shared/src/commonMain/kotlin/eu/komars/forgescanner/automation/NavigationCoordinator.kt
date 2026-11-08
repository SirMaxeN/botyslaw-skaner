package eu.komars.forgescanner.automation

import eu.komars.forgescanner.model.GameScreenKind

/** Source-aware navigation; only recognized controls can authorize a tap or prove its result.
 * All callbacks run on the caller's event loop. Each original navigation has one deadline
 * and at most one restoration route; recovery steps cannot recursively recover themselves.
 */
class NavigationCoordinator(
    private val port: Port, private val scheduler: Scheduler, private val nowMillis: () -> Long,
    private val onStatus: (AutoScanStep, Boolean) -> Unit = { _, _ -> }
) {
    interface Port {
        /** Deliver the current rendered screen; never require a static scene to render another buffer. */
        fun inspect(complete: (Result<GameScreenKind>) -> Unit)
        fun tap(step: AutoScanStep, recovering: Boolean, complete: (Result<Unit>) -> Unit)
        /** Cancel pending capture work only; the surrounding scan owns the armed gesture session. */
        fun cancel()
    }
    fun interface Scheduler { fun after(delayMillis: Long, action: () -> Unit): () -> Unit }
    private var active = false
    private var generation = 0L
    private var phase = 0L
    private var recoveryUsed = false
    private var original = AutoScanStep.OPEN_MOUNTS
    private var completion: ((Result<Unit>) -> Unit)? = null
    private var globalTimer: (() -> Unit)? = null
    private var phaseTimer: (() -> Unit)? = null
    private var probeTimer: (() -> Unit)? = null
    private val remaining = ArrayDeque<AutoScanStep>()

    fun start(step: AutoScanStep, complete: (Result<Unit>) -> Unit) {
        cancel()
        require(step.isNavigation)
        active = true; recoveryUsed = false; original = step; completion = complete
        val token = generation
        globalTimer = scheduler.after(GLOBAL_TIMEOUT_MILLIS) {
            if (active && token == generation) fail("Nie udało się bezpiecznie wrócić do właściwego ekranu. Ponów odczyt.")
        }
        transition(step, recovering = false)
    }

    fun cancel() {
        generation++; active = false; phase++
        globalTimer?.invoke(); globalTimer = null
        clearPhase(); remaining.clear(); completion = null
    }

    private fun clearPhase() {
        phaseTimer?.invoke(); phaseTimer = null
        probeTimer?.invoke(); probeTimer = null
        runCatching { port.cancel() }
    }

    private fun current(token: Long, turn: Long) = active && generation == token && phase == turn

    /** Preflight also makes repeated navigation idempotent: an already-open target needs no tap. */
    private fun transition(step: AutoScanStep, recovering: Boolean) {
        if (!active) return
        observe(step, recovering, beforeTap = true)
    }

    private fun tap(step: AutoScanStep, recovering: Boolean) {
        clearPhase(); val turn = ++phase; val token = generation
        var delivered = false
        try {
            port.tap(step, recovering) tapped@{ result ->
                if (!current(token, turn) || delivered) return@tapped
                delivered = true
                if (result.isFailure) fail(result.exceptionOrNull()?.message ?: "Nie udało się wykonać przejścia.")
                else observe(step, recovering, beforeTap = false)
            }
        } catch (problem: Exception) {
            if (current(token, turn)) fail(problem.message ?: "Nie udało się wykonać przejścia.")
        }
    }

    private fun observe(step: AutoScanStep, recovering: Boolean, beforeTap: Boolean) {
        clearPhase(); val turn = ++phase; val token = generation
        onStatus(step, recovering)
        if (!current(token, turn)) return
        val ready = NavigationReadinessGate(step, nowMillis(),
            minimumSettleMillis = NavigationReadinessGate.minimumSettleMillis(step))
        phaseTimer = scheduler.after(NavigationReadinessGate.PHASE_TIMEOUT_MILLIS +
            NavigationReadinessGate.LATE_TARGET_GRACE_MILLIS) {
            if (current(token, turn)) timedOut(ready.stableScreen(nowMillis()), recovering)
        }

        fun inspect() {
            if (!current(token, turn)) return
            var delivered = false
            try {
                port.inspect observed@{ observed ->
                    if (!current(token, turn) || delivered) return@observed
                    delivered = true
                    if (observed.isFailure) {
                        fail(observed.exceptionOrNull()?.message ?: "Nie można sprawdzić ekranu.")
                        return@observed
                    }
                    val now = nowMillis()
                    when (ready.observe(observed.getOrThrow(), now)) {
                        NavigationReadinessGate.Status.READY -> next()
                        NavigationReadinessGate.Status.TIMEOUT -> timedOut(ready.stableScreen(now), recovering)
                        NavigationReadinessGate.Status.WAIT -> {
                            val source = ready.stableScreen(now)
                            if (beforeTap && source != GameScreenKind.UNKNOWN &&
                                !NavigationScreenPolicy.accepts(step, source)) {
                                if (isSource(step, source)) tap(step, recovering)
                                else restore(source, recovering)
                            } else probeTimer = scheduler.after(NavigationReadinessGate.OBSERVATION_GAP_MILLIS, ::inspect)
                        }
                    }
                }
            } catch (problem: Exception) {
                if (current(token, turn)) fail(problem.message ?: "Nie można sprawdzić ekranu.")
            }
        }
        probeTimer = scheduler.after(if (beforeTap) 0L else 160L, ::inspect)
    }

    private fun isSource(step: AutoScanStep, screen: GameScreenKind): Boolean = when (step) {
        AutoScanStep.OPEN_MOUNTS, AutoScanStep.OPEN_RESEARCH -> screen == GameScreenKind.HOME
        AutoScanStep.CLOSE_MOUNTS -> screen == GameScreenKind.MOUNTS
        AutoScanStep.OPEN_SKILLS, AutoScanStep.OPEN_PETS, AutoScanStep.CLOSE_RESEARCH ->
            screen == GameScreenKind.SKILLS || screen == GameScreenKind.PETS || screen == GameScreenKind.TECHNOLOGY
        else -> false
    }

    private fun next() {
        clearPhase(); phase++
        val step = remaining.removeFirstOrNull()
        if (step == null) finish(Result.success(Unit)) else transition(step, recovering = true)
    }

    private fun timedOut(screen: GameScreenKind, recovering: Boolean) {
        clearPhase(); phase++
        restore(screen, recovering)
    }

    private fun restore(screen: GameScreenKind, recovering: Boolean) {
        if (!active) return
        if (recovering) {
            fail("Przejście nadal nie jest gotowe. Zatrzymano skan bez kolejnych kliknięć.")
            return
        }
        val route = NavigationRecoveryPlan.steps(original, screen, recoveryUsed)
        if (route == null) {
            fail("Nie rozpoznaję pewnego ekranu do wznowienia. Wróć do młotków i ponów odczyt.")
            return
        }
        clearPhase(); phase++; recoveryUsed = true
        remaining.clear(); remaining.addAll(route)
        val first = remaining.removeFirstOrNull()
        if (first == null) finish(Result.success(Unit)) else transition(first, recovering = true)
    }

    private fun fail(message: String) = finish(Result.failure(IllegalStateException(message)))

    private fun finish(result: Result<Unit>) {
        if (!active) return
        val done = completion
        cancel()
        done?.invoke(result)
    }

    companion object { const val GLOBAL_TIMEOUT_MILLIS = 8500L }
}
