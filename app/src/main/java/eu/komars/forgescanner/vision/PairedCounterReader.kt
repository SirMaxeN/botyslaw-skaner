package eu.komars.forgescanner.vision

import android.graphics.Bitmap
import android.graphics.Rect
import eu.komars.forgescanner.model.ScanTarget

/** Owns one screenshot and reads two separately calibrated regions from exactly that frame. */
class PairedCounterReader(private val ocr: OcrAnalyzer) {
    fun read(bitmap: Bitmap, target: ScanTarget, regions: Map<ScanTarget, Rect>,
        complete: (Result<Map<ScanTarget, List<OcrCandidate>>>) -> Unit) {
        val counters = target.counters
        require(counters.size == 2 && regions.keys == counters.toSet())
        val readings = linkedMapOf<ScanTarget, List<OcrCandidate>>()
        fun finish(result: Result<Map<ScanTarget, List<OcrCandidate>>>) {
            if (!bitmap.isRecycled) bitmap.recycle()
            complete(result)
        }
        fun next(index: Int) {
            val counter = counters[index]
            ocr.analyze(bitmap, counter.step, regions.getValue(counter)) { analysis ->
                val candidates = OcrChoices.unique(analysis.candidates, counter.field.isCost)
                if (analysis.error != null || candidates.isEmpty()) {
                    finish(Result.failure(IllegalStateException("${counter.title}: brak czytelnej liczby. Wybierz Ustaw.")))
                } else {
                    readings[counter] = candidates
                    if (index + 1 == counters.size) finish(Result.success(readings.toMap())) else next(index + 1)
                }
            }
        }
        next(0)
    }
}
