package eu.komars.forgescanner.battle

import eu.komars.forgescanner.model.PixelRegion
import kotlin.math.abs

/** Structural proposals only. Artwork is not classified and no proposal authorizes a tap. */
class BattleSetupGeometry(private val width:Int, private val height:Int, private val pixels:IntArray) {
    data class Layout(val regions:Map<String,PixelRegion>,val profile:Boolean,val panel:PixelRegion?=null)
    private data class Part(val box:PixelRegion,val area:Int)
    private fun r(c:Int)=c ushr 16 and 255
    private fun g(c:Int)=c ushr 8 and 255
    private fun b(c:Int)=c and 255
    fun detect(requireEquipment:Boolean=false):Layout {
        if(width<100 || height<100 || pixels.size.toLong()!=width.toLong()*height || pixels.size>600000) return Layout(emptyMap(),false)
        val panels=components { c -> r(c) in 35..105 && g(c) in 40..115 && b(c) in 65..145 && b(c)-r(c) in 8..55 && abs(r(c)-g(c))<30 }
        val panel=panels.filter { it.box.width>width*.6 && it.box.height>width*.8 && it.area>it.box.width*it.box.height*.15 }.maxByOrNull { it.area }?.box
        // Weapon details are a compact, wide tooltip, not the tall player profile.
        val tooltip=panels.filter { part ->
            val q=part.box
            q.left>width*.025 && q.right<width*.975 && q.width>width*.55 && q.height>width*.16 && q.height<width*.70 &&
                q.width.toDouble()/q.height in 1.5..5.5 && part.area>q.width*q.height*.50
        }.singleOrNull()?.box
        if(tooltip!=null) return Layout(emptyMap(),false,tooltip)
        if(panel==null) {
            val info=components { c -> r(c)<40 && g(c)<40 && b(c)<40 }.filter { part ->
                val q=part.box
                q.width in 5..(width*.055).toInt() && q.height in 5..(width*.055).toInt() &&
                    q.width.toDouble()/q.height in .7..1.3 && part.area.toDouble()/(q.width*q.height) in .38.. .88 &&
                    q.left<width*.30 && (q.top>height*.45 || height<width*.9) && lightSurround(q) && whiteGlyph(q)
            }.singleOrNull()?.box
            if(info!=null && !requireEquipment) return Layout(mapOf("open" to expand(info,width*.015)),false)
        }
        val colorCards=components { c -> r(c)>115 && g(c) in 60..(if(requireEquipment)245 else 225) && b(c) in (if(requireEquipment)0 else 30)..195 && r(c)>g(c)+12 && r(c)>b(c)+10 }
        val outlines=components { c -> r(c)<45 && g(c)<45 && b(c)<45 }
        val raw=(colorCards+outlines).filter { it.box.width in (width*.065).toInt()..(width*.21).toInt() &&
            it.box.height in (width*.065).toInt()..(width*.23).toInt() && it.box.width.toDouble()/it.box.height in .50..1.6 &&
            it.area>it.box.width*it.box.height*.025 }.map { it.box }.filter { panel==null || contains(panel,it) }
        val cards=mutableListOf<PixelRegion>()
        for(q in raw.sortedByDescending { it.width*it.height }) {
            if(cards.none {
                val overlap=maxOf(0,minOf(it.right,q.right)-maxOf(it.left,q.left))*maxOf(0,minOf(it.bottom,q.bottom)-maxOf(it.top,q.top))
                (abs((it.left+it.right)-(q.left+q.right))<width*.08 && abs((it.top+it.bottom)-(q.top+q.bottom))<width*.08) ||
                    (requireEquipment && overlap>minOf(it.width*it.height,q.width*q.height)*.75)
            }) cards+=q
        }
        val groups=cards.map { anchor -> cards.filter { abs(it.top-anchor.top)<width*.03 }.sortedBy { it.left } }
            .filter { row -> row.size==5 && row.zipWithNext().all { (a,b) -> b.left>a.left+width*.08 && b.left-a.left<width*.22 } }
        val topRow=groups.sortedBy { it.minOf { q -> q.top } }.firstOrNull { row -> panel==null ||
            row.first().top in (panel.top+panel.width*.43).toInt()..(panel.top+panel.width*.85).toInt() }
            ?: return Layout(emptyMap(),false,panel)
        fun median(values:List<Int>)=values.sorted()[values.size/2]
        val pitch=median(topRow.zipWithNext().map { (a,b) -> b.left-a.left })
        if(pitch<width*.09 || pitch>width*.22) return Layout(emptyMap(),false,panel)
        val slotWidth=(pitch*.84).toInt()
        val slotLeft=median(topRow.mapIndexed { i,q -> q.left-i*pitch })
        val rowBottom=median(topRow.map { it.top })+slotWidth
        val below=cards.filter { q -> q.top>rowBottom && q.top-rowBottom<width*.10 &&
            (0..2).any { col -> abs(q.left-(slotLeft+col*pitch))<pitch*.22 } }
        val lower=below.map { anchor -> below.filter { abs(it.top-anchor.top)<width*.03 } }
            .filter { row -> row.size>=2 && row.any { abs(it.left-slotLeft)<pitch*.22 } }
            .minByOrNull { it.minOf { q -> q.top } } ?: return Layout(emptyMap(),false,panel)
        val lowerTop=median(lower.map { it.top })
        val first=PixelRegion(slotLeft,lowerTop,slotLeft+slotWidth,lowerTop+slotWidth)
        if(panel==null) {
            // A home-screen i must be a small isolated black disk on the light equipment panel.
            val candidates=components { c -> r(c)<40 && g(c)<40 && b(c)<40 }.filter { part ->
                val q=part.box
                q.width in 5..(width*.055).toInt() && q.height in 5..(width*.055).toInt() &&
                    q.width.toDouble()/q.height in .7..1.3 && q.left in (first.left-width*.04).toInt()..(first.left+first.width*.5).toInt() &&
                    q.top in first.bottom..(first.bottom+first.height*.65).toInt() && lightSurround(q) && whiteGlyph(q)
            }
            return Layout(candidates.singleOrNull()?.box?.let { mapOf("open" to expand(it,width*.015)) } ?: emptyMap(),false)
        }
        val left=panel.left; val top=panel.top; val pw=panel.width
        val regions=linkedMapOf<String,PixelRegion>()
        regions["weapon"]=first
        regions["name"]=box(left+pw*.20,top+pw*.025,left+pw*.60,top+pw*.095)
        regions["header"]=box(left+pw*.50,top+pw*.055,left+pw*.98,top+pw*.235)
        // Header fallbacks include the label to leave enough height for tiny game text.
        regions["damage"]=box(left+pw*.62,top+pw*.115,left+pw*.97,top+pw*.175)
        regions["health"]=box(left+pw*.60,top+pw*.18,left+pw*.97,top+pw*.225)
        for(i in 0..2) {
            val cx=first.left+first.width*.32+i*pw*.103
            val cy=first.bottom+pw*.09
            regions["skill${i+1}"]=box(cx-pw*.04,cy-pw*.04,cx+pw*.04,cy+pw*.04)
        }
        for(i in 0..2) {
            val cx=first.left+first.width*.32+(i+3)*pw*.103
            val cy=first.bottom+pw*.09
            regions["pet${i+1}"]=box(cx-pw*.04,cy-pw*.04,cx+pw*.04,cy+pw*.04)
        }
        regions["mount"]=box(first.left+pw*.49,first.top+first.height*.10,panel.right-pw*.055,first.bottom-first.height*.12)
        regions["fairy"]=box(left+pw*.80,first.bottom+pw*.045,left+pw*.92,first.bottom+pw*.175)
        val bonusTop=first.bottom+pw*.17
        val buttons=components { c -> (r(c)>180 && g(c)<135 && b(c)<150) || (r(c)<70 && g(c)>100 && b(c)>160) }
            .map { it.box }.filter { it.width>pw*.14 && it.height>pw*.055 && it.top>bonusTop && it.right>left && it.left<panel.right }
        val bottom=minOf(bonusTop+pw*.32,panel.bottom-pw*.075,buttons.minOfOrNull { it.top }?.minus(pw*.018) ?: Double.POSITIVE_INFINITY)
        if(bottom-bonusTop>pw*.09) regions["bonuses"]=box(left+pw*.08,bonusTop,left+pw*.94,bottom)
        return Layout(regions,true,panel)
    }
    private fun whiteGlyph(q:PixelRegion):Boolean {
        var count=0
        for(y in q.top until q.bottom) for(x in q.left until q.right) if(r(pixels[y*width+x])>180 && g(pixels[y*width+x])>180 && b(pixels[y*width+x])>180) count++
        return count>=2 && count<q.width*q.height*.45
    }
    private fun lightSurround(q:PixelRegion):Boolean {
        val padding=maxOf(2,q.width/3); var count=0
        for((x,y) in listOf(q.left-padding to q.top,q.right+padding to q.top,q.left-padding to q.bottom,q.right+padding to q.bottom)) {
            if(x !in 0 until width || y !in 0 until height) return false
            val c=pixels[y*width+x]; if(minOf(r(c),g(c),b(c))>210) count++
        }
        return count==4
    }
    private fun contains(a:PixelRegion,q:PixelRegion)=q.left>=a.left && q.right<=a.right && q.top>=a.top && q.bottom<=a.bottom
    private fun expand(q:PixelRegion,p:Double)=box(q.left-p,q.top-p,q.right+p,q.bottom+p)
    private fun box(l:Double,t:Double,r:Double,b:Double)=PixelRegion(l.toInt().coerceIn(0,width-1),t.toInt().coerceIn(0,height-1),r.toInt().coerceIn(1,width),b.toInt().coerceIn(1,height))
    private fun components(match:(Int)->Boolean):List<Part> {
        val visited=BooleanArray(pixels.size); val queue=IntArray(pixels.size); val parts=mutableListOf<Part>()
        for(start in pixels.indices) {
            if(visited[start] || !match(pixels[start])) continue
            var head=0; var tail=1; queue[0]=start; visited[start]=true
            var l=start%width; var right=l; var t=start/width; var bottom=t
            while(head<tail) {
                val at=queue[head++]; val x=at%width; val y=at/width
                l=minOf(l,x);right=maxOf(right,x);t=minOf(t,y);bottom=maxOf(bottom,y)
                fun add(next:Int) { if(!visited[next] && match(pixels[next])) { visited[next]=true;queue[tail++]=next } }
                if(x>0) add(at-1);if(x<width-1) add(at+1);if(y>0) add(at-width);if(y<height-1) add(at+width)
            }
            if(tail>=4) parts+=Part(PixelRegion(l,t,right+1,bottom+1),tail)
        }
        return parts
    }
}


