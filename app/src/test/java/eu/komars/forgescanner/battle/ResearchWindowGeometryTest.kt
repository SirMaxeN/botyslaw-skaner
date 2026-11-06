package eu.komars.forgescanner.battle
import eu.komars.forgescanner.model.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.*
class ResearchWindowGeometryTest {
 @Test fun descriptionMovesWithTheDialogAndExcludesLowerDonationControls() {
  val w=360;val h=780
  for(top in listOf(220,280)) {
   val p=IntArray(w*h){0xff555555.toInt()}
   for(y in top until top+300)for(x in 25..335)p[y*w+x]=0xffffffff.toInt()
   val panel=ResearchWindowGeometry.find(w,h,p)!!;val text=ResearchWindowGeometry.description(panel)
   assertEquals(top,panel.top);assertTrue(text.top>=top);assertTrue(text.bottom<top+120)
   assertTrue(text.left>panel.left && text.right<panel.right)
  }
  assertNull(ResearchWindowGeometry.find(w,h,IntArray(w*h){0xff555555.toInt()}))
 }
 @Test fun suppliedPopupScreenshotSelectsResearchDescriptionAndItsOwnCloseInsteadOfBottomMenu() {
  val path=System.getenv("WAR_RESEARCH_WINDOW");assumeTrue(path!=null)
  DataInputStream(BufferedInputStream(File(path!!).inputStream())).use {input ->
   val w=input.readInt();val h=input.readInt();val p=IntArray(w*h){input.readInt()}
   val panel=ResearchWindowGeometry.find(w,h,p)
   assertNotNull("Research popup must be detected",panel)
   assertTrue("$panel",panel!!.top in 240..280 && panel.bottom in 530..580)
   val text=ResearchWindowGeometry.description(panel)
   assertTrue(text.top in 250..300 && text.bottom in 320..370)
   val close=CloseButtonPixelDetector().detect(w,h,p,PixelRegion(w/2-27,panel.bottom-27,w/2+27,panel.bottom+27))
   assertNotNull("The popup X must be found despite the separate main-menu X",close)
   assertTrue("$close",(close!!.top+close.bottom)/2 in 530..580)
  }
 }
}
