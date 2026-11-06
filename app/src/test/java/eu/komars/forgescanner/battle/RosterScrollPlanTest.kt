package eu.komars.forgescanner.battle
import org.junit.Assert.*
import org.junit.Test
class RosterScrollPlanTest {
 @Test fun sixRowsReserveExtraRoomForInertiaAndKeepTwoCompleteAnchors() {
  val y=listOf(75,247,419,591,763,935);val distance=RosterScrollPlan.down(1209,2340,y)
  assertTrue(distance>=172*3);assertTrue(distance<=172*3.5)
  assertTrue(y.count {it>=y.first()+distance}>=2)
 }
 @Test fun fiveRowsKeepAReserveBeforeTheirLastTwoPeople() {
  val y=listOf(80,252,424,596,768);val distance=RosterScrollPlan.down(1000,2340,y)
  assertTrue(distance>=172*2);assertTrue(y.count {it>=y.first()+distance}>=2)
 }
 @Test fun allGesturesRespectTheSharedSafetyLimitAtDifferentScreenProportions() {
  for(screen in listOf(1280,1920,2340,3200))for(fraction in listOf(.4,.55,.7)) {
   val viewport=(screen*fraction).toInt();val rows=(0..5).map {(viewport*(.05+it*.15)).toInt()}
   assertTrue(RosterScrollPlan.down(viewport,screen,rows)<=screen*.35f)
   assertTrue(RosterScrollPlan.rewind(viewport,screen)<=screen*.35f)
   assertTrue(RosterScrollPlan.rewind(viewport,screen)<=viewport*.8f)
  }
 }
 @Test fun recoveringAnOvershootMovesBackAboutOneRowNotToTheStart() {
  val distance=RosterScrollPlan.backtrack(1209,2340,listOf(70,242,414,586,758,930,1102))
  assertTrue(distance>=172);assertTrue(distance<172*1.3)
 }
}
