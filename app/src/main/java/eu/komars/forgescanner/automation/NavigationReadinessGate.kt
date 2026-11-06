package eu.komars.forgescanner.automation

import eu.komars.forgescanner.model.GameScreenKind

/** Time-separated observations of fixed screen controls establish readiness, never artwork. */
class NavigationReadinessGate(
    private val step: AutoScanStep,
    private val startedAt: Long,
    private val timeoutMillis: Long = PHASE_TIMEOUT_MILLIS + LATE_TARGET_GRACE_MILLIS,
    private val minimumSettleMillis: Long = NavigationReadinessGate.minimumSettleMillis(step)
) {
    enum class Status { WAIT, READY, TIMEOUT }
    private var candidate = GameScreenKind.UNKNOWN
    private var since = startedAt
    private var observations = 0
    private var latest = startedAt

    init {
        require(step.isNavigation && timeoutMillis > 0 && minimumSettleMillis >= 0)
    }

    fun stableScreen(now: Long): GameScreenKind = candidate.takeIf {
        it != GameScreenKind.UNKNOWN && now >= latest && observations >= 2 &&
            now - since >= OBSERVATION_GAP_MILLIS && now - latest <= MAX_STABLE_AGE_MILLIS
    } ?: GameScreenKind.UNKNOWN

    /** Inspect current rendered chrome again after the gap; a static game need not render a new buffer. */
    fun observe(screen: GameScreenKind, now: Long): Status {
        if (now < latest || now - startedAt >= timeoutMillis) return Status.TIMEOUT
        latest = now
        if (screen == GameScreenKind.UNKNOWN || screen != candidate) {
            candidate = screen; since = now; observations = 1
        } else observations++
        return if (now - startedAt >= minimumSettleMillis &&
            NavigationScreenPolicy.accepts(step, stableScreen(now))) Status.READY else Status.WAIT
    }

    companion object {
        const val OBSERVATION_GAP_MILLIS = 120L
        const val PHASE_TIMEOUT_MILLIS = 3000L
        // A source capture can arrive near Android's 3-second capture timeout.
        // Keep observing without tapping before deciding a transition failed.
        const val LATE_TARGET_GRACE_MILLIS = 750L
        private const val MAX_STABLE_AGE_MILLIS = 600L

        fun minimumSettleMillis(step: AutoScanStep): Long =
            if (step == AutoScanStep.CLOSE_MOUNTS || step == AutoScanStep.CLOSE_RESEARCH) 450L else 240L
    }
}
