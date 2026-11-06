package eu.komars.forgescanner.battle

/** Short central strokes, independent of screen resolution and below GestureSafety limits. */
internal object ResearchScrollGesture {
 const val REWIND_COUNT=6
 const val ROUTE_VERSION=1
 fun replaySteps(version:Int,savedWidth:Int,savedHeight:Int,steps:Int,width:Int,height:Int):Int? =
  steps.takeIf {version==ROUTE_VERSION && savedWidth==width && savedHeight==height && width>0 && height>0 && it in 0..12}
 data class Stroke(val x:Float,val fromY:Float,val toY:Float,val durationMs:Long)
 fun create(width:Int,height:Int,toTop:Boolean):Stroke {
  require(width>0 && height>0)
  val half=if(toTop).16f else .095f
  val upper=height*(.50f-half);val lower=height*(.50f+half)
  return Stroke(width*.50f,if(toTop)upper else lower,if(toTop)lower else upper,260L)
 }
}
