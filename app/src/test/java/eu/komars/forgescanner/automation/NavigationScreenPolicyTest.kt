package eu.komars.forgescanner.automation

import eu.komars.forgescanner.model.GameScreenKind
import org.junit.Assert.*
import org.junit.Test

class NavigationScreenPolicyTest {
    @Test fun openingResearchAcceptsAllThreeSelectedTabs() {
        for (screen in listOf(GameScreenKind.SKILLS, GameScreenKind.PETS, GameScreenKind.TECHNOLOGY)) {
            assertTrue(NavigationScreenPolicy.accepts(AutoScanStep.OPEN_RESEARCH, screen))
        }
        assertFalse(NavigationScreenPolicy.accepts(AutoScanStep.OPEN_RESEARCH, GameScreenKind.MOUNTS))
    }
    @Test fun changedGraphicsOrMissingCloseButtonCannotProveAnUnknownDestination() {
        for (step in AutoScanStep.entries) assertFalse(NavigationScreenPolicy.accepts(step, GameScreenKind.UNKNOWN))
        for (step in listOf(AutoScanStep.CLOSE_MOUNTS, AutoScanStep.CLOSE_RESEARCH)) {
            assertTrue(NavigationScreenPolicy.accepts(step, GameScreenKind.HOME))
            for (screen in GameScreenKind.entries.filter { it != GameScreenKind.HOME }) {
                assertFalse(NavigationScreenPolicy.accepts(step, screen))
            }
        }
    }
    @Test fun selectingATabNeedsTheSelectedTabRatherThanGenericResearchChrome() {
        assertTrue(NavigationScreenPolicy.accepts(AutoScanStep.OPEN_SKILLS, GameScreenKind.SKILLS))
        assertFalse(NavigationScreenPolicy.accepts(AutoScanStep.OPEN_SKILLS, GameScreenKind.PETS))
        assertFalse(NavigationScreenPolicy.accepts(AutoScanStep.OPEN_PETS, GameScreenKind.TECHNOLOGY))
        assertTrue(NavigationScreenPolicy.accepts(AutoScanStep.OPEN_PETS, GameScreenKind.PETS))
    }
}
