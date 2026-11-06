package eu.komars.forgescanner.battle
import eu.komars.forgescanner.model.PixelRegion
import org.junit.Assert.*
import org.junit.Test
class EquipmentSlotPresenceTest {
 @Test fun emptyRequiresTheActualProfileBackground() {
  val bg=0xff44495c.toInt();val q=PixelRegion(0,0,32,32)
  assertTrue(EquipmentSlotPresence.isEmpty(32,32,IntArray(1024){bg},q,bg))
  assertFalse(EquipmentSlotPresence.isEmpty(32,32,IntArray(1024){0xff55bb33.toInt()},q,bg))
  val icon=IntArray(1024){bg};for(y in 8..23)for(x in 8..23)icon[y*32+x]=0xffffdd33.toInt()
  assertFalse(EquipmentSlotPresence.isEmpty(32,32,icon,q,bg))
 }
 @Test fun missingOrInvalidPixelsAreNotProofOfAbsence() {
  assertFalse(EquipmentSlotPresence.isEmpty(32,32,IntArray(0),PixelRegion(0,0,32,32),0))
  assertFalse(EquipmentSlotPresence.isEmpty(32,32,IntArray(1024),PixelRegion(-1,0,32,32),0))
 }
}
