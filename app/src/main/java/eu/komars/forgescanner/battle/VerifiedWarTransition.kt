package eu.komars.forgescanner.battle

/**
 * One source-checked tap followed by two independent, time-separated destination observations.
 *
 * All methods, scheduled actions and port callbacks must run on the same event loop. State labels
 * are supplied by the caller's screen classifier: an empty set means unknown; [start]'s source
 * labels are alternatives, not a list of required features. The caller must not publish a screen
 * hidden behind a modal as the current source. A destination label takes precedence over source.
 *
 * A successful gesture is not proof of navigation. This runner never retries a tap. Unknown
 * sources may settle until the overall deadline; a recognized wrong source fails immediately.
 * After tapping, unrelated and unknown observations reset destination stability and keep waiting.
 * [cancel] silently invalidates pending callbacks, including callbacks from an earlier [start].
 */
class VerifiedWarTransition(
    private val port: Port,
    private val scheduler: Scheduler,
    private val nowMillis: () -> Long,
    private val timeoutMillis: Long = 6_000L,
    private val observationGapMillis: Long = 250L,
    private val requiredDestinationObservations: Int = 2
) {
    interface Port {
        /** Observe a clean current frame, delivering a result on the caller's event loop. */
        fun observe(complete: (Result<Set<String>>) -> Unit)
        fun tap(complete: (Result<Unit>) -> Unit)
        /** Cancel this transition's pending work; do not cancel the surrounding gesture session. */
        fun cancel()
    }

    fun interface Scheduler {
        /** Queue an action (never invoke inline) and return an idempotent cancellation function. */
        fun after(delayMillis: Long, action: () -> Unit): () -> Unit
    }

    init {
        require(requiredDestinationObservations in 1..2)
        require(observationGapMillis >= 250L && timeoutMillis > observationGapMillis)
    }

    private var generation = 0L
    private var active = false
    private var completion: ((Result<Unit>) -> Unit)? = null
    private var deadline: (() -> Unit)? = null
    private var probe: (() -> Unit)? = null
    private var source = emptySet<String>()
    private var destination = ""
    private var allowAlready = false
    private var tapped = false
    private var checkingDestination = false
    private var firstDestinationAt: Long? = null
    private var startedAt = 0L

    /**
     * Begin a new transition, silently cancelling any previous one. [source] contains acceptable
     * source states. An already visible [destination] fails unless [allowAlreadyAtDestination]
     * explicitly allows an idempotent no-tap result; that result still needs two observations.
     * Invalid arguments and port/scheduler failures are delivered through [complete].
     */
    fun start(source: Set<String>, destination: String,
              allowAlreadyAtDestination: Boolean = false, complete: (Result<Unit>) -> Unit) {
        cancel()
        active = true
        completion = complete
        this.source = source.toSet()
        this.destination = destination
        allowAlready = allowAlreadyAtDestination
        tapped = false
        checkingDestination = false
        firstDestinationAt = null
        if (source.isEmpty() || source.any(String::isBlank) || destination.isBlank() || destination in source) {
            fail("Nieprawidłowy opis przejścia między ekranami.")
            return
        }
        val token = generation
        try {
            startedAt = nowMillis()
            deadline = scheduler.after(timeoutMillis) {
                if (current(token)) fail("Nie potwierdzono przejścia do właściwego ekranu. Zatrzymano skan.")
            }
            observe(token)
        } catch (error: Exception) {
            if (current(token)) finish(Result.failure(error))
        }
    }

    /** Stop without reporting a completion, even if a port delivers a late or duplicate result. */
    fun cancel() {
        generation++
        active = false
        completion = null
        val oldDeadline = deadline; deadline = null
        val oldProbe = probe; probe = null
        runCatching { oldDeadline?.invoke() }
        runCatching { oldProbe?.invoke() }
        runCatching { port.cancel() }
    }

    private fun current(token: Long) = active && generation == token

    private fun observe(token: Long) {
        if (!current(token)) return
        var delivered = false
        try {
            port.observe observed@{ result ->
                if (!current(token) || delivered) return@observed
                delivered = true
                try {
                    val now = nowMillis()
                    if (now < startedAt || now - startedAt >= timeoutMillis) {
                        fail("Upłynął czas potwierdzenia ekranu.")
                        return@observed
                    }
                    val states = result.getOrThrow()
                    if (checkingDestination) {
                        confirmDestination(states, now, token)
                    } else if (destination in states) {
                        if (!allowAlready) fail("Ekran docelowy był już otwarty. Nie wykonano kliknięcia.")
                        else {
                            checkingDestination = true
                            confirmDestination(states, now, token)
                        }
                    } else if (states.any { it in source }) {
                        tap(token)
                    } else if (states.isEmpty()) {
                        scheduleObservation(token)
                    } else {
                        fail("Nie rozpoznano właściwego ekranu przed kliknięciem. Zatrzymano skan.")
                    }
                } catch (error: Exception) {
                    if (current(token)) finish(Result.failure(error))
                }
            }
        } catch (error: Exception) {
            if (!delivered && current(token)) finish(Result.failure(error))
        }
    }

    private fun tap(token: Long) {
        if (!current(token) || tapped) return
        tapped = true
        var delivered = false
        try {
            port.tap completed@{ result ->
                if (!current(token) || delivered) return@completed
                delivered = true
                if (result.isFailure) finish(result)
                else {
                    checkingDestination = true
                    firstDestinationAt = null
                    scheduleObservation(token)
                }
            }
        } catch (error: Exception) {
            if (!delivered && current(token)) finish(Result.failure(error))
        }
    }

    private fun confirmDestination(states: Set<String>, now: Long, token: Long) {
        if (destination !in states) firstDestinationAt = null
        else {
            val first = firstDestinationAt
            if (requiredDestinationObservations == 1 || (first != null && now - first >= observationGapMillis)) {
                finish(Result.success(Unit))
                return
            }
            if (first == null) firstDestinationAt = now
        }
        scheduleObservation(token)
    }

    private fun scheduleObservation(token: Long) {
        if (!current(token)) return
        try {
            probe = scheduler.after(observationGapMillis) {
                if (current(token)) { probe = null; observe(token) }
            }
        } catch (error: Exception) {
            if (current(token)) finish(Result.failure(error))
        }
    }

    private fun fail(message: String) = finish(Result.failure(IllegalStateException(message)))

    private fun finish(result: Result<Unit>) {
        if (!active) return
        val done = completion
        cancel()
        done?.invoke(result)
    }
}
