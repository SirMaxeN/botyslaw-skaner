package eu.komars.forgescanner.automation

import eu.komars.forgescanner.model.GameScreenKind
import org.junit.Assert.*
import org.junit.Test

class ResourceSetupScreensTest {
    @Test fun homeCollectsBothEntrancesAndHammersWithoutOpeningAnything() {
        val fields=ResourceSetupScreens.forStep(AutoScanStep.HAMMERS).fields
        assertEquals(listOf(AutoScanStep.HAMMERS,AutoScanStep.COINS,AutoScanStep.GEMS,AutoScanStep.OPEN_MOUNTS,AutoScanStep.OPEN_RESEARCH),fields)
        val flow=CalibrationFlow();flow.beginAll();flow.approve(fields)
        assertEquals(AutoScanStep.HAMMERS,flow.current)
        assertEquals(AutoScanStep.OPEN_MOUNTS,flow.advance())
    }
    @Test fun mountCloseBelongsWithCountersAndListButMustStillBeExecuted() {
        val flow=CalibrationFlow();flow.beginAll()
        flow.approve(ResourceSetupScreens.forStep(AutoScanStep.HAMMERS).fields)
        assertEquals(AutoScanStep.OPEN_MOUNTS,flow.advance())
        assertEquals(AutoScanStep.MOUNT_KEYS,flow.advance())
        val fields=ResourceSetupScreens.forStep(AutoScanStep.MOUNT_KEYS).fields
        assertEquals(listOf(AutoScanStep.MOUNT_KEYS,AutoScanStep.MOUNT_COST,AutoScanStep.MOUNT_LIST,AutoScanStep.CLOSE_MOUNTS),fields)
        flow.approve(fields)
        assertEquals(AutoScanStep.CLOSE_MOUNTS,flow.advance())
        assertEquals(AutoScanStep.OPEN_RESEARCH,flow.advance())
    }
    @Test fun firstResearchStepAlwaysConfiguresOnlyTabsRegardlessOfRememberedScreen() {
        for(visible in GameScreenKind.entries) {
            assertEquals(listOf(AutoScanStep.OPEN_SKILLS,AutoScanStep.OPEN_PETS,AutoScanStep.OPEN_TECH),ResourceSetupScreens.forStep(AutoScanStep.OPEN_SKILLS,visible).fields)
        }
        assertEquals(listOf(AutoScanStep.SKILL_TICKETS,AutoScanStep.SKILL_COST),ResourceSetupScreens.forStep(AutoScanStep.SKILL_TICKETS).fields)
        assertEquals(listOf(AutoScanStep.SHELLS,AutoScanStep.PET_LIST),ResourceSetupScreens.forStep(AutoScanStep.PET_LIST).fields)
        assertEquals(listOf(AutoScanStep.RED_VIALS,AutoScanStep.GREEN_VIALS),ResourceSetupScreens.forStep(AutoScanStep.GREEN_VIALS).fields)
    }
    @Test fun approvedFieldsDoNotLeakIntoNewSetupOrRepair() {
        val flow=CalibrationFlow();flow.beginAll();flow.approve(AutoScanStep.entries)
        flow.beginRepair(AutoScanStep.MOUNT_COST)
        assertFalse(flow.isApproved(AutoScanStep.MOUNT_COST))
        flow.approve(ResourceSetupScreens.forStep(AutoScanStep.MOUNT_COST).fields)
        assertNull(flow.advance())
        flow.beginAll()
        assertTrue(AutoScanStep.entries.none(flow::isApproved))
    }
    @Test fun fullSetupNeedsOneSelectionPerVisibleScreenAndPreservesNavigationOrder() {
        for(initialResearch in listOf(GameScreenKind.SKILLS,GameScreenKind.PETS,GameScreenKind.TECHNOLOGY)) {
            val flow=CalibrationFlow();flow.beginAll()
            var visible=GameScreenKind.HOME
            val selections=mutableListOf<String>();val actions=mutableListOf<AutoScanStep>()
            var count=0
            while(flow.current!=null) {
                check(count++<30)
                val step=flow.current!!
                if(!flow.isApproved(step) && step!=AutoScanStep.CLOSE_RESEARCH) {
                    val screen=ResourceSetupScreens.forStep(step,visible)
                    selections+=screen.title;flow.approve(screen.fields)
                }
                if(step.isNavigation) {
                    actions+=step
                    visible=when(step) {
                        AutoScanStep.OPEN_MOUNTS->GameScreenKind.MOUNTS
                        AutoScanStep.CLOSE_MOUNTS,AutoScanStep.CLOSE_RESEARCH->GameScreenKind.HOME
                        AutoScanStep.OPEN_RESEARCH->initialResearch
                        AutoScanStep.OPEN_SKILLS->GameScreenKind.SKILLS
                        AutoScanStep.OPEN_PETS->GameScreenKind.PETS
                        AutoScanStep.OPEN_TECH->GameScreenKind.TECHNOLOGY
                        else->error("unexpected route")
                    }
                }
                flow.advance(step)
            }
            assertEquals(AutoScanStep.entries.filter {it.isNavigation},actions)
            assertEquals(6,selections.size) // + the tech tree screen with the vial counters
            assertEquals(selections.distinct(),selections)
            assertEquals(GameScreenKind.HOME,visible)
        }
    }
}
