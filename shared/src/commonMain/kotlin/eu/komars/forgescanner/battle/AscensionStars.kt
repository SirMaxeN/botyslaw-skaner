package eu.komars.forgescanner.battle

/**
 * Ascension ("wzniesienie") stars under profile icons. Skill ascension is one level for all skills
 * (0–3 stars); the game multiplies active skill damage and health by AscensionConfigsLibrary
 * Skills values 49, 2499, 124999 (+1), i.e. ×50, ×2500, ×125 000 (game config 2026-10-08).
 */
object AscensionStars {
 private val skillMultipliers=doubleArrayOf(1.0,50.0,2500.0,125000.0)
 fun skillMultiplier(stars:Int)=skillMultipliers[stars.coerceIn(0,3)]

 private fun star(c:Int):Boolean {val r=c ushr 16 and 255;val g=c ushr 8 and 255;val b=c and 255;return r>=225&&g in 150..225&&b<=90}

 /**
  * Stars in the band under one icon label (pixels of that band). Overlapping stars merge, so the
  * count comes from the width of the yellow area relative to its height. Yellow icon artwork
  * (bomb, meteor) stays far below [minShare] of the slot area: real stars were 300+ px at
  * 1080 px width where artwork gave at most ~110.
  */
 fun count(width:Int,height:Int,argb:IntArray,slotArea:Int,minShare:Double=.045):Int {
  if(argb.size!=width*height)return 0
  var n=0;var minX=Int.MAX_VALUE;var maxX=-1;var minY=Int.MAX_VALUE;var maxY=-1
  for(y in 0 until height)for(x in 0 until width)if(star(argb[y*width+x])){n++;minX=minOf(minX,x);maxX=maxOf(maxX,x);minY=minOf(minY,y);maxY=maxOf(maxY,y)}
  if(n<slotArea*minShare||maxY<=minY)return 0
  val ratio=(maxX-minX+1.0)/(maxY-minY+1.0)
  return when {ratio<1.35->1;ratio<1.95->2;else->3}
 }

 /** One level for all skills: accepted only when every read slot agrees. */
 fun agreed(counts:List<Int>):Int?=counts.takeIf {it.isNotEmpty()}?.distinct()?.singleOrNull()
}
