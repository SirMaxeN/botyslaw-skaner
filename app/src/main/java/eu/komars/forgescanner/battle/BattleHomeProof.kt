package eu.komars.forgescanner.battle
import android.graphics.Bitmap
import eu.komars.forgescanner.model.*
object BattleHomeProof {
 fun isHome(w:Int,h:Int,p:IntArray):Boolean = GameUiGeometryDetector(w,h,p).screenKind()==GameScreenKind.HOME
 fun isHome(image:Bitmap):Boolean = eu.komars.forgescanner.vision.GameUiDetector().screenKind(image)==GameScreenKind.HOME
 fun diagnostic(image:Bitmap):String=buildString {
  for(w in listOf(360,480))for(filtered in listOf(true,false)) {
   val width=minOf(w,image.width);val height=image.height*width/image.width;val small=Bitmap.createScaledBitmap(image,width,height,filtered)
   val p=IntArray(width*height);small.getPixels(p,0,width,0,0,width,height)
   appendLine("$width x $height filter=$filtered home=${isHome(width,height,p)} close=${CloseButtonPixelDetector().detectGameplay(width,height,p)} scene=${BattleSetupGeometry(width,height,p).detect(true)}")
   if(small!==image)small.recycle()
  }
 }
}
