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

    /** Font votes are strict shape matches, one per source frame. A cached value supplies no vote. */
    fun combine(frames: List<OcrAnalysis>, target: ScanTarget, fontVotes: List<Long?> = emptyList()): OcrAnalysis {
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
        // Extra bursts (3, 6 or 9 frames) are judged together; three frames keep the strictest rules.
        val extended = frames.size > FRAME_COUNT
        val stable = frames.size >= FRAME_COUNT && candidates.size == 1 &&
            frames.indices.count { index ->
                val frame = frames[index]
                frame.error == null && !frame.requiresConfirmation && choices[index].size == 1
            } >= 2
        // Game-font reader: a value read identically in at least two of the three source frames.
        val fontCounts = if (frames.size >= FRAME_COUNT && fontVotes.size == frames.size)
            fontVotes.filterNotNull().groupingBy { it }.eachCount() else emptyMap()
        val unanimousFont = fontCounts.entries.singleOrNull()?.takeIf { it.value == frames.size }?.key
        val font = fontCounts.filterValues { it >= maxOf(2, (frames.size + 1) / 2) }.keys.singleOrNull()?.takeIf { it >= 0 && (!target.field.isCost || it > 0) }
        val fontCandidate = font?.let { value ->
            candidates.firstOrNull { it.value == value && !it.approximate }
                ?: OcrCandidate(target.field, value, value.toString(), ScreenBounds(0, 0, 0, 0),
                    .85f, false, "Czcionka gry: zgodne cyfry w ${fontCounts[value]} z ${frames.size} klatek.")
        }
        /** "≈ 9,88k" (9 880) does not contradict an exact 9 876 (within 0.1%); "≈ 9,9k" does. */
        fun conflictsWith(value: Long?) = candidates.any { it.value != value && it.approximate &&
            (value == null || kotlin.math.abs(it.value - value) > maxOf(1L, value / 1000)) }
        val readingFrames = choices.indices.filter { choices[it].isNotEmpty() && frames[it].error == null }
        val framesWith = candidates.associate { c -> c.value to choices.count { frame -> frame.any { it.value == c.value } } }
        // Strong majority: one exact value present in every frame that read anything (at least two frames),
        // every alternative seen in fewer frames, and no game-font majority saying otherwise.
        fun supports(c: OcrCandidate, top: OcrCandidate) = c.value == top.value ||
            c.approximate && kotlin.math.abs(c.value - top.value) <= maxOf(1L, top.value / 1000)
        val majority = candidates.firstOrNull { !it.approximate }?.takeIf { top ->
            val topFrames = framesWith[top.value] ?: 0
            frames.size >= FRAME_COUNT && readingFrames.size >= 2 &&
                (if (!extended) readingFrames.all { index -> choices[index].any { supports(it, top) } }
                 // More data: a clear majority of reading frames (at least 3 and 60 %), rare slips allowed.
                 else topFrames >= maxOf(3, (readingFrames.size * 3 + 4) / 5)) &&
                readingFrames.count { index -> !frames[index].requiresConfirmation && choices[index].any { it.value == top.value } } >= 2 &&
                candidates.filterNot { supports(it, top) }.all { (framesWith[it.value] ?: 0) <= (if (extended) maxOf(1, topFrames / 3) else 1) } &&
                (font == null || font == top.value) && (unanimousFont == null || unanimousFont == top.value) &&
                !conflictsWith(top.value)
        }
        // Game-font majority settles OCR alternatives it agrees with, unless another value has two OCR frames.
        val fontSettles = !stable && fontCandidate != null && !conflictsWith(font) &&
            (candidates.isEmpty() && unanimousFont == font ||
                (votes[font] ?: 0) >= 1 && candidates.none { it.value != font &&
                    (framesWith[it.value] ?: 0) >= (if (extended) maxOf(2, framesWith[font] ?: 0) else 2) })
        val confirmed = when {
            stable && (font == null || font == candidates.single().value) && (unanimousFont == null || unanimousFont == candidates.single().value) -> candidates.single()
            majority != null -> majority.copy(reason = "Przewaga odczytów: ${framesWith[majority.value]}/${frames.size} klatek.")
            fontSettles -> fontCandidate
            else -> null
        }
        val ordered = confirmed?.let(::listOf) ?: (candidates + listOfNotNull(fontCandidate))
            .distinctBy { it.value }
        return OcrAnalysis(ordered, frames.flatMap { it.lines },
            error = if (ordered.isEmpty()) "${target.title}: brak czytelnej liczby. Ustaw obszar lub wpisz wartość ręcznie." else null,
            requiresConfirmation = confirmed == null)
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
