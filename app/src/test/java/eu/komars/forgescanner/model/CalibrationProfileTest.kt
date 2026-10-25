package eu.komars.forgescanner.model

import org.junit.Assert.*
import org.junit.Test

class CalibrationProfileTest {
    @Test fun scalesRegionAndRowsWithCompatibleCapturedFrame() {
        val profile = CalibrationProfile.fromPixels(PixelRegion(100, 400, 900, 1600), 1000, 2000, 5, 160)
        val projected = requireNotNull(profile.project(500, 1000))
        assertEquals(PixelRegion(50, 200, 450, 800), projected.region)
        assertEquals(80, projected.rowHeightPx)
        assertEquals(5, projected.columns)
    }

    @Test fun requiresRecalibrationAfterRotationButAcceptsSmallCaptureAspectChanges() {
        val profile = CalibrationProfile.fromPixels(PixelRegion(10, 100, 990, 1900), 1000, 2000)
        assertNull(profile.project(2000, 1000))
        assertNotNull(profile.project(1000, 1950))
        assertNull(profile.project(1000, 1700))
    }

    @Test fun rejectsOutOfFrameAndEmptyRegionsInsteadOfClippingWrongSelection() {
        assertThrows(IllegalArgumentException::class.java) {
            CalibrationProfile.fromPixels(PixelRegion(-1, 0, 50, 50), 100, 100)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CalibrationProfile.fromPixels(PixelRegion(20, 20, 20, 50), 100, 100)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CalibrationProfile.fromPixels(PixelRegion(0, 0, 100, 100), 100, 100, columns = 0)
        }
    }

    @Test fun corruptProfilesAndFramesCannotProduceCoordinates() {
        val profile = CalibrationProfile.fromPixels(PixelRegion(1, 1, 90, 90), 100, 100)
        assertNull(profile.copy(left = Float.NaN).project(100, 100))
        assertNull(profile.copy(right = 1.1f).project(100, 100))
        assertNull(profile.copy(rowHeightFraction = 0f).project(100, 100))
        assertNull(profile.project(0, 100))
        assertNull(profile.project(1, 1))
    }
}
