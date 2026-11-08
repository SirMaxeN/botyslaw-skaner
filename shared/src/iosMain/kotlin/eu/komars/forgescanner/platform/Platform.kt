package eu.komars.forgescanner.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDate
import platform.Foundation.NSString
import platform.Foundation.decomposedStringWithCanonicalMapping
import platform.Foundation.stringWithFormat
import platform.Foundation.timeIntervalSince1970

// iOS: Foundation equivalents. Compiled only on a Mac (Kotlin/Native iOS targets are disabled on Windows).

@Suppress("CAST_NEVER_SUCCEEDS")
actual fun normalizeNfd(value: String): String = (value as NSString).decomposedStringWithCanonicalMapping

actual fun nowMillis(): Long = (NSDate().timeIntervalSince1970 * 1000.0).toLong()

// printf rounds exact binary ties half-even where Java rounds half-up; only affects exported glyph aspect text.
@OptIn(ExperimentalForeignApi::class)
actual fun formatFixed4(value: Double): String = NSString.stringWithFormat("%.4f", value)
