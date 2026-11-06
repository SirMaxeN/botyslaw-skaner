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
}
