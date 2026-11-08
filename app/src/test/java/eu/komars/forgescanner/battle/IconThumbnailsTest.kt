package eu.komars.forgescanner.battle
import org.junit.Assert.*
import org.junit.Test

class IconThumbnailsTest {
 private fun thumb(color:Int,noise:Int=0)=(0 until 256).joinToString("") {String.format(java.util.Locale.ROOT,"%06x",(color+(it*noise)%9) and 0xffffff)}
 @Test fun learnedInGameIconIsRecognisedOnlyWhenClearlyClosest() {
  val learned=listOf("METEORITE" to thumb(0x803020),"BOMB" to thumb(0x203080))
  assertEquals("METEORITE",IconThumbnails.recognize(thumb(0x803020,1),learned))
  assertNull(IconThumbnails.recognize(thumb(0x505050),learned))
  assertNull(IconThumbnails.recognize(thumb(0x803020),listOf("A" to thumb(0x803020),"B" to thumb(0x803021))))
  assertNull(IconThumbnails.recognize("broken",learned))
 }

 /** A disk icon drawn at an offset inside a 40×40 tile on the panel colour. */
 private fun disk(color:Int,dx:Int=0,dy:Int=0,mark:Int?=null)=(0 until 40*40).joinToString("") {
  val x=it%40-20-dx;val y=it/40-20-dy
  val c=when {mark!=null&&x in -9..9&&y in -12..6->mark;x*x+y*y<=16*16->color;else->0x3d4256}
  String.format(java.util.Locale.ROOT,"%06x",c)
 }
 @Test fun shiftedCopiesOfTheSameIconMatchButOtherArtworkDoesNot() {
  val bomb=disk(0xf0e040,mark=0x404040);val meteor=disk(0xf0e040,mark=0xa04020)
  assertTrue(IconThumbnails.iconDistance(bomb,disk(0xf0e040,3,-2,0x404040))!!<5.0)
  assertTrue(IconThumbnails.iconDistance(bomb,meteor)!!>5.0)
  val learned=listOf("BOMB" to bomb,"STAR" to disk(0x2050c0))
  assertEquals("BOMB",IconThumbnails.recognizeIcon(disk(0xf0e040,-3,2,0x404040),learned))
  assertNull(IconThumbnails.recognizeIcon(disk(0xff0000),learned))
  assertNull(IconThumbnails.iconDistance("short",bomb))
 }
 @Test fun tightBoxFindsTheIconInsideAShiftedSlot() {
  val w=60;val h=60;val bg=0x3d4256
  val px=IntArray(w*h){val x=it%w;val y=it/w;if((x-38)*(x-38)+(y-26)*(y-26)<=18*18)0xf0e040 else bg}
  val box=IconThumbnails.tightBox(w,h,px,bg)!!
  assertTrue(box.joinToString(),box[0] in 21..27&&box[1] in 9..15)
  assertNull(IconThumbnails.tightBox(w,h,IntArray(w*h){bg},bg))
 }
}
