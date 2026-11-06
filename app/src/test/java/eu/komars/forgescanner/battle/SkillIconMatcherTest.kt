package eu.komars.forgescanner.battle

import java.io.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class SkillIconMatcherTest {
    private fun read(file:File):SkillIconTemplate=DataInputStream(BufferedInputStream(file.inputStream())).use {
        val w=it.readInt();val h=it.readInt();SkillIconTemplate(file.nameWithoutExtension,w,h,IntArray(w*h) { _ -> it.readInt() })
    }
    private fun trimmed(s:SkillIconTemplate):SkillIconTemplate {
        var l=s.width;var t=s.height;var r=0;var b=0
        for(y in 0 until s.height) for(x in 0 until s.width) if(s.pixels[y*s.width+x] ushr 24>220) { l=minOf(l,x);t=minOf(t,y);r=maxOf(r,x+1);b=maxOf(b,y+1) }
        return SkillIconTemplate(s.id,r-l,b-t,IntArray((r-l)*(b-t)) { i -> s.pixels[(t+i/(r-l))*s.width+l+i%(r-l)] })
    }
    private fun templates():List<SkillIconTemplate> {
        val dir=System.getenv("SKILL_ICON_FIXTURES");assumeTrue(dir!=null)
        return File(dir!!).listFiles()!!.filter { it.extension=="argb" }.map { trimmed(read(it)) }
    }
    @Test fun allEighteenDownloadedIconsMatchTheirOwnArtwork() {
        val refs=templates();assertEquals(18,refs.size)
        refs.forEach { s ->
            assertEquals(s.id,SkillIconMatcher.match(s.width,s.height,s.pixels.map { if(it ushr 24<220) 0xff644ba0.toInt() else it }.toIntArray(),0,0,s.width,s.height,refs)?.id)
        }
    }
    @Test fun realPolishProfileStampedeRecognizedWithoutItsName() {
        val refs=templates();val path=System.getenv("BATTLE_SETUP_PROFILES")?.split(';')?.firstOrNull();assumeTrue(path!=null)
        val frame=read(File(path!!));val layout=BattleSetupGeometry(frame.width,frame.height,frame.pixels).detect()
        val q=layout.regions.getValue("skill2")
        val match=SkillIconMatcher.match(frame.width,frame.height,frame.pixels,q.left,q.top,q.right,q.bottom,refs)
        assertEquals("Stampede",match?.id)
    }
    @Test fun blankFieldNeverBecomesASkill() {
        assertNull(SkillIconMatcher.match(40,40,IntArray(1600) { 0xff44495c.toInt() },4,4,36,36,templates()))
    }
}

