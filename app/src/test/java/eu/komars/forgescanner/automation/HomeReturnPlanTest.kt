package eu.komars.forgescanner.automation

import eu.komars.forgescanner.model.GameScreenKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class HomeReturnPlanTest {
    @Test fun homeStartsImmediatelyWithoutAnyClick() {
        assertEquals(HomeReturnPlan.Action.Ready, HomeReturnPlan.next(GameScreenKind.HOME))
        assertEquals(HomeReturnPlan.Action.Ready, HomeReturnPlan.next(GameScreenKind.HOME, closeAlreadyAttempted = true))
    }

    @Test fun eachKnownInventoryUsesItsOwnSavedCloseButton() {
        assertEquals(HomeReturnPlan.Action.Close(AutoScanStep.CLOSE_MOUNTS), HomeReturnPlan.next(GameScreenKind.MOUNTS))
        for (screen in listOf(GameScreenKind.SKILLS, GameScreenKind.PETS, GameScreenKind.TECHNOLOGY)) {
            assertEquals(HomeReturnPlan.Action.Close(AutoScanStep.CLOSE_RESEARCH), HomeReturnPlan.next(screen))
        }
    }

    @Test fun unknownScreenNeverGetsABlindCloseClick() {
        assertEquals(HomeReturnPlan.Action.Unsupported, HomeReturnPlan.next(GameScreenKind.UNKNOWN))
        assertEquals(HomeReturnPlan.Action.Unsupported, HomeReturnPlan.next(GameScreenKind.UNKNOWN, closeAlreadyAttempted = true))
    }

    @Test fun failedCloseCannotTriggerAnotherTapEvenIfTheOldScreenIsStillVisible() {
        for (screen in GameScreenKind.entries.filter { it != GameScreenKind.HOME }) {
            assertEquals(HomeReturnPlan.Action.Unsupported, HomeReturnPlan.next(screen, closeAlreadyAttempted = true))
        }
    }

    @Test fun homePreparationCannotOpenTabsOrPerformOtherNavigation() {
        for (step in AutoScanStep.entries.filter { it != AutoScanStep.CLOSE_MOUNTS && it != AutoScanStep.CLOSE_RESEARCH }) {
            assertThrows(IllegalArgumentException::class.java) { HomeReturnPlan.Action.Close(step) }
        }
    }
}