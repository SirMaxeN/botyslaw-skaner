package eu.komars.forgescanner.battle

import eu.komars.forgescanner.model.PixelRegion

/** Empty requires positive evidence of the known profile background, never merely an OCR failure. */
object EquipmentSlotPresence {
 fun isEmpty(w:Int,h:Int,pixels:IntArray,slot:PixelRegion,background:Int):Boolean {
  if(w*h!=pixels.size || slot.left<0 || slot.top<0 || slot.right>w || slot.bottom>h || slot.width<8 || slot.height<8)return false
  var backgroundPoints=0
  for(y in 0 until 16)for(x in 0 until 16) {
   val c=pixels[(slot.top+(y+.5)*slot.height/16).toInt()*w+(slot.left+(x+.5)*slot.width/16).toInt()]
   val difference=(0..2).maxOf {shift->kotlin.math.abs((c ushr (8*shift) and 255)-(background ushr (8*shift) and 255))}
   if(difference<=10)backgroundPoints++
  }
  return backgroundPoints>=254
 }
}
