package eu.komars.forgescanner.battle

/** List boundaries use viewport pixels, independently of changing OCR spelling or row order. */
internal class RosterEndGate(private val comparisons:Int=2) {
 private var previous:IntArray?=null
 private var stable=0
 fun add(sample:IntArray):Boolean {
  require(sample.isNotEmpty())
  val old=previous
  var ink=0;var changed=0
  if(old!=null && old.size==sample.size)for(i in sample.indices) {
   if(minOf(old[i],sample[i])<180){ink++;if(kotlin.math.abs(sample[i]-old[i])>20)changed++}
  }
  val unchanged=old!=null && old.size==sample.size && ink>=maxOf(16,sample.size/200) && changed<=ink*2/100
  stable=if(unchanged)stable+1 else 0;previous=sample.copyOf()
  return stable>=comparisons
 }
}
