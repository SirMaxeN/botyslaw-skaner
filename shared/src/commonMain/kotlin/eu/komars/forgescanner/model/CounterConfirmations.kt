package eu.komars.forgescanner.model

/**
 * A counter value confirmed by the user, forwarded to whoever learns game-font digits from it
 * (Android: GameDigitMemory). Kept as a hook so the scan model has no platform dependency.
 */
object CounterConfirmations {
    @kotlin.concurrent.Volatile var listener: ((key: String, value: Long) -> Unit)? = null
}
