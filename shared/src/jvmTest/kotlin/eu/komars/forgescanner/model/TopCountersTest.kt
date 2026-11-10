package eu.komars.forgescanner.model

import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** User screenshots stay local (.local, not committed); the test is skipped when they are absent. */
class TopCountersTest {
    private fun load(name: String): Triple<Int, Int, IntArray>? {
        val file = File(System.getProperty("user.dir")).resolve("../.local/clan-tech/$name")
        if (!file.exists()) return null
        val image = ImageIO.read(file)
        return Triple(image.width, image.height, IntArray(image.width * image.height) { image.getRGB(it % image.width, it / image.width) })
    }

    @Test fun techTreeHasRedAndGreenVialsOnTheLeftAndGemsOnTheRight() {
        val (w, h, p) = load("tech-screen.png") ?: return
        val found = TopCounters.find(w, h, p).associateBy { it.icon }
        val red = found[TopCounters.Icon.RED]; val green = found[TopCounters.Icon.GREEN]; val gems = found[TopCounters.Icon.PINK]
        assertEquals(red?.region, TopCounters.forField(ResourceField.RED_VIALS, GameScreenKind.TECHNOLOGY, w, h, p))
        assertEquals(green?.region, TopCounters.forField(ResourceField.GREEN_VIALS, GameScreenKind.TECHNOLOGY, w, h, p))
        assertTrue(red != null && green != null && gems != null, "$found")
        assertTrue(red.region.top < green.region.top, "red above green")
        assertTrue(red.region.right < w / 2 && gems.region.left > w / 2, "$found")
        assertTrue(red.region.width > w / 7, "covers the number: ${red.region}")
    }

    @Test fun petsTabHasShellsOnTheLeftAndGemsOnTheRight() {
        val (w, h, p) = load("pets-screen.png") ?: return
        val shells = TopCounters.forField(ResourceField.SHELLS, GameScreenKind.PETS, w, h, p)
        val gems = TopCounters.forField(ResourceField.GEMS, GameScreenKind.PETS, w, h, p)
        assertTrue(shells != null && gems != null, "$shells $gems")
        assertTrue(shells.right < w / 2 && shells.width > w / 8 && gems.left > w / 2, "$shells $gems")
        assertEquals(null, TopCounters.forField(ResourceField.RED_VIALS, GameScreenKind.PETS, w, h, p), "vials only on the tech tree")
    }

    @Test fun realHomeWalletContainsBothNumbersWithoutOverlapping() {
        val (w,h,p)=load("home-wallet-94.png")?:return
        val detector=GameUiGeometryDetector(w,h,p)
        assertEquals(GameScreenKind.HOME,detector.screenKind())
        val coins=detector.detect(eu.komars.forgescanner.automation.AutoScanStep.COINS)?.region
        val gems=detector.detect(eu.komars.forgescanner.automation.AutoScanStep.GEMS)?.region
        assertTrue(coins!=null&&gems!=null,"$coins $gems")
        fun covers(r:PixelRegion,x:Int,y:Int)=x in r.left until r.right && y in r.top until r.bottom
        assertTrue(covers(coins,196,62)&&covers(coins,248,62),"$coins")
        assertTrue(covers(gems,281,62)&&covers(gems,335,62),"$gems")
        assertTrue(coins.right<=gems.left,"$coins $gems")
    }

    @Test fun homeBarWithoutPillsSplitsCrownAndGemCounters() {
        val w = 360; val h = 720; val p = IntArray(w * h) { 0xFF000000.toInt() }
        fun box(l: Int, t: Int, s: Int, c: Int) { for (y in t until t + s) for (x in l until l + s) p[y * w + x] = c }
        box(40, 40, 16, 0xFFF0C030.toInt()) // the player's power (yellow swords) under the nick — not coins
        box(60, 42, 40, 0xFFFFFFFF.toInt()) // "788m"
        box(215, 18, 16, 0xFFF0C030.toInt()) // crown
        box(240, 20, 30, 0xFFFFFFFF.toInt()) // "2.12m"
        box(290, 18, 16, 0xFFF050C8.toInt()) // gem
        box(330, 20, 10, 0xFFFFFFFF.toInt()) // "7"
        val found = TopCounters.find(w, h, p).associateBy { it.icon }
        val coins = TopCounters.forField(ResourceField.COINS, GameScreenKind.HOME, w, h, p)!!; val gems = found.getValue(TopCounters.Icon.PINK).region
        assertTrue(coins.left <= 215 && coins.right in 271..289, "$coins")
        assertTrue(gems.left <= 290 && gems.right >= 340, "$gems")
    }
}
