package eu.komars.forgescanner.battle
import org.junit.Assert.*
import org.junit.Test
class ResearchBrakePointTest {
 @Test fun neverPicksColoredControlsOrTextInsideMargin() {
  val w=360;val h=780;val pixels=IntArray(w*h){0xff44495c.toInt()}
  assertNull(ResearchBrakePoint.find(w,h,pixels))
  for(y in 400..450)for(x in 1..17)pixels[y*w+x]=-1
  val point=ResearchBrakePoint.find(w,h,pixels)!!
  assertEquals(9,point.first)
  pixels[point.second*w+point.first]=0xff000000.toInt()
  assertNull(ResearchBrakePoint.find(w,h,pixels))
 }
}
