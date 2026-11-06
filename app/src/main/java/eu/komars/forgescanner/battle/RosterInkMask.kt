package eu.komars.forgescanner.battle
/** Solid character fills, excluding the gray card, black outline and blue equipment icons. */
internal object RosterInkMask {
 fun name(r:Int,g:Int,b:Int,threshold:Int=244)=minOf(r,g,b)>threshold && maxOf(r,g,b)-minOf(r,g,b)<20
 fun power(r:Int,g:Int,b:Int)=r>130&&g>70&&b<200&&r-g>15&&g-b>20
}
