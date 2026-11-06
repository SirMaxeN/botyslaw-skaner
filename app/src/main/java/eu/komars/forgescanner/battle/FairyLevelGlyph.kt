package eu.komars.forgescanner.battle

/** Independent shape evidence from white game lettering. Never assigns a digit without a learned label. */
internal object FairyLevelGlyph {
 data class Signature(val digits:Int,val aspect:Double,val bits:String)
 private data class Part(val l:Int,val t:Int,val r:Int,val b:Int,val area:Int){val w get()=r-l;val h get()=b-t}
 fun extract(w:Int,h:Int,p:IntArray):Signature? {
  if(w<12||h<12||p.size!=w*h)return null
  val top=(h*.58).toInt();val ink=BooleanArray(p.size){i->i/w>=top&&minOf(p[i] ushr 16 and 255,p[i] ushr 8 and 255,p[i] and 255)>=220}
  val visited=BooleanArray(p.size);val queue=IntArray(p.size);val parts=mutableListOf<Part>()
  for(i in p.indices)if(ink[i]&&!visited[i]) {
   var head=0;var tail=1;queue[0]=i;visited[i]=true;var l=i%w;var rr=l;var t=i/w;var bb=t
   while(head<tail){val n=queue[head++];val x=n%w;val y=n/w;l=minOf(l,x);rr=maxOf(rr,x);t=minOf(t,y);bb=maxOf(bb,y)
    fun add(k:Int){if(k in p.indices&&ink[k]&&!visited[k]){visited[k]=true;queue[tail++]=k}}
    if(x>0)add(n-1);if(x<w-1)add(n+1);if(y>top)add(n-w);if(y<h-1)add(n+w)
   };parts+=Part(l,t,rr+1,bb+1,tail)
  }
  val letters=parts.filter {it.b>=h*.80&&it.h>=h*.10&&it.h<=h*.36&&it.w>=2}
  val height=letters.maxOfOrNull {it.h}?:return null
  val baseline=letters.maxOf {it.b};val aligned=letters.filter {it.h>=height*.55&&kotlin.math.abs(it.b-baseline)<=height*.25}
  val dots=parts.filter {it.h<=height*.45&&it.w<=height*.45&&it.b>=baseline-height*.35&&it.b<=baseline+2}
  val dot=dots.filter {d->aligned.count {it.r<=d.l}>=2&&aligned.count {it.l>=d.r} in 1..2}.maxByOrNull {it.l}?:return null
  val digits=aligned.filter {it.l>=dot.r}.sortedBy {it.l}
  if(digits.size !in 1..2)return null
  val l=digits.first().l;val rr=digits.last().r;val t=digits.minOf {it.t};val bb=digits.maxOf {it.b}
  val aspect=(rr-l).toDouble()/(bb-t)
  if(aspect !in .15..1.8)return null
  val bits=StringBuilder(20*28)
  for(y in 0 until 28)for(x in 0 until 20){val sx=l+((x+.5)*(rr-l)/20).toInt().coerceAtMost(rr-l-1);val sy=t+((y+.5)*(bb-t)/28).toInt().coerceAtMost(bb-t-1);bits.append(if(ink[sy*w+sx])'1'else'0')}
  return Signature(digits.size,aspect,bits.toString())
 }
 fun error(a:Signature,b:Signature):Double {
  if(a.digits!=b.digits||a.bits.length!=560||b.bits.length!=560||kotlin.math.abs(a.aspect-b.aspect)>.15)return 1.0
  var union=0;var common=0
  for(i in a.bits.indices){if(a.bits[i]=='1'||b.bits[i]=='1')union++;if(a.bits[i]=='1'&&b.bits[i]=='1')common++}
  return if(union==0)1.0 else 1-common.toDouble()/union+kotlin.math.abs(a.aspect-b.aspect)*.4
 }
 fun match(signature:Signature?,examples:List<Pair<Int,Signature>>):Int? {
  if(signature==null||signature.digits!=1)return null
  val scores=examples.filter {it.first in setOf(3,5,8)}.groupBy {it.first}.map {entry->entry.key to entry.value.minOf {error(signature,it.second)}}.sortedBy {it.second}
  val best=scores.firstOrNull()?:return null
  return best.first.takeIf {best.second<=.08&&(scores.size==1||scores[1].second-best.second>=.06)}
 }
}
