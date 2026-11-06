package eu.komars.forgescanner.model

/** Propose the adjacent red-flag nodes on blue or maxed green discs; distinguish sword/heart from resource bags. */
object WarResearchNodes {
 fun find(w:Int,h:Int,p:IntArray):Pair<PixelRegion,PixelRegion>? {
  if(w<100 || h<w || p.size!=w*h)return null
  fun red(i:Int):Boolean {val c=p[i];return (c ushr 16 and 255)>160 && (c ushr 8 and 255)<125 && (c and 255)<155}
  val seen=BooleanArray(p.size);val queue=IntArray(p.size);val candidates=mutableListOf<PixelRegion>();val swords=mutableSetOf<PixelRegion>();val hearts=mutableSetOf<PixelRegion>()
  for(y in (h*.23).toInt() until (h*.8).toInt())for(x in (w*.05).toInt() until (w*.95).toInt()) {
   val start=y*w+x;if(seen[start] || !red(start))continue
   var head=0;var tail=0;queue[tail++]=start;seen[start]=true
   var l=x;var t=y;var rr=x;var b=y
   while(head<tail) {
    val i=queue[head++];val xx=i%w;val yy=i/w;l=minOf(l,xx);rr=maxOf(rr,xx);t=minOf(t,yy);b=maxOf(b,yy)
    for(n in intArrayOf(if(xx>0)i-1 else -1,if(xx<w-1)i+1 else -1,i-w,i+w))if(n in p.indices && !seen[n] && red(n)) {seen[n]=true;queue[tail++]=n}
   }
   if(rr-l !in (w*.017).toInt()..(w*.10).toInt() || b-t !in (w*.014).toInt()..(w*.12).toInt())continue
   // The war banner has a horizontal pole with gold caps on BOTH sides.
   // Spear/pennant attack nodes also have a red shape on a blue disc, but no
   // such pole; accepting those would select unrelated research.
   fun goldNear(x0:Int,x1:Int):Boolean {
    for(yy in maxOf(0,t-(w*.025).toInt())..minOf(h-1,t+(w*.012).toInt()))
     for(xx in maxOf(0,x0)..minOf(w-1,x1)) {
      val c=p[yy*w+xx];val r=c ushr 16 and 255;val g=c ushr 8 and 255;val b0=c and 255
      if(r>175 && g>115 && b0<110 && r>g+15)return true
     }
    return false
   }
   if(!goldNear(l-(w*.025).toInt(),l) || !goldNear(rr,rr+(w*.025).toInt()))continue
   val cx=(l+rr)/2;val cy=(t+b)/2+(w*.017).toInt();val rad=(w*.066).toInt()
   var disc=0;var blade=0;var heart=0;var pommel=0
   for(yy in maxOf(0,cy-rad) until minOf(h,cy+rad))for(xx in maxOf(0,cx-rad) until minOf(w,cx+rad)) {
    val c=p[yy*w+xx];val r=c ushr 16 and 255;val g=c ushr 8 and 255;val bb=c and 255
    if((bb>g+10 && g>r+15)||(g>r+15 && g>bb+15))disc++
    if(xx>=cx && yy>=cy) {
     if(r>145 && g>145 && bb>140 && maxOf(r,g,bb)-minOf(r,g,bb)<65)blade++
     if(r>160 && g<125 && bb<155)heart++
     if(r>175 && g>115 && bb<110 && r>g+15)pommel++
    }
   }
   if(disc>rad*rad*.6) {
    val region=PixelRegion(maxOf(0,cx-rad),maxOf(0,cy-rad),minOf(w,cx+rad),minOf(h,cy+rad))
    candidates+=region
    if(blade>rad*rad*.025 && pommel>rad*rad*.008)swords+=region
    if(heart>rad*rad*.07)hearts+=region
   }
  }
  val pairs=swords.flatMap {a->hearts.filter {b->b.left>a.left && b.left-a.left in (w*.14).toInt()..(w*.27).toInt() && kotlin.math.abs(b.top-a.top)<w*.055}.map {a to it}}
  return pairs.sortedWith(compareBy<Pair<PixelRegion,PixelRegion>> {it.first.top}.thenBy {it.first.left}).firstOrNull()
 }
}
