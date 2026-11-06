package eu.komars.forgescanner.automation

import eu.komars.forgescanner.model.GameNavigationGraph as Graph
import eu.komars.forgescanner.battle.ResearchScrollGesture
import org.junit.Assert.*
import org.junit.Test

class GameNavigationGraphTest {
    @Test fun everyNamedActionHasAnExplicitDestination() {
        assertEquals(Graph.Action.entries.toSet(),Graph.routes.keys)
        assertTrue(Graph.routes.values.all {it.from.isNotEmpty() && it.to!=Graph.Screen.UNKNOWN})
    }
    @Test fun resourceRouteReturnsHomeWithoutRepeatingRecognition() {
        val cursor=Graph.Cursor();cursor.observe(Graph.Screen.HOME)
        AutoScanStep.entries.filter {it.isNavigation}.forEach {step ->
            val action=requireNotNull(Graph.resourceAction(step.name))
            assertTrue("$step from ${cursor.screen}",cursor.allows(action));cursor.completed(action)
        }
        assertEquals(Graph.Screen.HOME,cursor.screen)
    }
    @Test fun nestedResearchAndRosterClosuresReturnToTheirParentNotHome() {
        val c=Graph.Cursor();c.observe(Graph.Screen.HOME)
        for(key in listOf("science","technology","clanTechnologyCategory","damageNode","researchClose","healthNode","researchClose")) {
            val action=requireNotNull(Graph.warAction(key));assertTrue(c.allows(action));c.completed(action)
        }
        assertEquals(Graph.Screen.TREE,c.screen)
        c.completed(Graph.Action.CLOSE_RESEARCH);assertEquals(Graph.Screen.HOME,c.screen)
        for(key in listOf("clan","warTab","ourFlag","rosterClose","enemyFlag","rosterClose","rivalInfo","infoOk")) {
            val action=requireNotNull(Graph.warAction(key));assertTrue(c.allows(action));c.completed(action)
        }
        assertEquals(Graph.Screen.WAR,c.screen)
        c.completed(Graph.Action.CLOSE_CLAN);assertEquals(Graph.Screen.HOME,c.screen)
    }
    @Test fun flagsAndMvpAreDifferentControlsRegardlessOfWarDay() {
        for(key in listOf("ourFlag","enemyFlag")) {
            val c=Graph.Cursor();c.observe(Graph.Screen.WAR)
            c.completed(requireNotNull(Graph.warAction(key)))
            assertEquals(Graph.Screen.ROSTER,c.screen)
            c.completed(requireNotNull(Graph.warAction("rosterClose")))
            c.completed(requireNotNull(Graph.warAction(if(key=="ourFlag")"ourMvp" else "enemyMvp")))
            assertEquals(Graph.Screen.WAR_RANKING,c.screen)
            assertFalse(c.allows(Graph.Action.CLOSE_ROSTER))
            c.completed(requireNotNull(Graph.warAction("rankingClose")))
            assertEquals(Graph.Screen.WAR,c.screen)
        }
    }
    @Test fun unexpectedClicksAreRejectedAndResetMakesRecoveryExplicit() {
        val c=Graph.Cursor();c.observe(Graph.Screen.SKILLS)
        assertFalse(c.allows(Graph.Action.OPEN_CLAN))
        assertEquals(Graph.Screen.SKILLS,c.screen)
        c.reset();assertEquals(Graph.Screen.UNKNOWN,c.screen)
        c.observe(Graph.Screen.HOME);assertTrue(c.allows(Graph.Action.OPEN_CLAN))
    }
    @Test fun scrollReplayRejectsOtherPhonesChangedGestureVersionsAndUnboundedCounts() {
        assertEquals(4,ResearchScrollGesture.replaySteps(1,1080,2340,4,1080,2340))
        assertEquals(0,ResearchScrollGesture.replaySteps(1,1080,2340,0,1080,2340))
        assertNull(ResearchScrollGesture.replaySteps(0,1080,2340,4,1080,2340))
        assertNull(ResearchScrollGesture.replaySteps(1,1080,2340,4,720,1280))
        assertNull(ResearchScrollGesture.replaySteps(1,1080,2340,13,1080,2340))
        assertNull(ResearchScrollGesture.replaySteps(1,1080,2340,-1,1080,2340))
    }
}
