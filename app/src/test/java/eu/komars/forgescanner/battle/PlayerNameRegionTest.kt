package eu.komars.forgescanner.battle

import org.junit.Assert.*
import org.junit.Test

class PlayerNameRegionTest {
    @Test fun findsGenderBoundaryAfterNicknamesOfDifferentLengths() {
        for(length in listOf(20,47,113)) {
            val w=150;val h=20;val pixels=IntArray(w*h) { 0xff44495c.toInt() }
            for(x in 3 until length) for(y in 4..14) pixels[y*w+x]=0xffffffff.toInt()
            for(x in length+4..length+10) for(y in 3..15) pixels[y*w+x]=0xff50e5fa.toInt()
            assertEquals(length+2,PlayerNameRegion.rightEdge(w,h,pixels))
        }
    }
    @Test fun noGenderMarkerDoesNotTruncateAnArbitraryNickname() {
        val pixels=IntArray(100*20) { 0xffffffff.toInt() }
        assertEquals(100,PlayerNameRegion.rightEdge(100,20,pixels))
        assertEquals("Długi nick_123",BattleText.playerName(" Długi nick_123 ♂\n[KLAN]"))
        assertEquals("Żółw 77",BattleText.playerName("Żółw 77 ♀"))
        assertEquals("Gracz-XYZ",BattleText.playerName("Gracz-XYZ"))
    }
}
