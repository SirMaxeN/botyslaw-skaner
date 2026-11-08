package eu.komars.forgescanner.battle

/** One screen read covers all visible rows; reserve extra room around two known avatars to join the next view. */
object RosterScrollPlan {
 fun down(viewportHeight:Int,screenHeight:Int,anchors:List<Int>):Float {
  val sorted=anchors.sorted();val gaps=sorted.zipWithNext {a,b->b-a}.filter {it>0}.sorted()
  val pitch=gaps.getOrNull(gaps.size/2)?.toFloat()
  val target=if(sorted.size>=3&&pitch!=null)(sorted[sorted.size-2]-sorted[0])-pitch*.75f else viewportHeight*.40f
  return target.coerceAtLeast(viewportHeight*.10f).coerceAtMost(minOf(viewportHeight*.65f,screenHeight*.30f))
 }
 fun backtrack(viewportHeight:Int,screenHeight:Int,anchors:List<Int>):Float {
  val gaps=anchors.sorted().zipWithNext {a,b->b-a}.filter {it>0}.sorted()
  val pitch=gaps.getOrNull(gaps.size/2)?.toFloat()?: (viewportHeight/6f)
  return (pitch*1.15f).coerceAtMost(minOf(viewportHeight*.28f,screenHeight*.20f))
 }
 fun rewind(viewportHeight:Int,screenHeight:Int)=minOf(viewportHeight*.80f,screenHeight*.34f)
}
