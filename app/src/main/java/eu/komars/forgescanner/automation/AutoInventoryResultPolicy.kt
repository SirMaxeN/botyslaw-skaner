package eu.komars.forgescanner.automation

import eu.komars.forgescanner.model.ScanSession
import eu.komars.forgescanner.model.ScanTarget
import eu.komars.forgescanner.model.ScrollAnalysis

/** The automatic and manual flows keep the same recording result and uncertainty. */
internal object AutoInventoryResultPolicy {
    fun reading(target: ScanTarget, analysis: ScrollAnalysis): AutoReading {
        require(target.isInventory)
        check(analysis.count in target.baseCount.toLong()..ScanSession.MAX_VALUE) {
            "Odczyt listy jest nieprawidłowy. Sprawdź obszar i odstęp rzędów."
        }
        val approximate = analysis.approximate || analysis.uncertainFrames > 0 ||
            analysis.lowConfidence || analysis.inferredRows > 0
        return AutoReading(analysis.count - target.baseCount, approximate,
            "${analysis.message} Łącznie ${analysis.count}; odjęto ${target.baseCount} bazowe.")
    }
}