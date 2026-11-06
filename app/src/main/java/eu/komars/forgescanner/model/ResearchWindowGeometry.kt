package eu.komars.forgescanner.model

/** Large white research dialog on a dimmed technology screen; never the donation buttons. */
object ResearchWindowGeometry {
 fun find(w:Int,h:Int,p:IntArray):PixelRegion? {
  if(w<100 || h<w || p.size!=w*h)return null
  val seen=BooleanArray(p.size);val q=IntArray(p.size);val found=mutableListOf<PixelRegion>()
  fun white(i:Int):Boolean {val c=p[i];return (c ushr 16 and 255)>235 && (c ushr 8 and 255)>235 && (c and 255)>235}
  for(i in p.indices) {
   if(seen[i] || !white(i))continue
   var head=0;var tail=0;q[tail++]=i;seen[i]=true
   var l=i%w;var rr=l;var t=i/w;var b=t
   while(head<tail) {
    val n=q[head++];val x=n%w;val y=n/w;l=minOf(l,x);rr=maxOf(rr,x);t=minOf(t,y);b=maxOf(b,y)
    for(k in intArrayOf(if(x>0)n-1 else -1,if(x<w-1)n+1 else -1,n-w,n+w))if(k in p.indices && !seen[k] && white(k)){seen[k]=true;q[tail++]=k}
   }
   if(rr-l>w*.6 && b-t>w*.3 && t>h*.12 && b<h*.9 && tail>w*h*.08)found+=PixelRegion(l,t,rr+1,b+1)
  }
  return found.maxByOrNull {it.width*it.height}
 }
 fun description(panel:PixelRegion)=PixelRegion(panel.left+(panel.width*.2).toInt(),panel.top+(panel.height*.035).toInt(),panel.right-(panel.width*.035).toInt(),panel.top+(panel.height*.28).toInt())
}
