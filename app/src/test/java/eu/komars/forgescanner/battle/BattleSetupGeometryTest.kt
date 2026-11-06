package eu.komars.forgescanner.battle

import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.io.DataInputStream
import java.io.BufferedInputStream

class BattleSetupGeometryTest {
    private data class StoredFrame(val width:Int,val height:Int,val pixels:IntArray)
    private fun readFrame(path:String):StoredFrame = DataInputStream(BufferedInputStream(File(path).inputStream())).use { input ->
        val width=input.readInt();val height=input.readInt()
        require(width>=100 && height>=100 && width.toLong()*height<=600000)
        StoredFrame(width,height,IntArray(width*height) { input.readInt() })
    }
    private val w=360;private val h=640
    private fun fill(p:IntArray,l:Int,t:Int,r:Int,b:Int,c:Int) { for(y in t until b) for(x in l until r) p[y*w+x]=c }
    private fun cards(p:IntArray,top:Int,left:Int=24) {
        for(i in 0..4) fill(p,left+i*61,top,left+i*61+42,top+44,0xffb08484.toInt())
        for(i in 0..2) fill(p,left+i*61,top+60,left+i*61+42,top+104,0xffffad35.toInt())
    }
    @Test fun profileUsesEquipmentAndModalAnchorsAndExcludesActionButtons() {
        val p=IntArray(w*h) { 0xff000000.toInt() }
        fill(p,12,35,348,610,0xff44495c.toInt());cards(p,210)
        fill(p,24,520,95,555,0xffff5158.toInt())
        val result=BattleSetupGeometry(w,h,p).detect()
        assertTrue(result.profile)
        assertEquals(24,result.regions.getValue("weapon").left)
        assertEquals(270,result.regions.getValue("weapon").top)
        assertTrue(result.regions.getValue("bonuses").bottom<520)
        assertFalse(result.regions.containsKey("open"))
    }
    private fun info(p:IntArray,cx:Int,cy:Int) {
        for(y in cy-5..cy+5) for(x in cx-5..cx+5) if((x-cx)*(x-cx)+(y-cy)*(y-cy)<=25) p[y*w+x]=0xff000000.toInt()
        fill(p,cx,cy-3,cx+1,cy-2,0xffffffff.toInt());fill(p,cx,cy-1,cx+1,cy+3,0xffffffff.toInt())
    }
    @Test fun homeRequiresUniqueBlackInfoDiskWithWhiteGlyphBelowEquipment() {
        val p=IntArray(w*h) { 0xfff4f4f4.toInt() };cards(p,40);info(p,33,158)
        val found=BattleSetupGeometry(w,h,p).detect()
        assertFalse(found.profile);assertTrue(found.regions.containsKey("open"))
        info(p,45,158)
        assertFalse(BattleSetupGeometry(w,h,p).detect().regions.containsKey("open"))
    }
    @Test fun blankOrUnsupportedFramesNeverProposeNavigation() {
        assertTrue(BattleSetupGeometry(w,h,IntArray(w*h)).detect().regions.isEmpty())
        assertTrue(BattleSetupGeometry(1,1,intArrayOf(0)).detect().regions.isEmpty())
        val p=IntArray(w*h) { 0xfff4f4f4.toInt() };cards(p,40)
        assertFalse(BattleSetupGeometry(w,h,p).detect().regions.containsKey("open"))
    }
    @Test fun privateUserScreensHaveProfileAndWeaponProposals() {
        val paths=System.getenv("BATTLE_SETUP_PROFILES")?.split(';').orEmpty()
        assumeTrue("Optional user screenshots",paths.isNotEmpty())
        paths.forEach { path ->
            val image=readFrame(path); val pixels=image.pixels
            val found=BattleSetupGeometry(image.width,image.height,pixels).detect()
            assertTrue("Profile not recognized: ${File(path).name}",found.profile)
            assertTrue(found.regions.containsKey("weapon"));assertTrue(found.regions.containsKey("bonuses"))
            val weapon=found.regions.getValue("weapon")
            assertTrue(weapon.left<image.width*.3 && weapon.top>image.height*.25)
            val viewport=found.regions.getValue("bonuses")
            assertTrue("Viewport must exclude empty space below the clipped list",viewport.height<image.width*.34)
            assertTrue(viewport.height>image.width*.20)
        }
    }
    @Test fun ownSkillTargetsHitTheSmallIconsRatherThanPetsOrGaps() {
        val path=System.getenv("BATTLE_SETUP_PROFILES")?.split(';')?.firstOrNull()
        assumeTrue("Optional own profile fixture",path!=null)
        val f=readFrame(path!!);val found=BattleSetupGeometry(f.width,f.height,f.pixels).detect()
        val expected=intArrayOf(110,150,185)
        for(i in 0..2) {
            val q=found.regions.getValue("skill${i+1}")
            assertTrue("Skill ${i+1} misses icon",kotlin.math.abs((q.left+q.right)/2-expected[i])<=10)
            assertTrue(q.top>=470 && q.bottom<=505)
            assertTrue("Skill must be left of pets",q.right<210)
        }
    }
    @Test fun privateUserHomeScreenProposesInfo() {
        val path=System.getenv("BATTLE_SETUP_HOME")
        assumeTrue("Optional home screenshot",!path.isNullOrBlank())
        val image=readFrame(path!!);val pixels=image.pixels
        val found=BattleSetupGeometry(image.width,image.height,pixels).detect()
        assertTrue("Home info not found",found.regions.containsKey("open"))
    }
    @Test fun compactWeaponTooltipIsNotAProfileAndKeepsHeaderAboveDamage() {
        val p=IntArray(w*h) { 0xff15211b.toInt() }
        fill(p,20,180,340,302,0xff44495c.toInt())
        val result=BattleSetupGeometry(w,h,p).detect()
        assertFalse(result.profile)
        assertEquals(20,result.panel!!.left)
        assertEquals(302,result.panel!!.bottom)
        val nav=IntArray(w*h) { 0xff15211b.toInt() };fill(nav,0,550,w,640,0xff44495c.toInt())
        assertNull("Full-width navigation is not an item tooltip",BattleSetupGeometry(w,h,nav).detect().panel)
    }
    @Test fun suppliedWeaponTooltipProposesFullHeaderAndOutsideDismissal() {
        val path=System.getenv("BATTLE_SETUP_WEAPON")
        assumeTrue("Optional weapon screenshot",!path.isNullOrBlank())
        val image=readFrame(path!!)
        val result=BattleSetupGeometry(image.width,image.height,image.pixels).detect()
        assertFalse(result.profile)
        val panel=requireNotNull(result.panel) { "Weapon tooltip not recognized" }
        assertTrue(panel.left in 15..30 && panel.right in 335..350)
        assertTrue(panel.top in 60..75 && panel.bottom in 185..195)
        val damage=eu.komars.forgescanner.model.PixelRegion(107,107,220,120)
        val reading=WeaponDetailLayout.readingArea(panel,damage,image.width,image.height)!!
        assertTrue(reading.left<30 && reading.right>330 && reading.bottom<107)
        val outside=WeaponDetailLayout.outsideArea(panel,image.width,image.height)!!
        assertTrue(outside.right<panel.left || outside.left>panel.right || outside.bottom<panel.top || outside.top>panel.bottom)
    }

    @Test fun fullPhoneHomeFindsInfoWithoutDependingOnInventoryRowArtwork() {
        val path=System.getenv("BATTLE_SETUP_FULL_HOME")
        assumeTrue("Optional full phone screenshot",!path.isNullOrBlank())
        val image=readFrame(path!!)
        val found=BattleSetupGeometry(image.width,image.height,image.pixels).detect()
        val info=requireNotNull(found.regions["open"]) { "Info disk missing on full home screen" }
        assertTrue((info.left+info.right)/2 in 20..50)
        assertTrue((info.top+info.bottom)/2 in 770..810)
    }

}


