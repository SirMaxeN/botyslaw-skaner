package eu.komars.forgescanner.battle
import eu.komars.forgescanner.model.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.*
class WarNavigationGeometryTest {
 @Test fun bannerPairAndClanEntryExcludeHeaderAndNotificationDots() {
  val w=360;val h=780;val p=IntArray(w*h){0xffcccccc.toInt()}
  fun fill(l:Int,t:Int,r:Int,b:Int,c:Int){for(y in t until b)for(x in l until r)p[y*w+x]=c}
  fill(180,0,360,200,0xffcf6666.toInt())
  fill(20,100,54,143,0xffff5515.toInt());fill(306,100,340,143,0xffff8815.toInt())
  fill(245,720,280,760,0xffff5515.toInt());fill(280,700,286,706,0xffff5515.toInt())
  val pair=WarNavigationGeometry.flags(w,h,p)!!
  assertEquals(20,pair.first.left);assertEquals(306,pair.second.left)
  assertEquals(245,WarNavigationGeometry.clanEntry(w,h,p)!!.left)
  assertNull(WarNavigationGeometry.flags(w,h,IntArray(w*h){0xffcf6666.toInt()}))
 }
 @Test fun clanBannerColoursMayChangeIndependently() {
  for(a in listOf(0xff33bb55.toInt(),0xff3355ee.toInt(),0xffbb33ee.toInt(),0xffeedd22.toInt())) {
   val w=360;val h=780;val p=IntArray(w*h){0xffcccccc.toInt()}
   for(y in 100..142)for(x in 20..53)p[y*w+x]=a
   for(y in 100..142)for(x in 306..339)p[y*w+x]=0xff22bbbb.toInt()
   assertNotNull("Banner colour ${a.toUInt()}",WarNavigationGeometry.flags(w,h,p))
  }
 }
 @Test fun warTextUsesTheRecognizedFooterLabelAndRejectsAmbiguity() {
  val words=listOf(BattleOcrLine("Wojna",100,60,150,80),BattleOcrLine("wojny",10,150,70,175),BattleOcrLine("Wojna",280,672,325,694))
  val found=WarTabTextProposal.find(360,780,words)!!
  assertTrue(found.top<672 && found.bottom>694);assertTrue((found.top+found.bottom)/2<700)
  assertNull(WarTabTextProposal.find(360,780,words+BattleOcrLine("War",270,710,320,735)))
 }
 @Test fun userScreenshotFindsBothActualBannerBodiesInsteadOfOldAboveBannerCoordinates() {
  val path=System.getenv("WAR_FLAGS_SCREEN");assumeTrue(path!=null)
  DataInputStream(BufferedInputStream(File(path!!).inputStream())).use {input ->
   val w=input.readInt();val h=input.readInt();val p=IntArray(w*h){input.readInt()}
   val pair=WarNavigationGeometry.flags(w,h,p)
   assertNotNull("Actual left and right flags must be detected",pair)
   val a=pair!!.first;val b=pair.second
   assertTrue("$a",(a.left+a.right)/2 in 20..60 && (a.top+a.bottom)/2 in 100..155)
   assertTrue("$b",(b.left+b.right)/2 in 300..345 && (b.top+b.bottom)/2 in 100..155)
  }
 }
 @Test fun mvpAvatarsBelowFlagsAreNotAlternativeClanEntrances() {
  val w=360;val h=780;val p=IntArray(w*h){0xffcccccc.toInt()}
  fun fill(l:Int,t:Int,r:Int,b:Int,c:Int){for(y in t until b)for(x in l until r)p[y*w+x]=c}
  fill(18,190,49,225,0xff22dd33.toInt());fill(311,190,342,225,0xff3344ee.toInt())
  assertNull("MVP portraits alone must never be proposed as flags",WarNavigationGeometry.flags(w,h,p))
  fill(20,100,54,143,0xffff5515.toInt());fill(306,100,340,143,0xff22bbbb.toInt())
  val flags=WarNavigationGeometry.flags(w,h,p)!!
  assertTrue(flags.first.bottom<190 && flags.second.bottom<190)
 }
}
