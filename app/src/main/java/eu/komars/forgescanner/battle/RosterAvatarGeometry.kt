package eu.komars.forgescanner.battle

/** Closed avatar frames identify rows; text/rank holes in card backgrounds cannot split players. */
internal object RosterAvatarGeometry {
 data class Located(val avatar:RosterBox,val row:RosterRowGeometry.Row)
 private fun background(c:Int):Boolean {
  val r=c ushr 16 and 255;val g=c ushr 8 and 255;val b=c and 255
  return (minOf(r,g,b)>=185&&maxOf(r,g,b)<=242&&maxOf(r,g,b)-minOf(r,g,b)<=12) || (r<100&&g>110&&b>180&&b>g*.95&&g>r*1.25)
 }
 fun find(w:Int,h:Int,p:IntArray):List<Located> {
  if(w<40||h<40||p.size!=w*h)return emptyList()
  val sw=(w*.30).toInt();val seen=BooleanArray(sw*h);val queue=IntArray(seen.size);val found=mutableListOf<Located>()
  fun dark(x:Int,y:Int):Boolean {val c=p[y*w+x];return maxOf(c ushr 16 and 255,c ushr 8 and 255,c and 255)<=150}
  for(i in seen.indices) {
   if(seen[i]||!dark(i%sw,i/sw))continue
   var head=0;var tail=0;queue[tail++]=i;seen[i]=true;var l=i%sw;var r=l;var t=i/sw;var b=t
   while(head<tail) {
    val n=queue[head++];val x=n%sw;val y=n/sw;l=minOf(l,x);r=maxOf(r,x);t=minOf(t,y);b=maxOf(b,y)
    for(dy in -1..1)for(dx in -1..1) {
     val nx=x+dx;val ny=y+dy;if(nx !in 0 until sw||ny !in 0 until h)continue
     val next=ny*sw+nx;if(!seen[next]&&dark(nx,ny)){seen[next]=true;queue[tail++]=next}
    }
   }
   r++;b++;val width=r-l;val height=b-t
   if(width<w*.095||height<w*.095||width>w*.24||height>w*.24||width.toDouble()/height !in .85..1.15)continue
   val sides=IntArray(4)
   for(k in 0 until 16) {
    val x=(l+width*(.2+(k+.5)*.6/16)).toInt();val y=(t+height*(.2+(k+.5)*.6/16)).toInt()
    if((0..2).any {dark(l+it,y)})sides[0]++
    if((0..2).any {dark(r-1-it,y)})sides[1]++
    if((0..2).any {dark(x,t+it)})sides[2]++
    if((0..2).any {dark(x,b-1-it)})sides[3]++
   }
   if(sides.any {it<13})continue
   val cy=(t+b)/2;val start=minOf(w-1,(r+width*.1).toInt());val end=(w*.97).toInt()
   if(end<=start||(start until end).count {background(p[cy*w+it])}<(end-start)*.5)continue
   val right=(r until w).lastOrNull {background(p[cy*w+it])}?.plus(1)?:continue
   found+=Located(RosterBox(l,t,r,b),RosterRowGeometry.Row(maxOf(0,(l-width*.18).toInt()),maxOf(0,(t-width*.17).toInt()),right,minOf(h,(b+width*.17).toInt())))
  }
  val unique=mutableListOf<Located>()
  for(item in found.sortedByDescending {it.avatar.width*it.avatar.height}) {
   val a=item.avatar
   if(unique.any {val b=it.avatar;kotlin.math.abs((a.top+a.bottom)-(b.top+b.bottom))/2.0<maxOf(a.height,b.height)*.7})continue
   unique+=item
  }
  return unique.sortedBy {it.avatar.top}
 }
}
