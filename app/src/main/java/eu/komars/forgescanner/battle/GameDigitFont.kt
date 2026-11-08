package eu.komars.forgescanner.battle

/**
 * Reads the game's outlined white "Poz. N" labels (1–3 digits) by comparing each digit's shape with
 * templates cut from real screenshots and with digits learned from confirmed reads. It is an
 * independent second opinion to ML Kit, which confuses this font's 0/o, 1/l and 3/5/8.
 * It only answers when every digit is clearly closer to one template than to any other digit.
 */
object GameDigitFont {
 const val W=10
 const val H=14
 data class Glyph(val aspect:Double,val bits:String)
 data class Sample(val digit:Int,val glyph:Glyph)
 private data class Part(val l:Int,val t:Int,val r:Int,val b:Int,val area:Int){val w get()=r-l;val h get()=b-t}

 private fun white(c:Int)=minOf(c ushr 16 and 255,c ushr 8 and 255,c and 255)>=205

 /**
  * Prefixed mode: digits after the last "." of a "Poz. N" label. Counter mode (prefixed=false): the
  * rightmost run of adjacent digits on the text line, e.g. "9383" beside the anvil icon; thousands
  * separators are too small to be letters and are skipped. Null when the structure is not found.
  */
 fun segment(w:Int,h:Int,p:IntArray,prefixed:Boolean=true):List<Glyph>? {
  if(w<8||h<6||p.size!=w*h)return null
  val ink=BooleanArray(p.size){white(p[it])}
  val visited=BooleanArray(p.size);val queue=IntArray(p.size);val parts=mutableListOf<Part>()
  for(i in p.indices)if(ink[i]&&!visited[i]) {
   var head=0;var tail=1;queue[0]=i;visited[i]=true;var l=i%w;var r=l;var t=i/w;var b=t
   while(head<tail){val n=queue[head++];val x=n%w;val y=n/w;l=minOf(l,x);r=maxOf(r,x);t=minOf(t,y);b=maxOf(b,y)
    fun add(k:Int){if(ink[k]&&!visited[k]){visited[k]=true;queue[tail++]=k}}
    if(x>0)add(n-1);if(x<w-1)add(n+1);if(y>0)add(n-w);if(y<h-1)add(n+w)
   }
   if(tail>=3)parts+=Part(l,t,r+1,b+1,tail)
  }
  if(parts.size<(if(prefixed)3 else 1))return null
  // The tallest glyphs (P and digits) define the text line; letters share its baseline.
  val tall=parts.filter {it.h>=3}.sortedByDescending {it.h}
  val height=tall.take(if(prefixed)3 else 2).minOfOrNull {it.h}?:return null
  if(height<5)return null
  val baseline=tall.filter {it.h>=height*.8}.map {it.b}.sorted().let {it[it.size/2]}
  val letters=parts.filter {it.h>=height*.55&&it.h<=height*1.4&&kotlin.math.abs(it.b-baseline)<=height*.25}.sortedBy {it.l}
  val digits=if(prefixed) {
   val dots=parts.filter {it.h<=height*.42&&it.w<=height*.5&&kotlin.math.abs(it.b-baseline)<=height*.25}
   val dot=dots.filter {d->letters.count {it.r<=d.l+1}>=2&&letters.count {it.l>=d.r-1} in 1..3}.maxByOrNull {it.l}?:return null
   // Digits right after the dot, up to the first wide gap: a neighbouring label may follow ("Poz. 14  Poz").
   val after=letters.filter {it.l>=dot.r-1}
   val run=mutableListOf(after.firstOrNull()?:return null)
   for(letter in after.drop(1)){if(letter.l-run.last().r<=height*.6)run.add(letter) else break}
   // A digit touching the right edge may be cut ("Poz. 1|4" read as 1): refuse instead of guessing.
   // Thin remnants of a cut digit are not "letters", so any ink on the text line at the edge counts.
   if(run.last().r>=w||parts.any {it.r>=w&&it.l>run.last().l&&it.l-run.last().r<=height*.6&&it.b>=baseline-height&&it.t<=baseline})return null
   run.takeIf {it.size in 1..3}?:return null
  } else {
   // Walk left from the rightmost letter while letters stay adjacent (gap below 0.6 × height).
   val run=mutableListOf(letters.lastOrNull()?:return null)
   for(letter in letters.dropLast(1).asReversed()){if(run.first().l-letter.r<=height*.6)run.add(0,letter) else break}
   run.takeIf {it.size in 1..7}?:return null
  }
  // Digits are adjacent: a large gap means another label or artwork.
  for((a,b) in digits.zipWithNext())if(b.l-a.r>height*.6)return null
  return digits.map {d ->
   val bits=StringBuilder(W*H)
   for(y in 0 until H)for(x in 0 until W) {
    val sx=d.l+((x+.5)*d.w/W).toInt().coerceAtMost(d.w-1);val sy=d.t+((y+.5)*d.h/H).toInt().coerceAtMost(d.h-1)
    bits.append(if(ink[sy*w+sx])'1' else '0')
   }
   Glyph(d.w.toDouble()/d.h,bits.toString())
  }
 }
 fun error(a:Glyph,b:Glyph):Double {
  var union=0;var common=0
  for(i in 0 until minOf(a.bits.length,b.bits.length)){val x=a.bits[i]=='1';val y=b.bits[i]=='1';if(x||y)union++;if(x&&y)common++}
  return (if(union==0)1.0 else 1-common.toDouble()/union)+kotlin.math.abs(a.aspect-b.aspect)*.5
 }
 /** Best digit for one glyph, or null when it is not clearly separated from the runner-up digit. */
 fun classify(glyph:Glyph,samples:List<Sample>,maxError:Double=.32,minMargin:Double=.07):Int? {
  val scores=samples.groupBy {it.digit}.map {(digit,list)->digit to list.minOf {error(glyph,it.glyph)}}.sortedBy {it.second}
  val best=scores.firstOrNull()?:return null
  val second=scores.getOrNull(1)?.second?:Double.MAX_VALUE
  return best.first.takeIf {best.second<=maxError&&second-best.second>=minMargin}
 }
 fun read(glyphs:List<Glyph>?,samples:List<Sample>):Int? {
  if(glyphs.isNullOrEmpty()||samples.isEmpty())return null
  val digits=glyphs.map {classify(it,samples)?:return null}
  if(digits.size>1&&digits.first()==0)return null
  return digits.fold(0){n,d->n*10+d}.takeIf {it in 1..999}
 }
 fun read(w:Int,h:Int,p:IntArray,samples:List<Sample>)=read(segment(w,h,p),samples)
 /** Plain counter such as "9383"; up to seven digits, leading zeros rejected. */
 fun readCounter(w:Int,h:Int,p:IntArray,samples:List<Sample>):Long? {
  val glyphs=segment(w,h,p,prefixed=false)?:return null
  if(samples.isEmpty())return null
  // The resource icon touching the number (e.g. the anvil hammer) is skipped only at the start.
  val classified=glyphs.map {classify(it,samples)}.dropWhile {it==null}
  if(classified.isEmpty()||classified.any {it==null})return null
  val digits=classified.map {it!!}
  if(digits.size>1&&digits.first()==0)return null
  return digits.fold(0L){n,d->n*10+d}
 }
 /** Training examples from a label whose value was confirmed independently. */
 fun learn(w:Int,h:Int,p:IntArray,value:Int):List<Sample> {
  val glyphs=segment(w,h,p)?:return emptyList()
  val digits=value.toString().map {it-'0'}
  return if(glyphs.size==digits.size)glyphs.zip(digits).map {(g,d)->Sample(d,g)} else emptyList()
 }
 /** Plain-text template format: one sample per line, "digit aspect bits". */
 fun encode(samples:List<Sample>):String=samples.joinToString("\n") {sample ->
  val aspect=String.format(java.util.Locale.ROOT,"%.4f",sample.glyph.aspect)
  "${sample.digit} $aspect ${sample.glyph.bits}"
 }
 fun decode(text:String):List<Sample> = text.lineSequence().mapNotNull {line ->
  val parts=line.trim().split(' ')
  if(parts.size!=3)null else runCatching {Sample(parts[0].toInt(),Glyph(parts[1].toDouble(),parts[2]))}.getOrNull()
 }.filter {it.digit in 0..9&&it.glyph.bits.length==W*H&&it.glyph.bits.all {c->c=='0'||c=='1'}}.toList()
}
