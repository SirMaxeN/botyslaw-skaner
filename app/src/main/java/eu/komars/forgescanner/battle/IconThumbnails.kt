package eu.komars.forgescanner.battle

/**
 * Compares 16×16 colour thumbnails of in-game skill icons with icons learned from detail windows
 * that named the skill. Answers only when one skill is clearly closer than every other skill.
 */
internal object IconThumbnails {
 private fun pixels(hex:String):IntArray?=if(hex.length!=16*16*6)null else runCatching {IntArray(256){hex.substring(it*6,it*6+6).toInt(16)}}.getOrNull()
 /** Mean absolute channel difference, 0–255. */
 fun distance(a:String,b:String):Double? {
  val x=pixels(a)?:return null;val y=pixels(b)?:return null
  var sum=0L
  for(i in 0 until 256){val p=x[i];val q=y[i];sum+=kotlin.math.abs((p ushr 16 and 255)-(q ushr 16 and 255))+kotlin.math.abs((p ushr 8 and 255)-(q ushr 8 and 255))+kotlin.math.abs((p and 255)-(q and 255))}
  return sum/768.0
 }
 fun <T> recognize(thumb:String,learned:List<Pair<T,String>>,maxDistance:Double=22.0,minMargin:Double=8.0):T? {
  val scores=learned.mapNotNull {(type,other)->distance(thumb,other)?.let {type to it}}.groupBy {it.first}.map {(type,list)->type to list.minOf {it.second}}.sortedBy {it.second}
  val best=scores.firstOrNull()?:return null
  val second=scores.getOrNull(1)?.second?:Double.MAX_VALUE
  return best.first.takeIf {best.second<=maxDistance&&second-best.second>=minMargin}
 }
}
