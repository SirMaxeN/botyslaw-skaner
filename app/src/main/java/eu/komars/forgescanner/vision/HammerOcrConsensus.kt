package eu.komars.forgescanner.vision

/** Each analysis is one source frame. Contrast passes never create extra votes. */
object HammerOcrConsensus {
    const val FRAME_COUNT = 3

    fun combine(frames: List<OcrAnalysis>): OcrAnalysis {
        val choices = frames.map { OcrChoices.unique(it.candidates, isCost = false) }
        val votes = choices.flatMap { it.map(OcrCandidate::value) }.groupingBy { it }.eachCount()
        val candidates = choices.flatten().groupBy(OcrCandidate::value).map { (value, readings) ->
            val best = readings.sortedWith(compareBy<OcrCandidate> { it.approximate }.thenByDescending { it.confidence }).first()
            best.copy(approximate = readings.any { it.approximate }, reason = "Zgodne klatki: ${votes[value]}/${frames.size}. Sprawdź odczyt.")
        }.sortedWith(compareByDescending<OcrCandidate> { votes[it.value] ?: 0 }.thenBy { it.approximate }.thenByDescending { it.confidence })
        val stable = frames.size == FRAME_COUNT && frames.all { it.error == null } &&
            choices.all { it.size == 1 } && choices.map { it.single().value }.distinct().size == 1
        return OcrAnalysis(candidates, frames.flatMap { it.lines },
            error = if (candidates.isEmpty()) "Nie odczytano młotków. Ustaw obszar lub wpisz wartość ręcznie." else null,
            requiresConfirmation = !stable)
    }
}
