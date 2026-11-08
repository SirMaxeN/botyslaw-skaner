package eu.komars.forgescanner.model

/** Saturated banner bodies independent of clan colour, separated from yellow poles, red header background and notification dots. */
object WarNavigationGeometry {
 private fun candidates(w:Int,h:Int,p:IntArray,top:Int,bottom:Int,anyColor:Boolean=false):List<PixelRegion> {
  if(w<100 || h<w || p.size!=w*h)return emptyList()
  fun flag(i:Int):Boolean {val c=p[i];val r=c ushr 16 and 255;val g=c ushr 8 and 255;val b=c and 255;return if(anyColor) maxOf(r,g,b)>150 && maxOf(r,g,b)-minOf(r,g,b)>125 else r>180 && g in 20..190 && b<80 && r>g*1.25}
  val seen=BooleanArray(p.size);val queue=IntArray(p.size);val found=mutableListOf<PixelRegion>()
  for(y in top until bottom)for(x in 0 until w) {
   val start=y*w+x;if(seen[start] || !flag(start))continue
   var head=0;var tail=0;queue[tail++]=start;seen[start]=true;var l=x;var rr=x;var t=y;var b=y
   while(head<tail) {
    val i=queue[head++];val xx=i%w;val yy=i/w;l=minOf(l,xx);rr=maxOf(rr,xx);t=minOf(t,yy);b=maxOf(b,yy)
    for(n in intArrayOf(if(xx>0)i-1 else -1,if(xx<w-1)i+1 else -1,i-w,i+w))if(n in p.indices && !seen[n] && flag(n)){seen[n]=true;queue[tail++]=n}
   }
   val bw=rr-l+1;val bh=b-t+1
   if(bw in (w*.04).toInt()..(w*.18).toInt() && bh in (w*.04).toInt()..(w*.24).toInt() && bh.toDouble()/bw in .65..2.0 && tail>bw*bh*.25)found+=PixelRegion(l,t,rr+1,b+1)
  }
  return found
 }
 fun flags(w:Int,h:Int,p:IntArray):Pair<PixelRegion,PixelRegion>? {
  // Banner bodies are above the separate MVP/points controls. Never propose an avatar as a clan flag.
  val boxes=candidates(w,h,p,0,(h*.20).toInt(),anyColor=true)
  val pairs=boxes.filter {(it.left+it.right)/2<w*.25}.flatMap {a ->boxes.filter {b ->(b.left+b.right)/2>w*.75 && kotlin.math.abs((a.top+a.bottom)-(b.top+b.bottom))<w*.16}.map {a to it}}
  return pairs.singleOrNull()
 }
 fun clanTechnologyCategory(w:Int,h:Int,p:IntArray):PixelRegion?=candidates(w,h,p,(h*.2).toInt(),(h*.7).toInt()).filter {(it.left+it.right)/2>w*.5}.singleOrNull()
 fun clanEntry(w:Int,h:Int,p:IntArray):PixelRegion?=candidates(w,h,p,(h*.86).toInt(),h).filter {(it.left+it.right)/2 in (w*.58).toInt()..(w*.88).toInt()}.singleOrNull()
}
