package eu.komars.forgescanner.model

import org.junit.Assert.*
import org.junit.Test

class OverlayPlacementTest {
    @Test fun singleMountViewportFitsCompactPanelOnDensePhone() {
        assertNull(OverlayPlacement.find(2340,708,1575,684,72,72,18))
        val y = OverlayPlacement.find(2340,708,1575,480,72,72,18)
        assertNotNull(y)
        assertTrue(y!!+72+480<=708)
    }
    @Test fun actualHeightCanFitWhereOldFixedReserveRefusedBothSides() {
        val y=OverlayPlacement.find(2340,708,1575,600,72,72,18)
        assertNotNull(y)
    }
    @Test fun neitherCandidateCanCoverTheListOrSystemBars() {
        assertNull(OverlayPlacement.find(1000,100,900,180,30,30,10))
        assertEquals(580,OverlayPlacement.find(1000,100,600,180,30,30,10))
    }
}
