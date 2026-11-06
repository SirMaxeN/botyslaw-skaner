package eu.komars.forgescanner.battle
import eu.komars.forgescanner.model.PixelRegion

/** A close control observed on this member's unobscured profile, not another modal's X. */
internal data class MemberProfileAnchor(val member:String,val width:Int,val height:Int,val close:PixelRegion) {
 fun closeFor(current:String,w:Int,h:Int,returnedToProfile:Boolean):PixelRegion? {
  if(!returnedToProfile||BattleText.normalize(member)!=BattleText.normalize(current)||width!=w||height!=h)return null
  return close.takeIf {it.left>=0&&it.top>=0&&it.right<=w&&it.bottom<=h&&it.width>=8&&it.height>=8}
 }
}

internal object CachedFairyPolicy {
 fun reusable(absent:Boolean,type:String,level:Int,effect:Double,detail:String=""):Boolean {
  if(absent)return true
  if(level !in 1..20)return false
  return when(type){"LORA"->effect.isFinite()&&effect>0;"MIRA"->(effect.isFinite()&&effect>0&&effect<=70)||FairyEffects.miraCoefficient(detail)!=null;"TIRA"->true;else->false}
 }
}
