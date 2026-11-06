package eu.komars.forgescanner.model

import eu.komars.forgescanner.automation.AutoScanStep
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.DataInputStream
import java.io.File

class GameUiStableAnchorsTest {
    private data class Profile(val width: Int, val height: Int, val pixels: IntArray) {
        fun detector() = GameUiGeometryDetector(width, height, pixels)
        fun copy() = Profile(width, height, pixels.copyOf())
        fun paint(box: PixelRegion, color: Int) {
            for (y in box.top.coerceAtLeast(0) until box.bottom.coerceAtMost(height))
                for (x in box.left.coerceAtLeast(0) until box.right.coerceAtMost(width)) pixels[y * width + x] = color
        }
    }
    private fun profile(name: String): Profile {
        val directory = System.getenv("SCANNER_UI_PROFILES")
        val file = directory?.let { File(it, "$name.gray") }
        assumeTrue(file?.isFile == true)
        return DataInputStream(file!!.inputStream().buffered()).use { input ->
            val width = input.readInt(); val height = input.readInt()
            Profile(width, height, IntArray(width * height) { input.readInt() })
        }
    }
    private fun contains(box: PixelRegion, x: Int, y: Int) = x in box.left until box.right && y in box.top until box.bottom
    private fun disable(source: Profile, step: AutoScanStep): Profile {
        val detector = source.detector()
        val price = detector.detect(step)!!.region
        val cx = (price.left + price.right) / 2; val cy = (price.top + price.bottom) / 2
        val button = detector.forbiddenActionAreas().first { contains(it, cx, cy) }
        val changed = source.copy()
        for (y in button.top until button.bottom) for (x in button.left until button.right) {
            val value = changed.pixels[y * changed.width + x]
            val r = value ushr 16 and 255; val g = value ushr 8 and 255; val b = value and 255
            if (r < 90 && g > 110 && b > 170 && b > g * 1.08) changed.pixels[y * changed.width + x] = 0xffd1d1d1.toInt()
            else if (y >= (button.top + button.bottom) / 2 && r > 235 && g > 235 && b > 235)
                changed.pixels[y * changed.width + x] = 0xffff5a5f.toInt()
        }
        return changed
    }

    @Test fun realFiveMountsWithSeventeenKeysAndDisabledRedPriceRemainRecognizable() {
        val detector = profile("changed-mounts-confirmed").detector()
        assertEquals(GameScreenKind.MOUNTS, detector.screenKind())
        val keys = detector.detect(AutoScanStep.MOUNT_KEYS)
        val price = detector.detect(AutoScanStep.MOUNT_COST)
        assertNotNull(keys); assertNotNull(price); assertNotNull(detector.detect(AutoScanStep.CLOSE_MOUNTS))
        assertTrue(detector.forbiddenActionAreas().any { contains(it,
            (price!!.region.left + price.region.right) / 2, (price.region.top + price.region.bottom) / 2) })
    }

    @Test fun enabledAndUnaffordableMountAndSkillControlsShareTheSameChrome() {
        for ((name, cost, kind, currency) in listOf(
            listOf("one-mount", AutoScanStep.MOUNT_COST, GameScreenKind.MOUNTS, AutoScanStep.MOUNT_KEYS),
            listOf("research", AutoScanStep.SKILL_COST, GameScreenKind.SKILLS, AutoScanStep.SKILL_TICKETS))) {
            val base = profile(name as String)
            val changed = disable(base, cost as AutoScanStep).detector()
            assertEquals(kind, changed.screenKind())
            assertNotNull(changed.detect(currency as AutoScanStep))
            val found = changed.detect(cost)
            assertNotNull(found)
            assertTrue(changed.forbiddenActionAreas().any { contains(it,
                (found!!.region.left + found.region.right) / 2, (found.region.top + found.region.bottom) / 2) })
        }
    }

    @Test fun inventoryArtworkIncludingFakeBarsAndASecondXDoesNotIdentifyTheMountScreen() {
        val source = profile("one-mount")
        val baseline = source.detector()
        val changed = source.copy()
        changed.paint(PixelRegion(20, 243, 340, 520), -1)
        for (row in 0..3) for (column in 0..4) {
            changed.paint(PixelRegion(25 + column * 63, 247 + row * 60, 75 + column * 63, 289 + row * 60),
                listOf(0xff00a3ff.toInt(), 0xffaaaaaa.toInt(), 0xffffaa33.toInt())[(row + column) % 3])
        }
        // A fake currency-shaped gray/orange/white group inside inventory is irrelevant.
        changed.paint(PixelRegion(145, 274, 210, 288), 0xff777777.toInt())
        changed.paint(PixelRegion(134, 272, 147, 289), 0xffffaa33.toInt())
        changed.paint(PixelRegion(175, 277, 195, 285), -1)
        // An equally convincing red X in artwork must not compete with footer chrome.
        val x = baseline.detect(AutoScanStep.CLOSE_MOUNTS)!!.region
        for (dy in 0 until x.height) for (dx in 0 until x.width)
            changed.pixels[(400 + dy) * changed.width + 160 + dx] = source.pixels[(x.top + dy) * source.width + x.left + dx]
        val detector = changed.detector()
        assertEquals(GameScreenKind.MOUNTS, detector.screenKind())
        for (step in listOf(AutoScanStep.MOUNT_KEYS, AutoScanStep.MOUNT_COST, AutoScanStep.CLOSE_MOUNTS))
            assertEquals(step.name, baseline.detect(step)!!.region, detector.detect(step)!!.region)
    }

    @Test fun currencyDigitAbsenceCannotHideAnotherFixedFieldOrItsScreen() {
        val source = profile("one-mount")
        val price = source.detector().detect(AutoScanStep.MOUNT_COST)!!.region
        val changed = source.copy()
        for (y in price.top until price.bottom) for (x in price.left until price.right) {
            val color = changed.pixels[y * changed.width + x]
            if ((color ushr 16 and 255) > 235 && (color ushr 8 and 255) > 235 && (color and 255) > 235)
                changed.pixels[y * changed.width + x] = 0xff0088dd.toInt()
        }
        val detector = changed.detector()
        assertEquals(GameScreenKind.MOUNTS, detector.screenKind())
        assertNotNull(detector.detect(AutoScanStep.MOUNT_KEYS))
        assertNotNull(detector.detect(AutoScanStep.CLOSE_MOUNTS))
    }

    @Test fun petsScreenDoesNotDependOnIncubatorOrInventoryPictures() {
        val source = profile("pets-top")
        source.paint(PixelRegion(0, 220, source.width, 430), -1)
        source.paint(PixelRegion(0, 520, source.width, 650), -1)
        val detector = source.detector()
        assertEquals(GameScreenKind.PETS, detector.screenKind())
        assertNotNull(detector.detect(AutoScanStep.OPEN_SKILLS))
        assertNotNull(detector.detect(AutoScanStep.CLOSE_RESEARCH))
    }

    @Test fun technologyIsResearchChromeAndOnlyItsTabsAndCloseRemainNavigation() {
        val source = profile("research")
        val base = source.detector()
        val first = base.detect(AutoScanStep.OPEN_SKILLS)!!.region
        val second = base.detect(AutoScanStep.OPEN_PETS)!!.region
        val pitch = second.left - first.left
        val strip = PixelRegion(first.left - 3, first.top - 3, first.left - 3 + pitch * 3, first.bottom + 3)
        for (y in strip.top until strip.bottom) for (x in strip.left until strip.right) {
            val c = source.pixels[y * source.width + x]
            if ((c ushr 16 and 255) < 90 && (c ushr 8 and 255) > 110 && (c and 255) > 170)
                source.pixels[y * source.width + x] = 0xff43506e.toInt()
        }
        source.paint(PixelRegion(strip.left + pitch * 2 + 4, strip.top + 4, strip.right - 4, strip.bottom - 4), 0xff00a3ff.toInt())
        val detector = source.detector()
        assertEquals(GameScreenKind.TECHNOLOGY, detector.screenKind())
        for (step in listOf(AutoScanStep.OPEN_SKILLS, AutoScanStep.OPEN_PETS, AutoScanStep.CLOSE_RESEARCH)) {
            val navigation = detector.detect(step)!!.region
            assertTrue(detector.forbiddenActionAreas().none { contains(it,
                (navigation.left + navigation.right) / 2, (navigation.top + navigation.bottom) / 2) })
        }
        assertTrue(detector.forbiddenActionAreas().any { contains(it, 180, 350) })
        assertNull(detector.detect(AutoScanStep.SKILL_TICKETS))
    }

    @Test fun homeHammerAndResearchAnchorsDoNotRequireReadableMountArtwork() {
        val source = profile("phone-before")
        val card = source.detector().detect(AutoScanStep.OPEN_MOUNTS)!!.region
        source.paint(PixelRegion(card.left - 4, card.top - 4, card.right + 4, card.bottom + 4), -1)
        val detector = source.detector()
        assertEquals(GameScreenKind.HOME, detector.screenKind())
        assertNotNull(detector.detect(AutoScanStep.HAMMERS))
        assertNotNull(detector.detect(AutoScanStep.OPEN_RESEARCH))
        assertTrue(detector.forbiddenActionAreas().isNotEmpty())
    }
}
