package eu.komars.forgescanner.model

import org.junit.Assert.*
import org.junit.Test

class TouchShieldGeometryTest {
    private val display = PixelRegion(0, 0, 100, 180)
    private fun contains(rect: PixelRegion, x: Int, y: Int) = x in rect.left until rect.right && y in rect.top until rect.bottom
    private fun verify(holes: List<PixelRegion>) {
        val pieces = TouchShieldGeometry.partition(display, holes)
        assertTrue(pieces.all { it.width > 0 && it.height > 0 && it.left >= 0 && it.top >= 0 && it.right <= 100 && it.bottom <= 180 })
        for (y in 0 until 180) for (x in 0 until 100) {
            val expected = if (holes.any { contains(it, x, y) }) 0 else 1
            assertEquals("x=$x,y=$y", expected, pieces.count { contains(it, x, y) })
        }
    }
    @Test fun stopPanelAndNarrowGestureCorridorRemainTheOnlyHoles() {
        verify(listOf(PixelRegion(10, 10, 70, 35), PixelRegion(40, 40, 60, 150)))
    }
    @Test fun overlappingAndClippedHolesDoNotCreateGapsOrOverlappingWindows() {
        verify(listOf(PixelRegion(-10, -20, 50, 50), PixelRegion(30, 30, 110, 200)))
    }
    @Test fun closingGestureRestoresEveryPixelExceptStop() {
        verify(listOf(PixelRegion(0, 140, 100, 180)))
    }
    @Test fun noHolesIsOneRectangleAndOutOfBoundsHolesAreIgnored() {
        assertEquals(listOf(display), TouchShieldGeometry.partition(display, emptyList()))
        assertEquals(listOf(display), TouchShieldGeometry.partition(display, listOf(PixelRegion(120, 200, 140, 220))))
    }
    @Test fun continuedPointerReusesOneNarrowStrip() {
        val panel = PixelRegion(0, 0, 100, 30)
        val first = PixelRegion(45, 100, 55, 160)
        val second = PixelRegion(45, 55, 55, 110)
        val union = TouchShieldGeometry.gestureCorridor(display, panel, first, second)
        assertEquals(PixelRegion(45, 55, 55, 160), union)
        assertEquals(union, TouchShieldGeometry.gestureCorridor(display, panel, union, second))
        verify(listOf(panel, union!!))
    }
    @Test fun mergedStripCannotExposeStopOrLargeAreas() {
        val panel = PixelRegion(0, 0, 100, 30)
        assertNull(TouchShieldGeometry.gestureCorridor(display, panel,
            PixelRegion(40, 40, 60, 100), PixelRegion(40, 20, 60, 60)))
        assertNull(TouchShieldGeometry.gestureCorridor(display, panel, null, PixelRegion(0, 35, 100, 175)))
    }
    @Test fun disjointGestureDoesNotOpenAreaBetweenTaps() {
        assertEquals(PixelRegion(70, 140, 80, 150), TouchShieldGeometry.gestureCorridor(display,
            PixelRegion(0, 0, 100, 30), PixelRegion(10, 40, 20, 50), PixelRegion(70, 140, 80, 150)))
    }
}
