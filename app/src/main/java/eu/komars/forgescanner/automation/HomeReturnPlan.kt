package eu.komars.forgescanner.automation

import eu.komars.forgescanner.model.GameScreenKind

/** A bounded preparation step, separate from the saved-coordinate scanning route. */
object HomeReturnPlan {
    sealed interface Action {
        data object Ready : Action
        data class Close(val step: AutoScanStep) : Action {
            init {
                require(step == AutoScanStep.CLOSE_MOUNTS || step == AutoScanStep.CLOSE_RESEARCH) {
                    "Returning home may only close a known inventory screen."
                }
            }
        }
        data object Unsupported : Action
    }

    /** Never repeat a close if the first tap did not produce a recognized home screen. */
    fun next(screen: GameScreenKind, closeAlreadyAttempted: Boolean = false): Action = when {
        screen == GameScreenKind.HOME -> Action.Ready
        closeAlreadyAttempted -> Action.Unsupported
        screen == GameScreenKind.MOUNTS -> Action.Close(AutoScanStep.CLOSE_MOUNTS)
        screen == GameScreenKind.SKILLS || screen == GameScreenKind.PETS || screen == GameScreenKind.TECHNOLOGY -> Action.Close(AutoScanStep.CLOSE_RESEARCH)
        else -> Action.Unsupported
    }
}