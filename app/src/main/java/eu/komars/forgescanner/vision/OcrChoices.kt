package eu.komars.forgescanner.vision

/** Multiple OCR passes may locate the same number at slightly different coordinates. */
object OcrChoices {
    fun unique(candidates: List<OcrCandidate>, isCost: Boolean): List<OcrCandidate> = candidates
        .filter { !isCost || it.value > 0 }
        .sortedWith(compareBy<OcrCandidate> { it.approximate }.thenByDescending { it.confidence })
        .distinctBy { it.value }
        .take(20)
}
