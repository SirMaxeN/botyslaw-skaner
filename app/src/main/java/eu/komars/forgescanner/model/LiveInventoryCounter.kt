package eu.komars.forgescanner.model

import kotlin.math.abs
import kotlin.math.max

/** Provisional progress only. Final full-sequence analysis replaces this value after Stop. */
class LiveInventoryCounter(private val columns: Int, private val rowHeight: Int) {
    private val analyzer = ScrollSequenceAnalyzer()
    private val rows = InventoryRowAccumulator(columns, rowHeight)
    private var previous: GrayScrollFrame? = null
    private var anchor: GrayScrollFrame? = null
    private var anchorOffset = 0.0
    private var offset = 0.0
    private var velocity = 0.0
    val count: Long get() = rows.count

    fun observe(frame: GrayScrollFrame) {
        val old = previous
        if (old != null) {
            // Brief duplicate buffers preserve timing; a sustained hold clears stale fling velocity.
            if (old.pixels.contentEquals(frame.pixels)) {
                if (frame.timestampNanos - old.timestampNanos > 50_000_000L) {
                    velocity = 0.0
                    previous = frame
                }
                rows.observe(frame, offset)
                return
            }
            val dt = if (frame.timestampNanos > old.timestampNanos) (frame.timestampNanos-old.timestampNanos)/1e9 else .016
            val reference = anchor ?: old
            var shift = analyzer.translation(reference, frame, rowHeight, offset-anchorOffset+velocity*dt)
            if (shift == null && reference !== old) {
                anchor = old; anchorOffset = offset
                shift = analyzer.translation(old, frame, rowHeight, velocity*dt)
            }
            if (shift != null) {
                val nextOffset = max(0.0, anchorOffset+shift.pixels)
                val change = nextOffset-offset
                velocity = if (abs(change) < .03) 0.0 else velocity*.5+change/dt*.5
                offset = nextOffset
                if (abs(shift.pixels) >= rowHeight * .40) { anchor = frame; anchorOffset = offset }
            } else {
                // A provisional miss must not keep an increasingly stale reference.
                anchor = frame; anchorOffset = offset
            }
        }
        if (anchor == null) { anchor = frame; anchorOffset = offset }
        previous = frame
        rows.observe(frame, offset)
    }
}
