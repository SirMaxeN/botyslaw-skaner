package eu.komars.forgescanner.battle
import android.graphics.Bitmap
import eu.komars.forgescanner.model.*
object BattleHomeProof {
 fun isHome(w:Int,h:Int,p:IntArray):Boolean {
  if(CloseButtonPixelDetector().detect(w,h,p)!=null)return false
  val scene=BattleSetupGeometry(w,h,p).detect(requireEquipment=true)
  return !scene.profile && scene.panel==null && scene.regions.containsKey("open")
 }
 fun isHome(image:Bitmap):Boolean {
  for(w in listOf(360,480))for(filtered in listOf(true,false)) {
   val width=minOf(w,image.width);val height=image.height*width/image.width
   val small=Bitmap.createScaledBitmap(image,width,height,filtered)
   val result=try {val p=IntArray(width*height);small.getPixels(p,0,width,0,0,width,height);isHome(width,height,p)}finally{if(small!==image)small.recycle()}
   if(result)return true
  }
  return false
 }
 fun diagnostic(image:Bitmap):String=buildString {
  for(w in listOf(360,480))for(filtered in listOf(true,false)) {
   val width=minOf(w,image.width);val height=image.height*width/image.width;val small=Bitmap.createScaledBitmap(image,width,height,filtered)
   val p=IntArray(width*height);small.getPixels(p,0,width,0,0,width,height)
   appendLine("$width x $height filter=$filtered home=${isHome(width,height,p)} close=${CloseButtonPixelDetector().detect(width,height,p)} scene=${BattleSetupGeometry(width,height,p).detect(true)}")
   if(small!==image)small.recycle()
  }
 }
}
