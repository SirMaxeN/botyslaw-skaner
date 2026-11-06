package eu.komars.forgescanner.battle
import eu.komars.forgescanner.model.*
import eu.komars.forgescanner.automation.AutoScanStep
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.*
class ResourceHomeRegressionTest {
 @Test fun resourcesRecognizeActualHomeAndAllEntryFields() {
  val dir=System.getenv("WAR_SCENE_FIXTURES");assumeTrue(dir!=null)
  for(name in listOf("resources-home.argb","scene-99.argb")) {
   val f=File(dir!!,name);assumeTrue(f.exists())
   DataInputStream(f.inputStream()).use {input ->
    val w=input.readInt();val h=input.readInt();val p=IntArray(w*h){input.readInt()}
    val detector=GameUiGeometryDetector(w,h,p)
    assertEquals(name,GameScreenKind.HOME,detector.screenKind())
    for(step in listOf(AutoScanStep.HAMMERS,AutoScanStep.OPEN_MOUNTS,AutoScanStep.OPEN_RESEARCH))assertNotNull("$name $step",detector.detect(step))
   }
  }
 }
}
