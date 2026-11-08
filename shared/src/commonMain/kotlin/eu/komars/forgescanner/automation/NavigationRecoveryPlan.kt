package eu.komars.forgescanner.automation

import eu.komars.forgescanner.model.GameScreenKind

/** At most one restoration route, consisting only of known navigation controls. */
object NavigationRecoveryPlan {
    fun steps(expected: AutoScanStep, screen: GameScreenKind, alreadyUsed: Boolean): List<AutoScanStep>? {
        if (alreadyUsed || screen == GameScreenKind.UNKNOWN || !expected.isNavigation) return null
        if (NavigationScreenPolicy.accepts(expected, screen)) return emptyList()
        val home = when (screen) {
            GameScreenKind.HOME -> emptyList()
            GameScreenKind.MOUNTS -> listOf(AutoScanStep.CLOSE_MOUNTS)
            GameScreenKind.SKILLS, GameScreenKind.PETS, GameScreenKind.TECHNOLOGY -> listOf(AutoScanStep.CLOSE_RESEARCH)
            else -> return null
        }
        return when (expected) {
            AutoScanStep.CLOSE_MOUNTS, AutoScanStep.CLOSE_RESEARCH -> home
            AutoScanStep.OPEN_MOUNTS -> home + AutoScanStep.OPEN_MOUNTS
            AutoScanStep.OPEN_RESEARCH -> home + AutoScanStep.OPEN_RESEARCH
            AutoScanStep.OPEN_SKILLS, AutoScanStep.OPEN_PETS -> {
                if (screen in setOf(GameScreenKind.SKILLS, GameScreenKind.PETS, GameScreenKind.TECHNOLOGY)) listOf(expected)
                else home + listOf(AutoScanStep.OPEN_RESEARCH, expected)
            }
            else -> null
        }
    }
}
