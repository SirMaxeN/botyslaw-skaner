package eu.komars.forgescanner.automation

/** Only distinct native recorder callbacks can permit another physical stroke. */
internal class PhysicalStrokeCaptureGate(
    private val maximumAgeMillis: Long = 150,
    private val maximumWaitMillis: Long = 750
) {
    enum class Status { READY, WAIT, STALLED }
    var latestTimestampNanos = 0L
        private set
    private var latestArrivalMillis = -1L
    private var waitingSince = -1L
    private var lastCheckMillis = -1L

    init { require(maximumAgeMillis > 0 && maximumWaitMillis > 0) }

    fun observe(timestampNanos: Long, arrivalMillis: Long) {
        if (timestampNanos <= latestTimestampNanos || arrivalMillis < 0) return
        if (latestArrivalMillis >= 0 && arrivalMillis < latestArrivalMillis) return
        latestTimestampNanos = timestampNanos
        latestArrivalMillis = arrivalMillis
    }

    fun check(nowMillis: Long, afterTimestampNanos: Long, allowInitialBootstrap: Boolean = false): Status {
        if (nowMillis < 0 || nowMillis < lastCheckMillis || nowMillis < latestArrivalMillis) return Status.STALLED
        lastCheckMillis = nowMillis
        val recent = latestArrivalMillis >= 0 && nowMillis - latestArrivalMillis <= maximumAgeMillis
        if (allowInitialBootstrap || (recent && latestTimestampNanos > afterTimestampNanos)) {
            resetWait()
            return Status.READY
        }
        if (waitingSince < 0) waitingSince = nowMillis
        return if (nowMillis - waitingSince >= maximumWaitMillis) Status.STALLED else Status.WAIT
    }

    fun resetWait() { waitingSince = -1L }
}
