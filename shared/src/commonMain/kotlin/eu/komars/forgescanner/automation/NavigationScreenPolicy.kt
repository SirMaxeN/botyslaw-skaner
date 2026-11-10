package eu.komars.forgescanner.automation

import eu.komars.forgescanner.model.GameScreenKind

/** Stable screen controls establish the destination; pixel changes alone never do. */
object NavigationScreenPolicy {
    fun accepts(step: AutoScanStep, screen: GameScreenKind): Boolean = when (step) {
        AutoScanStep.OPEN_MOUNTS -> screen == GameScreenKind.MOUNTS
        AutoScanStep.CLOSE_MOUNTS, AutoScanStep.CLOSE_RESEARCH -> screen == GameScreenKind.HOME
        AutoScanStep.OPEN_RESEARCH -> screen in setOf(GameScreenKind.SKILLS, GameScreenKind.PETS, GameScreenKind.TECHNOLOGY)
        AutoScanStep.OPEN_SKILLS -> screen == GameScreenKind.SKILLS
        AutoScanStep.OPEN_PETS -> screen == GameScreenKind.PETS
        AutoScanStep.OPEN_TECH -> screen == GameScreenKind.TECHNOLOGY
        else -> false
    }
}
