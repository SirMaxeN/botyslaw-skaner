package eu.komars.forgescanner.battle
import eu.komars.forgescanner.model.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.*
class HomeChatRegressionTest {
 @Test fun actualCurrencyIconCannotRejectHomeInAnyModule() {
  val dir=System.getenv("ROSTER_ROW_FIXTURES")
  val file=dir?.let {File(it,"home-gem-false-close-360.argb")}
  assumeTrue(file?.isFile==true)
  DataInputStream(file!!.inputStream().buffered()).use { f->
   val w=f.readInt();val h=f.readInt();val pixels=IntArray(w*h){f.readInt()}
   assertNotNull("Reproduce the old false close candidate",CloseButtonPixelDetector().detect(w,h,pixels))
   val geometry=GameUiGeometryDetector(w,h,pixels)
   assertEquals(GameScreenKind.HOME,geometry.screenKind())
   assertNotNull(geometry.detect(eu.komars.forgescanner.automation.AutoScanStep.OPEN_RESEARCH))
   assertTrue(BattleHomeProof.isHome(w,h,pixels))
   assertNull(WarScreenGeometry.observe(w,h,pixels).footerClose)
  }
 }
 @Test fun actualHomeWith29ChatBadgeCannotBecomeACloseWindowSetup() {
  val dir=System.getenv("ROSTER_ROW_FIXTURES");assumeTrue(dir!=null)
  for(size in listOf(360,480))DataInputStream(BufferedInputStream(File(dir,"home-chat-29-$size.argb").inputStream())).use {f ->
   val w=f.readInt();val h=f.readInt();val p=IntArray(w*h){f.readInt()}
   val proof=BattleHomeProof.isHome(w,h,p);val setup=BattleSetupGeometry(w,h,p).detect(true);val war=WarScreenGeometry.observe(w,h,p)
   assertTrue("$w: close=${CloseButtonPixelDetector().detect(w,h,p)} setup=$setup war=$war",proof)
   assertNull("Home is not an open research dialog",war.research)
  }
 }
}
