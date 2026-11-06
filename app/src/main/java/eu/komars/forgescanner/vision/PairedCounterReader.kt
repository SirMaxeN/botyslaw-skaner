package eu.komars.forgescanner.vision

import android.graphics.Bitmap
import android.graphics.Rect
import eu.komars.forgescanner.model.ScanTarget

/** Both separately calibrated counters share the same three fresh screenshot/OCR-strip passes. */
class PairedCounterReader(private val ocr: OcrAnalyzer) {
    fun read(bitmap: Bitmap, target: ScanTarget, regions: Map<ScanTarget, Rect>,
        isActive: () -> Boolean = { true },
        complete: (Result<Map<ScanTarget, List<OcrCandidate>>>) -> Unit) {
        val counters = target.counters
        if (counters.size != 2 || regions.keys != counters.toSet()) {
            if (!bitmap.isRecycled) bitmap.recycle()
            if (isActive()) complete(Result.failure(IllegalArgumentException("Ustaw oddzielnie obszar zasobu i kosztu.")))
            return
        }
        CounterBurstReader(ocr).readMany(bitmap, counters.associateWith { regions.getValue(it) }, isActive) { analyses ->
            val readings = linkedMapOf<ScanTarget, List<OcrCandidate>>()
            for (counter in counters) {
                val analysis = analyses.getValue(counter)
                val candidates = CounterOcrConsensus.forManualPair(analysis, counter)
                if (analysis.error != null || candidates.isEmpty()) {
                    complete(Result.failure(IllegalStateException("${counter.title}: brak czytelnej liczby. Wybierz Ustaw.")))
                    return@readMany
                }
                readings[counter] = candidates
            }
            complete(Result.success(readings.toMap()))
        }
    }
}
