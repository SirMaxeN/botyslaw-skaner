package eu.komars.forgescanner.automation

import eu.komars.forgescanner.battle.ResearchScrollGesture
import org.junit.Assert.*
import org.junit.Test

class ResearchScrollGestureTest {
 @Test fun centralResearchStrokesPassActualGestureSafetyOnDifferentPhones() {
  for((w,h) in listOf(1080 to 2340,720 to 1280,1440 to 3200,1080 to 1920)) {
   val safety=GestureSafety();val session=safety.arm(w,h,0)
   for(toTop in listOf(true,false)) {
    val s=ResearchScrollGesture.create(w,h,toTop)
    safety.drag(session,s.x,s.fromY,s.x,s.toY,s.durationMs,1)
    assertEquals(w*.5f,s.x,.01f)
    assertEquals(h*.5f,(s.fromY+s.toY)/2,.01f)
    assertEquals(toTop,s.toY>s.fromY)
    assertTrue(kotlin.math.abs(s.toY-s.fromY)<=h*(if(toTop).33f else .29f))
   }
  }
 }
}
