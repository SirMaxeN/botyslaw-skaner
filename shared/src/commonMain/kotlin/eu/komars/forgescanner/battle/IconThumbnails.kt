package eu.komars.forgescanner.battle

/**
 * Compares in-game skill icons with icons learned from detail windows that named the skill.
 *
 * Version 2 (icon40): the slot box from the profile layout shifts a few pixels between profiles,
 * which made fixed 16×16 thumbnails of the same skill look unrelated (same skill 25–80 apart,
 * different skills from 35). Icons are now cut tightly around the round artwork, scaled to 40×40
 * and compared with the best offset within ±4 px: on real scan captures the same skill is ~14
 * apart and different skills ≥37.
 */
object IconThumbnails {
 const val SIZE=40
 private const val SHIFT=4
 private fun pixels(hex:String,size:Int):IntArray?=if(hex.length!=size*size*6)null else runCatching {IntArray(size*size){hex.substring(it*6,it*6+6).toInt(16)}}.getOrNull()

 /** Mean absolute channel difference (0–255) of legacy 16×16 thumbnails. */
 fun distance(a:String,b:String):Double? {
  val x=pixels(a,16)?:return null;val y=pixels(b,16)?:return null
  var sum=0L
  for(i in 0 until 256){val p=x[i];val q=y[i];sum+=kotlin.math.abs((p ushr 16 and 255)-(q ushr 16 and 255))+kotlin.math.abs((p ushr 8 and 255)-(q ushr 8 and 255))+kotlin.math.abs((p and 255)-(q and 255))}
  return sum/768.0
 }

 /** Shift-tolerant distance of two 40×40 icons: best offset within ±4 px over the central 32×32. */
 fun iconDistance(a:String,b:String):Double? {
  val x=pixels(a,SIZE)?:return null;val y=pixels(b,SIZE)?:return null
  var best=Double.MAX_VALUE
  for(dy in -SHIFT..SHIFT)for(dx in -SHIFT..SHIFT) {
   var sum=0L
   for(row in SHIFT until SIZE-SHIFT)for(col in SHIFT until SIZE-SHIFT) {
    val p=x[row*SIZE+col];val q=y[(row+dy)*SIZE+col+dx]
    sum+=kotlin.math.abs((p ushr 16 and 255)-(q ushr 16 and 255))+kotlin.math.abs((p ushr 8 and 255)-(q ushr 8 and 255))+kotlin.math.abs((p and 255)-(q and 255))
    if(sum/1.0>=best*((SIZE-2*SHIFT)*(SIZE-2*SHIFT)*3))break
   }
   best=minOf(best,sum/((SIZE-2*SHIFT)*(SIZE-2*SHIFT)*3.0))
  }
  return best
 }

 /**
  * Tight square around the round icon inside a slot crop: rows and columns where at least 35% of
  * pixels differ from the panel background. Returns [left, top, right, bottom] in crop coordinates,
  * or null when no clear icon is found (the caller keeps the original box).
  */
 fun tightBox(width:Int,height:Int,argb:IntArray,background:Int):IntArray? {
  if(width<6||height<6||argb.size!=width*height)return null
  fun on(c:Int)=kotlin.math.abs((c ushr 16 and 255)-(background ushr 16 and 255))+kotlin.math.abs((c ushr 8 and 255)-(background ushr 8 and 255))+kotlin.math.abs((c and 255)-(background and 255))>60
  var left=width;var right=0;var top=height;var bottom=0
  for(y in 0 until height){var n=0;for(x in 0 until width)if(on(argb[y*width+x]))n++;if(n>width*.35){top=minOf(top,y);bottom=maxOf(bottom,y+1)}}
  for(x in 0 until width){var n=0;for(y in 0 until height)if(on(argb[y*width+x]))n++;if(n>height*.35){left=minOf(left,x);right=maxOf(right,x+1)}}
  if(right-left<width/3||bottom-top<height/3)return null
  val side=maxOf(right-left,bottom-top)
  return intArrayOf(left,top,minOf(width,left+side),minOf(height,top+side))
 }

 fun <T> recognize(thumb:String,learned:List<Pair<T,String>>,maxDistance:Double=22.0,minMargin:Double=8.0):T? =
  best(learned.mapNotNull {(type,other)->distance(thumb,other)?.let {type to it}},maxDistance,minMargin)

 /** icon40 match: one skill clearly closest (≤30 and ≥8 ahead of any other skill). */
 fun <T> recognizeIcon(icon:String,learned:List<Pair<T,String>>,maxDistance:Double=30.0,minMargin:Double=8.0):T? =
  best(learned.mapNotNull {(type,other)->iconDistance(icon,other)?.let {type to it}},maxDistance,minMargin)

 /** Closest other-skill distance, used to refuse storing an icon that looks like another skill. */
 fun <T> closestOther(icon:String,type:T,learned:List<Pair<T,String>>):Double? =
  learned.filter {it.first!=type}.mapNotNull {iconDistance(icon,it.second)}.minOrNull()

 private fun <T> best(scored:List<Pair<T,Double>>,maxDistance:Double,minMargin:Double):T? {
  val scores=scored.groupBy {it.first}.map {(type,list)->type to list.minOf {it.second}}.sortedBy {it.second}
  val best=scores.firstOrNull()?:return null
  val second=scores.getOrNull(1)?.second?:Double.MAX_VALUE
  return best.first.takeIf {best.second<=maxDistance&&second-best.second>=minMargin}
 }
}
