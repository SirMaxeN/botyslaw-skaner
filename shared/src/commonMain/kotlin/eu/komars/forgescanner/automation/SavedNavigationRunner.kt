package eu.komars.forgescanner.automation

/** Fast route for user-confirmed navigation coordinates: one tap, then a quiet settling gap.
 * This runner never captures or recognizes a screen. Read failures may invoke a separate
 * recovery coordinator; an ordinary successful tap never starts recognition or another tap.
 * Callbacks and scheduled actions must run on the caller's event loop.
 */
class SavedNavigationRunner(private val port: Port, private val scheduler: Scheduler) {
    interface Port {
        fun tap(step: AutoScanStep, complete: (Result<Unit>) -> Unit)
        /** Cancel pending work; the surrounding scan owns the armed gesture session. */
        fun cancel()
    }
    fun interface Scheduler {
        /** Return an idempotent cancellation function. */
        fun after(delayMillis: Long, action: () -> Unit): () -> Unit
    }

    private var generation = 0L
    private var active = false
    private var completion: ((Result<Unit>) -> Unit)? = null
    private var deadline: (() -> Unit)? = null
    private var settle: (() -> Unit)? = null

    fun start(step: AutoScanStep, complete: (Result<Unit>) -> Unit) {
        require(step.isNavigation) { "Only configured navigation steps may use the saved route." }
        cancel()
        val token = generation
        active = true; completion = complete
        deadline = scheduler.after(TAP_DEADLINE_MILLIS) {
            if (current(token)) finish(Result.failure(IllegalStateException(
                "Przejście trwało za długo. Zatrzymano skan bez kolejnego kliknięcia.")))
        }
        var delivered = false
        try {
            port.tap(step) tapped@{ result ->
                if (!current(token) || delivered) return@tapped
                delivered = true
                if (result.isFailure) finish(result)
                else settle = scheduler.after(settleMillis(step)) {
                    if (current(token)) finish(Result.success(Unit))
                }
            }
        } catch (problem: Exception) {
            if (!delivered && current(token)) finish(Result.failure(problem))
        }
    }

    /** STOP invalidates every outstanding callback without reporting a stale result. */
    fun cancel() {
        generation++; active = false
        deadline?.invoke(); deadline = null
        settle?.invoke(); settle = null
        completion = null
        runCatching { port.cancel() }
    }

    private fun current(token: Long) = active && generation == token

    private fun finish(result: Result<Unit>) {
        if (!active) return
        val done = completion
        cancel()
        done?.invoke(result)
    }

    companion object {
        const val TAP_DEADLINE_MILLIS = 10_000L
        const val CLOSE_SETTLE_MILLIS = 600L
        const val RESEARCH_OPEN_SETTLE_MILLIS = 450L
        const val MOUNTS_OPEN_SETTLE_MILLIS = 400L
        const val TAB_SETTLE_MILLIS = 320L

        fun settleMillis(step: AutoScanStep): Long = when (step) {
            AutoScanStep.CLOSE_MOUNTS, AutoScanStep.CLOSE_RESEARCH -> CLOSE_SETTLE_MILLIS
            AutoScanStep.OPEN_RESEARCH -> RESEARCH_OPEN_SETTLE_MILLIS
            AutoScanStep.OPEN_MOUNTS -> MOUNTS_OPEN_SETTLE_MILLIS
            AutoScanStep.OPEN_SKILLS, AutoScanStep.OPEN_PETS, AutoScanStep.OPEN_TECH -> TAB_SETTLE_MILLIS
            else -> throw IllegalArgumentException("Only navigation steps have a settling gap.")
        }
    }
}
