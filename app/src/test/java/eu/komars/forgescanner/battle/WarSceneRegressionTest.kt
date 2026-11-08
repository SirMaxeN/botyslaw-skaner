package eu.komars.forgescanner.battle
import eu.komars.forgescanner.model.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.*
class WarSceneRegressionTest {
 data class Frame(val w:Int,val h:Int,val p:IntArray)
 private fun frame(second:Int):Frame {
  val dir=System.getenv("WAR_SCENE_FIXTURES");assumeTrue(dir!=null)
  return DataInputStream(BufferedInputStream(File(dir,"scene-$second.argb").inputStream())).use {input ->val w=input.readInt();val h=input.readInt();Frame(w,h,IntArray(w*h){input.readInt()})}
 }
 @Test fun warCannotBeHomeAndItsVisibleFinalCloseMustNotBeSkipped() {
  val f=frame(15)
  assertNotEquals(GameScreenKind.HOME,GameUiGeometryDetector(f.w,f.h,f.p).screenKind())
  assertFalse(BattleHomeProof.isHome(f.w,f.h,f.p))
  val observed=WarScreenGeometry.observe(f.w,f.h,f.p)
  assertNotNull(observed.footerClose);assertNotNull(observed.info)
  assertTrue("${observed.info}",observed.info!!.left>f.w*.9 && observed.info!!.top in 225..255)
 }
 @Test fun actualCharacterHomeRequiresEquipmentProofAndNoOpenCloseControl() {
  val f=frame(35)
  assertTrue("Actual home must remain recognized even with a black battle animation",BattleHomeProof.isHome(f.w,f.h,f.p))
  assertNull(CloseButtonPixelDetector().detect(f.w,f.h,f.p))
 }
 @Test fun bothClanWindowsShareViewportAndCloseAndAreNotHome() {
  val a=frame(18);val b=frame(24)
  val own=WarScreenGeometry.observe(a.w,a.h,a.p);val other=WarScreenGeometry.observe(b.w,b.h,b.p)
  assertNotNull("Own clan viewport",own.roster);assertNotNull("Enemy clan viewport",other.roster)
  assertNotNull(own.rosterClose);assertNotNull(other.rosterClose)
  assertTrue(kotlin.math.abs(own.roster!!.top-other.roster!!.top)<=4);assertTrue(kotlin.math.abs(own.roster!!.bottom-other.roster!!.bottom)<=4)
  assertNull(own.ok);assertNull(other.ok)
  assertFalse(BattleHomeProof.isHome(a.w,a.h,a.p));assertFalse(BattleHomeProof.isHome(b.w,b.h,b.p))
 }
 @Test fun bonusWindowSeparatesTextAndOkFromDimmedUnderlyingWarControls() {
  val f=frame(31);val s=WarScreenGeometry.observe(f.w,f.h,f.p)
  assertNotNull("Bonus text proposal",s.bonusText);assertNotNull("Actual blue OK",s.ok)
  assertTrue(s.bonusText!!.bottom<s.ok!!.top)
  assertTrue("${s.ok}",s.ok!!.left in 110..135 && s.ok!!.top in 395..425)
  assertNull(s.roster);assertNull(s.research);assertNull(s.info)
  assertFalse(BattleHomeProof.isHome(f.w,f.h,f.p))
 }
 @Test fun clanTreeHasVisibleNodeGridBeforeOpeningResearch() {
  val f=frame(9);assertTrue(WarScreenGeometry.observe(f.w,f.h,f.p).tree)
 }
 @Test fun latestRecordingScrolledRostersKeepViewportAndClose() {
  val dir=System.getenv("WAR_SCENE_FIXTURES");assumeTrue(dir!=null)
  for(second in listOf(20,25,30,35,45,50,55,60,65,70)) {
   val file=File(dir,"latest-$second.argb");assumeTrue(file.exists())
   val f=DataInputStream(BufferedInputStream(file.inputStream())).use {input ->val w=input.readInt();val h=input.readInt();Frame(w,h,IntArray(w*h){input.readInt()})}
   val s=WarScreenGeometry.observe(f.w,f.h,f.p)
   assertNotNull("Roster at $second seconds",s.roster)
   assertNotNull("Roster close at $second seconds",s.rosterClose)
   assertFalse("Roster must not become HOME at $second seconds",BattleHomeProof.isHome(f.w,f.h,f.p))
  }
 }
 @Test fun rosterDetectionToleratesDifferentImageSizesAndAspectRatios() {
  val source=frame(18)
  for(w in listOf(320,400)) for(ratio in listOf(1.8,2.0,2.3,2.5)) {
   val h=(w*ratio).toInt()
   // Preserve circular controls: a different screen aspect is not a stretched bitmap.
   val scale=minOf(w.toDouble()/source.w,h.toDouble()/source.h)
   val iw=(source.w*scale).toInt();val ih=(source.h*scale).toInt()
   val ox=(w-iw)/2;val oy=(h-ih)/2
   val pixels=IntArray(w*h){0xff000000.toInt()}
   for(y in 0 until ih)for(x in 0 until iw) pixels[(oy+y)*w+ox+x]=source.p[(y*source.h/ih)*source.w+x*source.w/iw]
   val observed=WarScreenGeometry.observe(w,h,pixels)
   assertNotNull("Roster ${w}x$h",observed.roster)
   assertNotNull("Close ${w}x$h",observed.rosterClose)
  }
 }
 @Test fun homeRemainsHomeWithIgnoredStatusCorner() {
  val f=frame(35)
  for(offset in listOf(0,25,40)) {
   val p=f.p.copyOf()
   for(y in offset until minOf(f.h,offset+30))for(x in f.w*70/100 until f.w)p[y*f.w+x]=0xff000000.toInt()
   assertTrue("Status corner at $offset",BattleHomeProof.isHome(f.w,f.h,p))
  }
 }
 @Test fun nativeAndroidChat99IsNotAModalClose() {
  val dir=System.getenv("WAR_SCENE_FIXTURES");assumeTrue(dir!=null && File(dir,"scene-99.argb").exists())
  val f=frame(99)
  assertNull("Chat notification is not X",CloseButtonPixelDetector().detect(f.w,f.h,f.p))
  assertTrue("Actual rejected Android frame must be HOME",BattleHomeProof.isHome(f.w,f.h,f.p))
 }
}
