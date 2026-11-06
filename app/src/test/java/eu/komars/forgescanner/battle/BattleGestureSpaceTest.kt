package eu.komars.forgescanner.battle

import eu.komars.forgescanner.model.OverlayPlacement
import eu.komars.forgescanner.model.PixelRegion
import eu.komars.forgescanner.model.TouchShieldGeometry
import org.junit.Assert.*
import org.junit.Test

class BattleGestureSpaceTest {
    @Test fun stalePanelHeightAndSmallGapRejectScrollButSettledPaddedPlacementPermitsIt() {
        val display=PixelRegion(0,0,1080,2340)
        val corridor=PixelRegion(500,1180,541,1521)
        val oldY=OverlayPlacement.find(2340,1200,1500,400,0,20,8)!!
        assertNull(TouchShieldGeometry.gestureCorridor(display,PixelRegion(8,oldY,800,oldY+440),null,corridor))
        val newY=OverlayPlacement.find(2340,corridor.top-24,corridor.bottom+24,440,0,20,32)!!
        val panel=PixelRegion(8,newY,800,newY+440)
        assertNotNull(TouchShieldGeometry.gestureCorridor(display,panel,null,corridor))
        assertTrue(panel.bottom<corridor.top)
    }
    @Test fun protectingOnlyGestureCanHideListButProtectingWholeViewportKeepsItVisible() {
        val listTop=1200;val listBottom=1510;val panelHeight=440
        val narrowY=OverlayPlacement.find(2340,1280,1460,panelHeight,0,20,32)!!
        assertTrue(narrowY+panelHeight>listTop)
        val fullY=OverlayPlacement.find(2340,listTop-24,listBottom+24,panelHeight,0,20,32)!!
        assertTrue(fullY+panelHeight<listTop)
    }

}
