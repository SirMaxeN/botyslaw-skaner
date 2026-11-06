package eu.komars.forgescanner.vision
import android.graphics.Bitmap
import eu.komars.forgescanner.automation.AutoScanStep

/** Resource-only fallback: changing resource proposal scale must not alter battle/navigation policy. */
internal object ResourceFieldProposal {
 fun detect(image:Bitmap,step:AutoScanStep):GameUiDetector.Detection? {
  val primary=GameUiDetector().detect(image,step)?.takeIf {it.confidence>=.8f}
  return primary?:GameUiDetector(480).detect(image,step)?.takeIf {it.confidence>=.8f}
 }
}
