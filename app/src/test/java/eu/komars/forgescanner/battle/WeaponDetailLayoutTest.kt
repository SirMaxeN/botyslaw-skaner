package eu.komars.forgescanner.battle

import eu.komars.forgescanner.model.PixelRegion
import org.junit.Assert.*
import org.junit.Test

class WeaponDetailLayoutTest {
    @Test fun readsFullPanelWidthAboveDamageInsteadOfOnlyTitle() {
        val p=PixelRegion(60,120,360,650);val damage=PixelRegion(100,370,300,390)
        val area=WeaponDetailLayout.readingArea(p,damage,420,800)!!
        assertTrue(area.left<100 && area.right>300)
        assertTrue(area.top<150 && area.bottom<damage.top)
        assertTrue(area.left>=p.left && area.right<=p.right)
    }
    @Test fun dismissalIsOutsideItemAndAvoidsProfileCloseBelow() {
        val p=PixelRegion(60,120,360,650)
        val target=WeaponDetailLayout.outsideArea(p,420,800)!!
        assertTrue(target.right<p.left || target.left>p.right)
        assertTrue(target.top>p.top && target.bottom<p.bottom)
    }
    @Test fun noOutsideSpaceOrInvalidDamageDoesNotProduceAGuessedTarget() {
        assertNull(WeaponDetailLayout.outsideArea(PixelRegion(0,0,420,800),420,800))
        assertNull(WeaponDetailLayout.readingArea(PixelRegion(60,120,360,650),PixelRegion(5,400,50,420),420,800))
    }
    @Test fun rangedMarkerMayWrapAndOtherHeaderParenthesesAreNotAWeaponType() {
        assertEquals(true,BattleText.weapon("Łuk\n( dystans )\nPoziom 12"))
        assertEquals(false,BattleText.weapon("Miecz\nPoziom (12)\nLegendarny"))
        assertEquals(true,BattleText.weapon("Łuk (dystans"))
    }
}
