package eu.komars.forgescanner.update

/** Bounded availability check. Unknown/error is deliberately distinct from "up to date". */
class PlayUpdateCheckController(private val port: Port, private val scheduler: Scheduler) {
    enum class Status { UPDATE_AVAILABLE, UP_TO_DATE, UNAVAILABLE }
    enum class Presentation { OFFER_UPDATE, SHOW_CURRENT, SHOW_RETRY, OPEN_SCANNER }
    fun interface Port { fun request(complete: (Status) -> Unit) }
    fun interface Scheduler { fun after(delayMillis: Long, action: () -> Unit): () -> Unit }

    private var generation = 0L
    private var active = false
    private var timeout: (() -> Unit)? = null
    private var completion: ((Status) -> Unit)? = null

    fun check(complete: (Status) -> Unit) {
        cancel()
        val token = generation
        active = true; completion = complete
        timeout = scheduler.after(TIMEOUT_MILLIS) {
            if (active && generation == token) finish(Status.UNAVAILABLE)
        }
        try {
            port.request { status ->
                if (active && generation == token) finish(status)
            }
        } catch (_: Exception) {
            if (active && generation == token) finish(Status.UNAVAILABLE)
        }
    }

    fun cancel() {
        generation++; active = false
        timeout?.invoke(); timeout = null
        completion = null
    }

    private fun finish(status: Status) {
        val done = completion
        cancel()
        done?.invoke(status)
    }

    companion object {
        const val TIMEOUT_MILLIS = 3000L

        fun presentation(status: Status, manual: Boolean): Presentation = when {
            status == Status.UPDATE_AVAILABLE -> Presentation.OFFER_UPDATE
            !manual -> Presentation.OPEN_SCANNER
            status == Status.UP_TO_DATE -> Presentation.SHOW_CURRENT
            else -> Presentation.SHOW_RETRY
        }
    }
}
