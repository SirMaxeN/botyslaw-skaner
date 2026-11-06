package eu.komars.forgescanner.battle

/** Navigation and recognition finish independently. Only their joint completion releases the next phase. */
internal class CaptureAnalysisGate {
    private var collecting=false
    private var pending=0
    private var cancelled=false
    val ready get()=!cancelled && !collecting && pending==0
    fun beginCapture() {check(!cancelled && !collecting);collecting=true}
    fun submitAnalysis() {check(!cancelled && collecting);pending++}
    fun finishCapture() {check(collecting);collecting=false}
    fun finishAnalysis() {check(pending>0);pending--}
    fun cancel() {cancelled=true}
}
