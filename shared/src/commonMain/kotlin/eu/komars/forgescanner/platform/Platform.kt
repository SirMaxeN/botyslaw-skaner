package eu.komars.forgescanner.platform

/** Unicode NFD decomposition (diacritics split off), as used by name/label matching. */
expect fun normalizeNfd(value: String): String

/** Wall-clock milliseconds. */
expect fun nowMillis(): Long

/** `String.format(Locale.ROOT, "%.4f", value)` — fixed four decimals, root locale. */
expect fun formatFixed4(value: Double): String
