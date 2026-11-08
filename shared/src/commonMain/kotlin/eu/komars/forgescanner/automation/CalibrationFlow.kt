package eu.komars.forgescanner.automation

/**
 * Session-only calibration navigation. Saved positions live elsewhere: entering,
 * completing or cancelling this flow never erases existing calibration choices.
 */
class CalibrationFlow {
    private val approvedFields=mutableSetOf<AutoScanStep>()
    fun approve(fields:Collection<AutoScanStep>) {approvedFields.addAll(fields)}
    fun isApproved(step:AutoScanStep)=step in approvedFields
    var current: AutoScanStep? = null
        private set
    var isRepair: Boolean = false
        private set
    val index: Int? get() = current?.ordinal
    val isComplete: Boolean get() = current == null

    fun beginAll(): AutoScanStep {
        approvedFields.clear()
        isRepair = false
        return AutoScanStep.entries.first().also { current = it }
    }

    fun beginRepair(step: AutoScanStep): AutoScanStep {
        approvedFields.clear()
        isRepair = true
        current = step
        return step
    }

    /** Advance only after this field's save succeeds. Repairs finish at that one field.
     * Supplying the saved step also prevents a late callback from skipping a later field.
     */
    fun advance(expected: AutoScanStep? = current): AutoScanStep? {
        val step = current ?: return null
        if (expected != step) return current
        current = if (isRepair) null else AutoScanStep.entries.drop(step.ordinal+1)
            .firstOrNull { it.isNavigation || it !in approvedFields }
        return current
    }

    fun clear() { approvedFields.clear();current = null; isRepair = false }
    fun cancel() = clear()
}
