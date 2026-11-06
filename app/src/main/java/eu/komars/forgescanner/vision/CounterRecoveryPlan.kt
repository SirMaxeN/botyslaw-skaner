package eu.komars.forgescanner.vision

/** Pure geometry; candidates may never cross an independently detected counter container. */
internal data class CounterBounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = right - left
    val height get() = bottom - top
    fun valid(frameWidth: Int, frameHeight: Int) = left >= 0 && top >= 0 && right <= frameWidth && bottom <= frameHeight && width >= 8 && height >= 8
    fun contains(other: CounterBounds) = left <= other.left && top <= other.top && right >= other.right && bottom >= other.bottom
}

internal data class CounterRecoveryCandidate(val crop: CounterBounds, val persistence: CounterBounds, val anchored: Boolean)

internal data class CounterRecoveryPlan(val candidates: List<CounterRecoveryCandidate>, val verifyOriginal: Boolean) {
    companion object {
        const val MAX_ATTEMPTS = 3
        fun create(original: CounterBounds, detected: CounterBounds?, container: CounterBounds?,
                   frameWidth: Int, frameHeight: Int, confidence: Float): CounterRecoveryPlan {
            val originalValid = original.valid(frameWidth, frameHeight)
            val anchor = detected?.takeIf { confidence.isFinite() && confidence >= .8f && it.valid(frameWidth, frameHeight) }
            val safeContainer = container?.takeIf { it.valid(frameWidth, frameHeight) && anchor != null && it.contains(anchor) } ?: anchor
            val values = mutableListOf<CounterRecoveryCandidate>()
            if (originalValid) values += CounterRecoveryCandidate(original, original, false)
            if (anchor != null && safeContainer != null) {
                // Keep the full usable width when saving: future values can acquire more digits.
                values += CounterRecoveryCandidate(anchor, safeContainer, true)
                if (anchor.height >= 20) {
                    // Only remove one top/bottom border pixel; never trim horizontal digits.
                    val inner = CounterBounds(anchor.left, anchor.top + 1, anchor.right, anchor.bottom - 1)
                    if (inner.valid(frameWidth, frameHeight) && safeContainer.contains(inner)) {
                        values += CounterRecoveryCandidate(inner, safeContainer, true)
                    }
                }
            }
            val verify = originalValid && anchor != null && safeContainer != null &&
                (!safeContainer.contains(original) || original.width < anchor.width * .7 ||
                    original.left > anchor.left + anchor.width * .05 ||
                    original.right < anchor.right - anchor.width * .05)
            // An identical original/anchor still has an anchored fallback via the border-only crop.
            return CounterRecoveryPlan(values.distinctBy { it.crop }.take(MAX_ATTEMPTS), verify)
        }
    }
}

/** Independent stable values that disagree must never be resolved by choosing the largest. */
internal class CounterRecoveryAgreement {
    private val values = linkedSetOf<Long>()
    val conflicting: Boolean get() = values.size > 1
    /** A crop disproved by a strong field anchor is not evidence of the complete number. */
    fun observeStable(value: Long, trusted: Boolean = true): Boolean {
        if (trusted) values += value
        return !conflicting
    }
}
