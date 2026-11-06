package eu.komars.forgescanner.automation
import android.accessibilityservice.GestureDescription
import android.graphics.Path
/** One final continuation, preserving the pointer and releasing it at the end. */
internal object HeldStrokeRelease {
 fun create(stroke:GestureDescription.StrokeDescription,x:Float,y:Float,durationMs:Long):GestureDescription.StrokeDescription {
  require(durationMs in 80..500)
  return stroke.continueStroke(Path().apply {moveTo(x,y)},0,durationMs,false)
 }
}
