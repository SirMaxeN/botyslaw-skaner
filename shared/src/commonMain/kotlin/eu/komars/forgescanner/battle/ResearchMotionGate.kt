package eu.komars.forgescanner.battle

/** Two consecutive low-change comparisons before using research coordinates. */
class ResearchMotionGate(private val comparisons:Int=2) {
 private var previous:IntArray?=null
 private var stable=0
 fun add(values:IntArray):Boolean {
  require(values.isNotEmpty())
  val old=previous
  val same=old!=null && old.size==values.size &&
   values.indices.count {kotlin.math.abs(values[it]-old[it])>8}<=values.size*2/100
  stable=if(same)stable+1 else 0
  previous=values.copyOf()
  return stable>=comparisons
 }
}
