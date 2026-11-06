package eu.komars.forgescanner.automation

import eu.komars.forgescanner.model.ScanTarget

/** Screen inspection is a fallback for a repeatedly unreadable counter, never a routine step.
 * The caller resets the empty-burst streak after any candidate or incomplete source burst.
 */
object CriticalReadRecoveryPolicy {
    fun expectedNavigation(target: ScanTarget): AutoScanStep? = when (target) {
        ScanTarget.HAMMERS -> AutoScanStep.CLOSE_RESEARCH
        ScanTarget.MOUNT_KEYS, ScanTarget.MOUNT_COST -> AutoScanStep.OPEN_MOUNTS
        ScanTarget.TICKETS, ScanTarget.SKILL_COST -> AutoScanStep.OPEN_SKILLS
        // A list may already be partly counted or scrolled; do not restart it implicitly.
        ScanTarget.MOUNTS, ScanTarget.PETS -> null
    }

    fun eligible(consecutiveEmptyCompleteBursts: Int, readCandidates: Int, sourceFrames: Int,
                 alreadyAttempted: Boolean, captureReady: Boolean, gameForeground: Boolean): Boolean =
        consecutiveEmptyCompleteBursts >= 2 && sourceFrames >= 3 && readCandidates == 0 &&
            !alreadyAttempted && captureReady && gameForeground
}
