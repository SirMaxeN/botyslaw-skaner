package eu.komars.forgescanner.battle
import eu.komars.forgescanner.model.WarResearchNodes
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.*
class WarResearchNodesTest {
 @Test fun adjacentBlueFlagNodesExcludeGreenResourceFlag() {
  val w=360;val h=780;val pixels=IntArray(w*h){0xffffffff.toInt()}
  fun node(cx:Int,green:Boolean=false,sword:Boolean=false,heart:Boolean=false) {
   for(y in 390..438)for(x in cx-24..cx+24)if((x-cx)*(x-cx)+(y-414)*(y-414)<24*24)pixels[y*w+x]=if(green)0xff80d880.toInt() else 0xff80c0ee.toInt()
   for(y in 397..401)for(x in listOf(cx-12,cx+12))pixels[y*w+x]=0xffffcc22.toInt()
   for(y in 400..419)for(x in cx-9..cx+9)pixels[y*w+x]=0xffef5055.toInt()
  }
  fun symbol(cx:Int,sword:Boolean) {
   for(y in 420..429)for(x in cx+2..cx+10)pixels[y*w+x]=if(sword)0xffdddddd.toInt() else 0xffef5055.toInt()
   if(sword)for(y in 430..433)for(x in cx+10..cx+14)pixels[y*w+x]=0xffffcc22.toInt()
  }
  node(80);symbol(80,true);node(160);symbol(160,false);node(240,true)
  val pair=WarResearchNodes.find(w,h,pixels)!!
  assertTrue(pair.first.left in 50..65);assertTrue(pair.second.left in 130..145)
  assertNull(WarResearchNodes.find(w,h,IntArray(w*h){0xffffffff.toInt()}))
 }
 @Test fun suppliedScreenshotProposesActualFlagIconsInsteadOfOldFixedCoordinates() {
  val path=System.getenv("WAR_RESEARCH_SCREEN");assumeTrue(path!=null)
  DataInputStream(BufferedInputStream(File(path!!).inputStream())).use {input ->
   val w=input.readInt();val h=input.readInt();val p=IntArray(w*h){input.readInt()}
   val pair=WarResearchNodes.find(w,h,p)
   assertNotNull("Both blue flag icons must be found in the user's screenshot",pair)
   val a=pair!!.first;val b=pair.second
   assertTrue("damage $a",(a.left+a.right)/2 in 55..95 && (a.top+a.bottom)/2 in 380..450)
   assertTrue("health $b",(b.left+b.right)/2 in 125..175 && (b.top+b.bottom)/2 in 380..450)
  }
 }
 @Test fun latestUserScreensDistinguishWarBannerFromPennant() {
  val dir=System.getenv("WAR_SCENE_FIXTURES");assumeTrue(dir!=null)
  for(index in 1..2) {
   val file=File(dir!!,"research-user-$index.argb");assumeTrue(file.exists())
   val pair=DataInputStream(BufferedInputStream(file.inputStream())).use {input ->
    val w=input.readInt();val h=input.readInt();val pixels=IntArray(w*h){input.readInt()}
    val original=WarResearchNodes.find(w,h,pixels)
    for(i in pixels.indices) {
     val c=pixels[i];val r=c ushr 16 and 255;val g=c ushr 8 and 255;val b=c and 255
     if(b>g+10 && g>r+15)pixels[i]=0xff000000.toInt() or (r shl 16) or (b shl 8) or r
    }
    val maxed=WarResearchNodes.find(w,h,pixels)
    if(index==2)assertNotNull("Maxed green war nodes must match",maxed)
    else assertNull("Green resource/pennant nodes must not match",maxed)
    original
   }
   if(index==1)assertNull("Pennants and potion banners are not war damage/health",pair)
   else {
    assertNotNull("Find actual war research on second screenshot",pair)
    assertTrue(pair!!.first.left in 40..65 && pair.first.top in 450..490)
    assertTrue(pair.second.left in 115..140)
   }
  }
 }
}
