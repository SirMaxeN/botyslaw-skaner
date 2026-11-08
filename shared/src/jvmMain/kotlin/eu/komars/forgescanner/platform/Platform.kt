package eu.komars.forgescanner.platform

import java.text.Normalizer

// Android/JVM: exactly the calls the app used before the shared module existed.
actual fun normalizeNfd(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)

actual fun nowMillis(): Long = System.currentTimeMillis()

actual fun formatFixed4(value: Double): String = String.format(java.util.Locale.ROOT, "%.4f", value)
