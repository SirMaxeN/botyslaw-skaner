package eu.komars.forgescanner.model

import eu.komars.forgescanner.automation.AutoScanStep
import org.junit.Assert.*
import org.junit.Test

class GameUiGeometryDetectorTest {
    @Test fun blankOrWrongAspectScreensNeverProduceNavigationOrResourceGuesses() {
        for((width,height)in listOf(360 to 780,780 to 360)) {
            val detector=GameUiGeometryDetector(width,height,IntArray(width*height){-1})
            assertEquals(GameScreenKind.UNKNOWN,detector.screenKind())
            AutoScanStep.entries.forEach { assertNull(detector.detect(it)) }
        }
    }
    @Test fun blankScreensNeverGuessInventoryViewports() {
        val detector=GameUiGeometryDetector(360,780,IntArray(360*780){-1})
        assertNull(detector.detect(AutoScanStep.MOUNT_LIST));assertNull(detector.detect(AutoScanStep.PET_LIST))
    }
    @Test fun optionalPrivateProfilesHaveIndependentAnchorsAndNumericHeadroom() {
        val directory=System.getenv("SCANNER_UI_PROFILES")
        org.junit.Assume.assumeTrue(directory!=null)
        val cases=listOf("phone-before" to GameScreenKind.HOME,"one-mount" to GameScreenKind.MOUNTS,
            "research" to GameScreenKind.SKILLS,"pets-top" to GameScreenKind.PETS)
        for((name,kind)in cases) {
            java.io.DataInputStream(java.io.File(directory,"$name.gray").inputStream().buffered()).use { input ->
                val width=input.readInt();val height=input.readInt();val pixels=IntArray(width*height){input.readInt()}
                val detector=GameUiGeometryDetector(width,height,pixels)
                assertEquals(name,kind,detector.screenKind())
                val steps=when(kind) {
                    GameScreenKind.HOME->listOf(AutoScanStep.HAMMERS,AutoScanStep.OPEN_MOUNTS,AutoScanStep.OPEN_RESEARCH)
                    GameScreenKind.MOUNTS->listOf(AutoScanStep.MOUNT_KEYS,AutoScanStep.MOUNT_COST,AutoScanStep.CLOSE_MOUNTS)
                    GameScreenKind.SKILLS->listOf(AutoScanStep.SKILL_TICKETS,AutoScanStep.SKILL_COST,AutoScanStep.OPEN_PETS)
                    else->listOf(AutoScanStep.OPEN_SKILLS,AutoScanStep.CLOSE_RESEARCH)
                }
                for(step in steps) {
                    val found=detector.detect(step)
                    assertNotNull("$name ${step.name}",found)
                    assertTrue(found!!.region.width>=if(step.target!=null)width*.08 else 12.0)
                    if(step.target!=null)assertEquals(found.region,found.container)
                }
                if(kind==GameScreenKind.HOME) {
                    val mount=detector.detect(AutoScanStep.OPEN_MOUNTS)!!.region
                    for(color in listOf(0xff00aaff.toInt(),0xffaa44dd.toInt(),0xff00aa66.toInt())) {
                        val changed=pixels.copyOf()
                        for(y in mount.top until mount.bottom)for(x in mount.left until mount.right)changed[y*width+x]=color
                        val alternate=GameUiGeometryDetector(width,height,changed)
                        assertEquals(GameScreenKind.HOME,alternate.screenKind())
                        assertEquals(mount,alternate.detect(AutoScanStep.OPEN_MOUNTS)!!.region)
                        val cx=(mount.left+mount.right)/2;val cy=(mount.top+mount.bottom)/2
                        assertTrue(alternate.forbiddenActionAreas().none { cx in it.left until it.right && cy in it.top until it.bottom })
                    }
                }
                if (kind == GameScreenKind.MOUNTS) assertNotNull(detector.detect(AutoScanStep.MOUNT_LIST))
                if (kind == GameScreenKind.PETS) assertNotNull(detector.detect(AutoScanStep.PET_LIST))
            }
        }
    }
    @Test fun optionalPrivateNumericProposalsCoverWholeBarsAndCurrencyIcons() {
        val directory=System.getenv("SCANNER_UI_PROFILES")
        org.junit.Assume.assumeTrue(directory!=null)
        val cases=listOf(
            Triple("phone-before",AutoScanStep.HAMMERS,PixelRegion(137,643,222,662)),
            Triple("one-mount",AutoScanStep.MOUNT_KEYS,PixelRegion(142,208,212,229)),
            Triple("one-mount",AutoScanStep.MOUNT_COST,PixelRegion(160,562,204,578)),
            Triple("research",AutoScanStep.SKILL_TICKETS,PixelRegion(8,51,85,75)),
            Triple("research",AutoScanStep.SKILL_COST,PixelRegion(155,604,211,624)))
        for((name,step,container)in cases) {
            java.io.DataInputStream(java.io.File(directory,"$name.gray").inputStream().buffered()).use { input ->
                val width=input.readInt();val height=input.readInt();val pixels=IntArray(width*height){input.readInt()}
                val found=GameUiGeometryDetector(width,height,pixels).detect(step)
                assertNotNull("$name ${step.name}",found)
                val region=found!!.region
                assertTrue("$step left edge clips its icon/base",region.left<=container.left)
                assertTrue("$step top edge clips its icon/base",region.top<=container.top)
                assertTrue("$step right edge clips its bar",region.right>=container.right)
                assertTrue("$step bottom edge clips its icon/base",region.bottom>=container.bottom)
                assertTrue("$step needs horizontal room for larger values",region.width>=width*.20)
                if(step==AutoScanStep.HAMMERS) {
                    assertTrue("Only the lower anvil base belongs in OCR",region.top>height*.80)
                    assertTrue("The forge level caption must stay outside the counter",region.right<width*.66)
                }
            }
        }
    }

}
