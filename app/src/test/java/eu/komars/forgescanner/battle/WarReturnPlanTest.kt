package eu.komars.forgescanner.battle
import eu.komars.forgescanner.model.PixelRegion
import org.junit.Assert.*
import org.junit.Test
class WarReturnPlanTest {
 @Test fun homeOverridesAnEarlierCloseProposal() {assertEquals(WarReturnPlan.Step.Home,WarReturnPlan.decide(setOf("home"),setOf("mainClose")))}
 @Test fun aNotificationCandidateOnAnUnknownScreenCannotOpenCloseCalibration() {assertEquals(WarReturnPlan.Step.Wait,WarReturnPlan.decide(emptySet(),setOf("mainClose")))}
 @Test fun actualKnownModalUsesItsOwnFreshClose() {assertEquals(WarReturnPlan.Step.Close("rosterClose"),WarReturnPlan.decide(setOf("roster"),setOf("mainClose","rosterClose")))}
 @Test fun headerCountCropCannotReachTheChatBanner() {
  for(w in listOf(360,480,1080)) {val h=w*13/6;val list=PixelRegion(w/10,h*28/100,w*9/10,h*80/100)
   val crop=RosterHeaderRegion.above(w,h,list)!!
   assertEquals(list.top,crop.bottom);assertTrue(crop.top<crop.bottom);assertTrue(crop.bottom<h*.7)
   assertNull(RosterHeaderRegion.above(w,h,PixelRegion(0,h*80/100,w,h)))
  }
 }
}
