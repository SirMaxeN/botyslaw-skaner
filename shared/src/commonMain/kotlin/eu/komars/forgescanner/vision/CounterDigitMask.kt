package eu.komars.forgescanner.vision

/** Preserve white counters and red insufficient-resource digits on a white OCR background. */
object CounterDigitMask {
    fun isInk(red: Int, green: Int, blue: Int): Boolean {
        val luminance = (red * 299 + green * 587 + blue * 114) / 1000
        return luminance > 200 || (red >= 145 && red - green >= 45 && red - blue >= 30)
    }
}
