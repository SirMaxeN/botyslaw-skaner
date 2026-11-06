package eu.komars.forgescanner.model

/** Polling a cached native image is not another observation of list motion.
 * IDs are assigned when ImageReader acquires a new Image, never by comparing pixels.
 * Zero keeps replay fixtures and explicit injected frames backwards compatible.
 */
class SourceFrameSequence {
    private var lastSourceFrameId = 0L

    fun accept(frame: GrayScrollFrame): Boolean {
        if (frame.sourceFrameId <= 0) return true
        if (frame.sourceFrameId <= lastSourceFrameId) return false
        lastSourceFrameId = frame.sourceFrameId
        return true
    }
}
