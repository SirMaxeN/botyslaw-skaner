package eu.komars.forgescanner.battle

import kotlin.math.abs
data class SkillIconTemplate(val id:String,val width:Int,val height:Int,val pixels:IntArray)
data class SkillIconMatch(val id:String,val error:Double,val margin:Double)
/** Alpha-masked artwork comparison, independent of translated tooltip text and rarity background. */
object SkillIconMatcher {
    fun match(width:Int,height:Int,pixels:IntArray,left:Int,top:Int,right:Int,bottom:Int,templates:List<SkillIconTemplate>):SkillIconMatch? {
        if(width*height!=pixels.size || right<=left || bottom<=top) return null
        val low=IntArray(3) { 255 };val high=IntArray(3)
        for(y in top.coerceAtLeast(0) until bottom.coerceAtMost(height)) for(x in left.coerceAtLeast(0) until right.coerceAtMost(width)) {
            val q=pixels[y*width+x]
            for(c in 0..2) { val v=q ushr (c*8) and 255;low[c]=minOf(low[c],v);high[c]=maxOf(high[c],v) }
        }
        if((0..2).maxOf { high[it]-low[it] }<60) return null
        val scores=templates.map { ref ->
            var best=Double.POSITIVE_INFINITY
            val silhouette=ref.pixels.filter { it ushr 24>=220 }.all { (it ushr 16 and 255)+(it ushr 8 and 255)+(it and 255)<90 }
            for(scale in doubleArrayOf(.5,.65,.8,1.0,1.2,1.4)) for(dx in doubleArrayOf(-.24,-.12,0.0,.12,.24)) for(dy in doubleArrayOf(-.24,-.12,0.0,.12,.24)) {
                val w=(right-left)*scale;val h=w*ref.height/ref.width
                val cx=(left+right)/2.0+dx*(right-left);val cy=(top+bottom)/2.0+dy*(bottom-top)
                var error=0.0;var count=0
                var sa=0.0;var sb=0.0;var saa=0.0;var sbb=0.0;var sab=0.0
                val corners=doubleArrayOf(-.45,.45).flatMap { ox -> doubleArrayOf(-.45,.45).map { oy -> pixels[(cy+oy*h).toInt().coerceIn(0,height-1)*width+(cx+ox*w).toInt().coerceIn(0,width-1)] } }
                val br=corners.sumOf { it ushr 16 and 255 }/4;val bg=corners.sumOf { it ushr 8 and 255 }/4;val bb=corners.sumOf { it and 255 }/4
                val background=(255 shl 24) or (br shl 16) or (bg shl 8) or bb
                for(y in 0 until 20) for(x in 0 until 24) {
                    val raw=ref.pixels[((y+.5)*ref.height/20).toInt()*ref.width+((x+.5)*ref.width/24).toInt()]
                    if(raw ushr 24<220 && !silhouette) continue
                    val c=if(raw ushr 24<220) background else raw
                    val px=(cx-w/2+(x+.5)*w/24).toInt();val py=(cy-h/2+(y+.5)*h/20).toInt()
                    if(px !in 0 until width || py !in 0 until height) { error+=255;count++;continue }
                    val q=pixels[py*width+px]
                    val color=(abs((c ushr 16 and 255)-(q ushr 16 and 255))+abs((c ushr 8 and 255)-(q ushr 8 and 255))+abs((c and 255)-(q and 255)))/3.0
                    error+=color;count++
                    val a=((c ushr 16 and 255)+(c ushr 8 and 255)+(c and 255))/3.0
                    val v=((q ushr 16 and 255)+(q ushr 8 and 255)+(q and 255))/3.0
                    sa+=a;sb+=v;saa+=a*a;sbb+=v*v;sab+=a*v
                }
                val denom=kotlin.math.sqrt(maxOf(0.0,count*saa-sa*sa)*maxOf(0.0,count*sbb-sb*sb))
                val correlation=if(denom>1e-8) (count*sab-sa*sb)/denom else 0.0
                if(count>30 && (!silhouette || correlation>=.25)) best=minOf(best,error/count)
            }
            ref.id to best
        }.sortedBy { it.second }
        val best=scores.firstOrNull() ?: return null
        val margin=(scores.getOrNull(1)?.second ?: 255.0)-best.second
        return SkillIconMatch(best.first,best.second,margin).takeIf { it.error<=50.0 && it.margin>=4.0 }
    }
}

