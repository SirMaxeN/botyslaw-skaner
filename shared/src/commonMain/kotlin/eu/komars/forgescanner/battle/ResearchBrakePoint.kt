package eu.komars.forgescanner.battle
/** Only a completely white patch in the outer margin, never an assumed control coordinate. */
object ResearchBrakePoint {
 fun find(w:Int,h:Int,p:IntArray):Pair<Int,Int>? {
  if(w<100 || h<w || p.size!=w*h)return null
  val radius=maxOf(3,w/80)
  for(y in listOf(h*55/100,h*65/100,h*40/100))for(x in listOf(w*25/1000,w*975/1000)) {
   if(x-radius<1 || x+radius>=w-1)continue
   var blank=true
   for(yy in y-radius..y+radius)for(xx in x-radius..x+radius) {
    val c=p[yy*w+xx]
    if((c ushr 16 and 255)<235 || (c ushr 8 and 255)<235 || (c and 255)<235)blank=false
   }
   if(blank)return x to y
  }
  return null
 }
}
