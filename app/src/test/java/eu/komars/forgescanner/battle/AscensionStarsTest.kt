package eu.komars.forgescanner.battle
import org.junit.Assert.*
import org.junit.Test
class AscensionStarsTest {
 private fun band(w:Int,h:Int,stars:Int,size:Int=20):IntArray {
  val px=IntArray(w*h){0x3d4256}
  for(s in 0 until stars)for(y in 10 until 10+size)for(x in 10+s*(size*6/10) until 10+s*(size*6/10)+size)px[y*w+x]=0xffc828
  return px
 }
 @Test fun countsOverlappingStarsByWidth() {
  for(n in 1..3)assertEquals(n,AscensionStars.count(100,50,band(100,50,n),3000))
  assertEquals(0,AscensionStars.count(100,50,band(100,50,0),3000))
 }
 @Test fun yellowArtworkBelowThresholdIsNotAStar() {
  assertEquals(0,AscensionStars.count(100,50,band(100,50,1,size=8),5184))
 }
 @Test fun skillAscensionNeedsAgreementAndMultipliesSkills() {
  assertEquals(2,AscensionStars.agreed(listOf(2,2,2)))
  assertNull(AscensionStars.agreed(listOf(1,0,0)))
  assertEquals(50.0,AscensionStars.skillMultiplier(1),0.0)
  assertEquals(2500.0,AscensionStars.skillMultiplier(2),0.0)
  assertEquals(125000.0,AscensionStars.skillMultiplier(3),0.0)
  assertEquals(1.0,AscensionStars.skillMultiplier(0),0.0)
 }
}
