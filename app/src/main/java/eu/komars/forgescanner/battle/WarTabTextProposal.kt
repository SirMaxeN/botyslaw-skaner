package eu.komars.forgescanner.battle
import eu.komars.forgescanner.model.PixelRegion
object WarTabTextProposal {
 fun find(width:Int,height:Int,words:List<BattleOcrLine>):PixelRegion? {
  val matches=words.filter {BattleText.normalize(it.text).trim() in listOf("wojna","war") && it.top>height*.6 && it.left>width*.5 && it.bottom<=height}
  val line=matches.singleOrNull()?:return null
  val pad=maxOf(8,width/80)
  return PixelRegion((line.left-pad).coerceAtLeast(0),(line.top-pad).coerceAtLeast(0),(line.right+pad).coerceAtMost(width),(line.bottom+pad).coerceAtMost(height))
 }
}
