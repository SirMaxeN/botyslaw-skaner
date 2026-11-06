package eu.komars.forgescanner.vision

import eu.komars.forgescanner.model.ScanTarget

/** One list entry is one independent source frame, regardless of contrast-pass count. */
object CounterOcrConsensus {
    const val FRAME_COUNT = 3

    /** Preserve review requirements through the existing paired-reader list API. */
    fun forManualPair(analysis: OcrAnalysis, target: ScanTarget): List<OcrCandidate> =
        OcrChoices.unique(analysis.candidates, target.field.isCost).map { candidate ->
            if (analysis.requiresConfirmation) candidate.copy(approximate = true,
                reason = "Niepewny odczyt — wybierz wartość ręcznie. ${candidate.reason}") else candidate
        }

    fun combine(frames: List<OcrAnalysis>, target: ScanTarget): OcrAnalysis {
        require(!target.isInventory)
        val choices = frames.map { frame ->
            OcrChoices.unique(frame.candidates.filter { it.field == null || it.field == target.field }, target.field.isCost)
        }
        val votes = choices.flatMap { it.map(OcrCandidate::value) }.groupingBy { it }.eachCount()
        val candidates = choices.flatten().groupBy(OcrCandidate::value).map { (value, readings) ->
            val best = readings.sortedWith(compareBy<OcrCandidate> { it.approximate }.thenByDescending { it.confidence }).first()
            best.copy(field = target.field, approximate = readings.any { it.approximate },
                reason = "Zgodne klatki: ${votes[value]}/${frames.size}. Sprawdź: ${target.title.lowercase()}.")
        }.sortedWith(compareByDescending<OcrCandidate> { votes[it.value] ?: 0 }.thenBy { it.approximate }.thenByDescending { it.confidence })
        val stable = frames.size == FRAME_COUNT && frames.all { it.error == null && !it.requiresConfirmation } &&
            choices.all { it.size == 1 } && choices.map { it.single().value }.distinct().size == 1
        return OcrAnalysis(candidates, frames.flatMap { it.lines },
            error = if (candidates.isEmpty()) "${target.title}: brak czytelnej liczby. Ustaw obszar lub wpisz wartość ręcznie." else null,
            requiresConfirmation = !stable)
    }
}

/** A shortcut changes image variants, never the number of independently captured source frames. */
object CounterOcrPassPolicy {
    fun canVerify(previousFrames: List<OcrAnalysis>, target: ScanTarget): Boolean =
        previousFrames.isNotEmpty() && previousFrames.map { singleValue(it, target) }.let { values ->
            values.all { it != null } && values.distinct().size == 1
        }

    /** The original crop and isolated white digit interiors must each provide their own answer. */
    fun verificationAgrees(passes: List<OcrAnalysis>, target: ScanTarget): Boolean =
        passes.size == 2 && singleValue(passes[0], target)?.let { value ->
            value == singleValue(passes[1], target)
        } == true

    private fun singleValue(analysis: OcrAnalysis, target: ScanTarget): Long? {
        if (analysis.error != null || analysis.requiresConfirmation) return null
        return OcrChoices.unique(analysis.candidates.filter { it.field == null || it.field == target.field },
            target.field.isCost).singleOrNull()?.value
    }
}
