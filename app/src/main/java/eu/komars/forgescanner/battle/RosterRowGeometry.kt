package eu.komars.forgescanner.battle

/** Neutral gray cards define rows, independently of names, avatars, flags and clan ranks. */
internal object RosterRowGeometry {
 data class Row(val left:Int,val top:Int,val right:Int,val bottom:Int) {val height get()=bottom-top}
 fun find(w:Int,h:Int,p:IntArray):List<Row> {
  if(w<40 || h<40 || p.size!=w*h)return emptyList()
  fun gray(c:Int):Boolean {val r=c ushr 16 and 255;val g=c ushr 8 and 255;val b=c and 255;return minOf(r,g,b)>=185 && maxOf(r,g,b)<=240 && maxOf(r,g,b)-minOf(r,g,b)<=12}
  fun band(y:Int):Boolean {var count=0;for(i in 0 until 32){val x=(w*(.50+(i+.5)*.42/32)).toInt().coerceIn(0,w-1);if(gray(p[y*w+x]))count++};return count>=23}
  val bands=BooleanArray(h){band(it)}
  // Rank/name glyphs can briefly interrupt the broad gray band; white card gaps remain separate.
  var gapStart=-1;val maxHole=maxOf(1,(w*.012).toInt())
  for(y in 0 until h)if(!bands[y]){if(gapStart<0)gapStart=y}else if(gapStart>=0){if(gapStart>0 && y-gapStart<=maxHole)for(k in gapStart until y)bands[k]=true;gapStart=-1}
  val out=mutableListOf<Row>();var start=-1
  for(y in 0..h) {
   if(y<h && bands[y]){if(start<0)start=y}
   else if(start>=0) {
    val end=y;val size=end-start
    // Clipped cards must be read after the next overlapping movement, never as complete rows.
    if(start>1 && end<h-1 && size>=w*.065 && size<=w*.32) {
     val cy=(start+end)/2;var left=w;var right=0
     for(x in 0 until w)if(gray(p[cy*w+x])){left=minOf(left,x);right=maxOf(right,x+1)}
     if(right-left>w*.65)out+=Row(left,start,right,end)
    };start=-1
   }
  };return out
 }
}

/** Contrast passes belong to one card; never pair one player's name with another player's power. */
internal object RosterRowEvidence {
 fun resolve(passes:List<List<WarMember>>):WarMember? {
  val votes=passes.mapNotNull {it.singleOrNull()}.groupingBy {it}.eachCount().entries.sortedByDescending {it.value}
  val first=votes.firstOrNull()?:return null
  return first.key.takeIf {votes.size==1 || first.value>votes[1].value}
 }
}

