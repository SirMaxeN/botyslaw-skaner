package eu.komars.forgescanner.battle
import eu.komars.forgescanner.model.PixelRegion
/** The member count is above the configured list. Bottom banners and chat are outside this crop. */
internal object RosterHeaderRegion {
 fun above(w:Int,h:Int,list:PixelRegion):PixelRegion? {
  if(list.left<0||list.right>w||list.top<=0||list.bottom>h||list.width<w*.4||list.top>h*.70||list.height<w*.5)return null
  val top=maxOf(0,list.top-(list.width*.38).toInt())
  return PixelRegion(list.left+(list.width*.20).toInt(),top,list.right,list.top)
 }
}
