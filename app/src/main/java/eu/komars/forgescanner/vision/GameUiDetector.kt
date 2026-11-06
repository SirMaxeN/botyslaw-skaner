package eu.komars.forgescanner.vision

import android.graphics.Bitmap
import android.graphics.Rect
import eu.komars.forgescanner.automation.AutoScanStep
import eu.komars.forgescanner.automation.NavigationScreenPolicy
import eu.komars.forgescanner.model.GameScreenKind
import eu.komars.forgescanner.model.GameUiGeometryDetector
import kotlin.math.roundToInt

/** UI proposals only. Navigation remains gated by the fixed route and destination verification. */
class GameUiDetector(private val analysisWidth:Int=360) {
    data class Detection(val region:Rect,val confidence:Float,val reason:String,val container:Rect?=null)
    fun detect(bitmap:Bitmap,step:AutoScanStep):Detection? = withGeometry(bitmap) { geometry,scale ->
        geometry.detect(step)?.let { proposal ->
            fun bounds(left:Int,top:Int,right:Int,bottom:Int)=Rect((left/scale).roundToInt(),(top/scale).roundToInt(),(right/scale).roundToInt(),(bottom/scale).roundToInt())
            val r=proposal.region
            Detection(bounds(r.left,r.top,r.right,r.bottom),proposal.confidence,proposal.reason,
                proposal.container?.run { bounds(left,top,right,bottom) })
        }
    }
    fun technologyTab(bitmap:Bitmap):Rect? = withGeometry(bitmap) { geometry,scale ->
        geometry.technologyTab()?.let { Rect((it.left/scale).roundToInt(),(it.top/scale).roundToInt(),(it.right/scale).roundToInt(),(it.bottom/scale).roundToInt()) }
    }
    fun repairCandidates(bitmap:Bitmap,step:AutoScanStep,previous:Rect):List<Rect> {
        val found=detect(bitmap,step)?.takeIf { it.confidence>=.8f } ?: return emptyList()
        return listOf(Rect(found.container?:found.region)).distinct().filter { !it.isEmpty }
    }
    data class Observation(val kind: GameScreenKind, val close: Rect? = null)
    /** Reuse one structural analysis when recovery also needs the current close control. */
    fun observe(bitmap: Bitmap, closeStep: AutoScanStep? = null): Observation = withGeometry(bitmap) { geometry, scale ->
        val kind = geometry.screenKind()
        val close = closeStep?.let { geometry.detect(it)?.region }?.run {
            Rect((left / scale).roundToInt(), (top / scale).roundToInt(), (right / scale).roundToInt(), (bottom / scale).roundToInt())
        }
        Observation(kind, close)
    } ?: Observation(GameScreenKind.UNKNOWN)
    fun screenKind(bitmap:Bitmap):GameScreenKind = withGeometry(bitmap) { geometry,_ -> geometry.screenKind() }?:GameScreenKind.UNKNOWN
    fun forbiddenActionAreas(bitmap:Bitmap):List<Rect> = withGeometry(bitmap) { geometry,scale ->
        geometry.forbiddenActionAreas().map { Rect((it.left/scale).roundToInt(),(it.top/scale).roundToInt(),(it.right/scale).roundToInt(),(it.bottom/scale).roundToInt()) }
    }?:emptyList()
    fun isDestination(bitmap:Bitmap,navigationStep:AutoScanStep):Boolean =
        NavigationScreenPolicy.accepts(navigationStep, screenKind(bitmap))
    private fun <T> withGeometry(bitmap:Bitmap,action:(GameUiGeometryDetector,Double)->T):T? {
        if(bitmap.isRecycled||bitmap.width<100||bitmap.height<100)return null
        val width=minOf(analysisWidth.coerceIn(100,480),bitmap.width);val scale=width.toDouble()/bitmap.width
        val height=(bitmap.height*scale).roundToInt()
        val reduced=Bitmap.createScaledBitmap(bitmap,width,height,true)
        return try {
            val colors=IntArray(width*height);reduced.getPixels(colors,0,width,0,0,width,height)
            action(GameUiGeometryDetector(width,height,colors),scale)
        }finally{if(reduced!==bitmap)reduced.recycle()}
    }
}
