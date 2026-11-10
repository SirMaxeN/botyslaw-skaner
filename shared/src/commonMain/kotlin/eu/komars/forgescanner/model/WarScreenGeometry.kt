package eu.komars.forgescanner.model
import kotlin.math.abs

/** Current visible modal content wins over every feature of the dimmed screen below it. */
object WarScreenGeometry {
 data class Part(val box:PixelRegion,val area:Int)
 data class Observation(val info:PixelRegion?=null,val bonusText:PixelRegion?=null,val ok:PixelRegion?=null,val roster:PixelRegion?=null,val rosterClose:PixelRegion?=null,val research:PixelRegion?=null,val researchClose:PixelRegion?=null,val footerClose:PixelRegion?=null,val tree:Boolean=false)
 private fun parts(w:Int,h:Int,p:IntArray,predicate:(Int)->Boolean):List<Part> {
  val neighbors=IntArray(4);val seen=BooleanArray(p.size);val q=IntArray(p.size);val found=mutableListOf<Part>()
  for(i in p.indices){if(seen[i]||!predicate(p[i]))continue
   var head=0;var tail=0;q[tail++]=i;seen[i]=true;var l=i%w;var rr=l;var t=i/w;var b=t
   while(head<tail){val n=q[head++];val x=n%w;val y=n/w;l=minOf(l,x);rr=maxOf(rr,x);t=minOf(t,y);b=maxOf(b,y)
    neighbors[0]=if(x>0)n-1 else -1;neighbors[1]=if(x<w-1)n+1 else -1;neighbors[2]=n-w;neighbors[3]=n+w
    for(k in neighbors)if(k in p.indices&&!seen[k]&&predicate(p[k])){seen[k]=true;q[tail++]=k}
   }
   if(tail>=4)found+=Part(PixelRegion(l,t,rr+1,b+1),tail)
  };return found
 }
 private fun red(c:Int)=c ushr 16 and 255
 private fun green(c:Int)=c ushr 8 and 255
 private fun blue(c:Int)=c and 255
 fun observe(w:Int,h:Int,p:IntArray):Observation {
  if(w<100||h<w||p.size!=w*h)return Observation()
  val close=CloseButtonPixelDetector()
  val footer=close.detectGameplay(w,h,p)
  fun around(cx:Int,cy:Int,r:Int)=PixelRegion((cx-r).coerceAtLeast(0),(cy-r).coerceAtLeast(0),(cx+r).coerceAtMost(w),(cy+r).coerceAtMost(h))
  val light=parts(w,h,p){red(it)>200&&green(it)>200&&blue(it)>200&&maxOf(red(it),green(it),blue(it))-minOf(red(it),green(it),blue(it))<30}
  val viewport=light.map {it.box}.filter {it.left>w*.025&&it.right<w*.975&&it.width>w*.65&&it.height>w*.85&&it.top>h*.10&&it.bottom<h*.93}.maxByOrNull {it.width*it.height}
  if(viewport!=null){
   val x=close.detect(w,h,p,around((viewport.left+viewport.right)/2,viewport.bottom+(w*.09).toInt(),(w*.09).toInt()))
   if(x!=null)return Observation(roster=viewport,rosterClose=x,footerClose=footer)
  }
  val white=ResearchWindowGeometry.find(w,h,p)
  if(white!=null){
   val x=close.detect(w,h,p,around((white.left+white.right)/2,white.bottom,(w*.075).toInt()))
   if(x!=null)return Observation(research=white,researchClose=x,footerClose=footer)
   val buttons=parts(w,h,p){red(it)<90&&green(it)>110&&blue(it)>170&&blue(it)>green(it)*1.08}.map {it.box}.filter {it.left>white.left&&it.right<white.right&&it.top>white.top&&it.bottom<white.bottom&&it.width>w*.2&&it.height>w*.07}
   val ok=buttons.singleOrNull()
   if(ok!=null){val pad=(w*.02).toInt();return Observation(bonusText=PixelRegion(white.left+pad,white.top+pad,white.right-pad,ok.top-pad),ok=ok,footerClose=footer)}
  }
  val disks=parts(w,h,p){red(it)<45&&green(it)<45&&blue(it)<45}.filter {part ->
   val q=part.box
   q.left>w*.7&&q.top>h*.18&&q.bottom<h*.5&&q.width in (w*.025).toInt()..(w*.065).toInt()&&q.height in (w*.025).toInt()..(w*.065).toInt()&&q.width.toDouble()/q.height in .7..1.3&&part.area.toDouble()/(q.width*q.height) in .40.. .88
  }.map {it.box}.filter {q ->
   val cx=(q.left+q.right)/2;var whitePixels=0
   for(y in q.top+1 until q.bottom-1)for(x in maxOf(q.left,cx-1)..minOf(q.right-1,cx+1)){val c=p[y*w+x];if(red(c)>220&&green(c)>220&&blue(c)>220)whitePixels++}
   whitePixels>=maxOf(3,q.height/3)
  }
  val circles=parts(w,h,p){val r=red(it);val g=green(it);val b=blue(it);(g>100&&g>r*1.15&&g>b*1.05)||(b>130&&g>100&&b>r*1.2)}.map {it.box}.filter {it.top>h*.18&&it.bottom<h*.83&&it.width in (w*.07).toInt()..(w*.20).toInt()&&it.height.toDouble()/it.width in .75..1.25}
  val tree=circles.any {a ->circles.count {abs(it.top-a.top)<w*.04}>=3}
  return Observation(info=disks.singleOrNull(),footerClose=footer,tree=tree)
 }
}
