package eu.komars.forgescanner.battle

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import org.json.JSONObject

/** Learns user-confirmed artwork. Only hashes/colors are retained, never screenshots. */
class SkillVisualMemory(private val context:Context) {
    private val prefs=context.getSharedPreferences("skill_visual_memory_v1",0).also { p ->
        // One-time cleanup: artwork learned before aligned icon boxes (no "box" marker) is unreliable.
        val stale=p.all.filter { (key,value) -> !key.startsWith("name_") && runCatching { !JSONObject(value as String).getJSONObject("visual").has("box") }.getOrDefault(true) }.keys
        if(stale.isNotEmpty()) p.edit().apply { stale.forEach { remove(it) } }.apply()
    }
    private val wikiTemplates by lazy {
        BattleSkill.entries.mapNotNull { skill ->
            val file=when(skill) { BattleSkill.SHURIKENS -> "Shuriken";BattleSkill.ARROW_RAIN -> "RainOfArrows";else -> skill.name.lowercase().split('_').joinToString("") { it.replaceFirstChar(Char::uppercaseChar) } }
            runCatching {
                val image=context.assets.open("battle_skills/$file.png").use { android.graphics.BitmapFactory.decodeStream(it) }
                var l=image.width;var t=image.height;var r=0;var b=0
                for(y in 0 until image.height) for(x in 0 until image.width) if(image.getPixel(x,y) ushr 24>220) { l=minOf(l,x);t=minOf(t,y);r=maxOf(r,x+1);b=maxOf(b,y+1) }
                val pixels=IntArray((r-l)*(b-t));image.getPixels(pixels,0,r-l,l,t,r-l,b-t);image.recycle()
                SkillIconTemplate(skill.name,r-l,b-t,pixels)
            }.getOrNull()
        }
    }
    fun fromWiki(frame:Bitmap,r:Rect):BattleSkill? {
        return wikiMatch(frame,r)?.let { BattleSkill.valueOf(it.id) }
    }
    fun wikiMatch(frame:Bitmap,r:Rect):SkillIconMatch? {
        // The matcher probes slightly beyond the icon to align the artwork.
        val window=Rect(r).apply {inset(-r.width()/2,-r.height()/2)}
        if(!window.intersect(0,0,frame.width,frame.height))return null
        val pixels=IntArray(window.width()*window.height())
        frame.getPixels(pixels,0,window.width(),window.left,window.top,window.width(),window.height())
        return SkillIconMatcher.match(window.width(),window.height(),pixels,r.left-window.left,r.top-window.top,r.right-window.left,r.bottom-window.top,wikiTemplates)
    }
    fun fingerprint(frame:Bitmap,r:Rect):JSONObject {
        val crop=Bitmap.createBitmap(frame,r.left,r.top,r.width(),maxOf(1,(r.height()*.75).toInt()))
        val small=Bitmap.createScaledBitmap(crop,9,8,true)
        var bits=0L;var red=0L;var green=0L;var blue=0L
        fun gray(c:Int)=((c ushr 16 and 255)*3+(c ushr 8 and 255)*6+(c and 255))
        for(y in 0..7) for(x in 0..7) {
            val c=small.getPixel(x,y);bits=(bits shl 1) or if(gray(c)>gray(small.getPixel(x+1,y))) 1L else 0L
            red+=c ushr 16 and 255;green+=c ushr 8 and 255;blue+=c and 255
        }
        if(small!==crop) small.recycle()
        // 16×16 colour thumbnail of the in-game icon: compared with icons learned from confirmed reads.
        val thumb=Bitmap.createScaledBitmap(crop,16,16,true);val hex=StringBuilder(16*16*6)
        for(y in 0 until 16)for(x in 0 until 16)hex.append(String.format(java.util.Locale.ROOT,"%06x",thumb.getPixel(x,y) and 0xffffff))
        if(thumb!==crop)thumb.recycle();crop.recycle()
        return JSONObject().put("hash",bits.toString()).put("r",red/64).put("g",green/64).put("b",blue/64).put("thumb",hex.toString()).put("box",2)
    }
    private fun score(a:JSONObject,b:JSONObject):Int {
        val bits=java.lang.Long.bitCount(a.getString("hash").toLong() xor b.getString("hash").toLong())
        val color=listOf("r","g","b").sumOf { kotlin.math.abs(a.getInt(it)-b.getInt(it)) }
        return bits+color/8
    }
    fun recognize(signature:JSONObject):BattleSkill? {
        IconThumbnails.recognize(signature.optString("thumb"),prefs.all.values.mapNotNull { value -> runCatching { val o=JSONObject(value as String);BattleSkill.valueOf(o.getString("type")) to o.getJSONObject("visual").optString("thumb") }.getOrNull() })?.let { return it }
        val scored=prefs.all.values.mapNotNull { value -> runCatching { val o=JSONObject(value as String);BattleSkill.valueOf(o.getString("type")) to score(signature,o.getJSONObject("visual")) }.getOrNull() }
            .groupBy { it.first }.map { (type,values) -> type to values.minOf { it.second } }.sortedBy { it.second }
        val best=scored.firstOrNull() ?: return null
        return best.first.takeIf { best.second<=10 && (scored.size==1 || scored[1].second-best.second>=4) }
    }
    private fun title(raw:String):String {
        val first=BattleText.normalize(raw.lineSequence().firstOrNull().orEmpty())
        val clean=first.substringAfter(']',first).trim()
        return clean.takeUnless { it.contains('%') || Regex("^(poz|poziom|level|lv)[., :0-9]*$").matches(it) }.orEmpty()
    }
    private fun learnedThumbs():List<Triple<String,BattleSkill,String>> = prefs.all.mapNotNull { (key,value) -> runCatching { val o=JSONObject(value as String);Triple(key,BattleSkill.valueOf(o.getString("type")),o.getJSONObject("visual").optString("thumb")) }.getOrNull() }
    /** Diagnostics: the closest learned icons, e.g. "LIGHTNING 7.2 / WORM 61.0". */
    fun nearest(signature:JSONObject):String {
        val thumb=signature.optString("thumb")
        return learnedThumbs().mapNotNull { (_,type,t) -> IconThumbnails.distance(thumb,t)?.let { type to it } }
            .groupBy { it.first }.map { (type,list) -> type to list.minOf { it.second } }.sortedBy { it.second }.take(2)
            .joinToString(" / ") { "${it.first} ${String.format(java.util.Locale.ROOT,"%.1f",it.second)}" }
    }
    fun remember(type:BattleSkill,raw:String,visual:JSONObject?) {
        val thumb=visual?.optString("thumb").orEmpty()
        if(visual!=null&&thumb.isNotEmpty()) {
            // An icon that looks like another skill's learned icon is a mislabel (e.g. Morale vs Higher Morale):
            // store neither, and drop the conflicting entries so they cannot decide later reads.
            val conflicts=learnedThumbs().filter { (_,other,t) -> other!=type && (IconThumbnails.distance(thumb,t)?:99.0)<12.0 }
            if(conflicts.isNotEmpty()) { prefs.edit().apply { conflicts.forEach { remove(it.first) } }.apply(); return }
            val same=learnedThumbs().filter { it.second==type }
            prefs.edit().apply {
                if(same.size>=8) remove(same.first().first)
                putString(type.name+"_"+visual.optString("hash"),JSONObject().put("type",type.name).put("visual",visual).toString())
            }.apply()
        } else if(visual!=null) prefs.edit().putString(type.name+"_"+visual.optString("hash"),JSONObject().put("type",type.name).put("visual",visual).toString()).apply()
        val title=title(raw)
        if(title.length>=4 && !title.contains("nie otwarto")) prefs.edit().putString("name_"+title,type.name).apply()
    }
    fun byName(raw:String):BattleSkill?=runCatching { BattleSkill.valueOf(prefs.getString("name_"+title(raw),null) ?: return null) }.getOrNull()
}

