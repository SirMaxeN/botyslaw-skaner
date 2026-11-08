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
            .apply {icon(frame,r)?.let {put("icon",it)}}
    }
    /** 40×40 colour icon cut tightly around the round artwork (top 75%, above the "Poz. N" label). */
    private fun icon(frame:Bitmap,r:Rect):String? = runCatching {
        val slot=Rect(r);if(!slot.intersect(0,0,frame.width,frame.height)||slot.width()<6||slot.height()<6)return null
        val pixels=IntArray(slot.width()*slot.height());frame.getPixels(pixels,0,slot.width(),slot.left,slot.top,slot.width(),slot.height())
        val background=frame.getPixel(maxOf(0,slot.left-6),slot.centerY())
        val t=IconThumbnails.tightBox(slot.width(),slot.height(),pixels,background)?:intArrayOf(0,0,slot.width(),slot.height())
        val crop=Bitmap.createBitmap(frame,slot.left+t[0],slot.top+t[1],t[2]-t[0],maxOf(1,((t[3]-t[1])*.75).toInt()))
        val scaled=Bitmap.createScaledBitmap(crop,IconThumbnails.SIZE,IconThumbnails.SIZE,true)
        val hex=StringBuilder(IconThumbnails.SIZE*IconThumbnails.SIZE*6)
        for(y in 0 until IconThumbnails.SIZE)for(x in 0 until IconThumbnails.SIZE)hex.append(String.format(java.util.Locale.ROOT,"%06x",scaled.getPixel(x,y) and 0xffffff))
        if(scaled!==crop)scaled.recycle();crop.recycle()
        hex.toString()
    }.getOrNull()
    /** Locally learned icons (type, hash, 40×40 icon) for the shared base on the bot server. */
    fun exportIcons():List<Triple<String,String,String>> = prefs.all.values.mapNotNull { value -> runCatching { val o=JSONObject(value as String);val v=o.getJSONObject("visual")
        if(v.optBoolean("shared"))null else Triple(o.getString("type"),v.getString("hash"),v.getString("icon")) }.getOrNull() }
    /** Every stored icon (local and shared): type, hash, 40×40 icon. */
    fun allIcons():List<Triple<String,String,String>> = prefs.all.values.mapNotNull { value -> runCatching { val o=JSONObject(value as String);val v=o.getJSONObject("visual");Triple(o.getString("type"),v.getString("hash"),v.getString("icon")) }.getOrNull() }
    fun isShared(type:String,hash:String)=runCatching {JSONObject(prefs.getString(type+"_"+hash,null)!!).getJSONObject("visual").optBoolean("shared")}.getOrDefault(false)
    /** Icons learned by other phones; stored like local ones but marked shared (never re-uploaded). */
    fun importShared(type:String,hash:String,icon:String) {
        val skill=runCatching {BattleSkill.valueOf(type)}.getOrNull()?:return
        if(icon.length!=IconThumbnails.SIZE*IconThumbnails.SIZE*6||prefs.contains(skill.name+"_"+hash))return
        // At most 12 per skill: drop the oldest shared one first (four artworks × a few samples).
        val sharedKeys=prefs.all.keys.filter {it.startsWith(skill.name+"_")&&isShared(skill.name,it.removePrefix(skill.name+"_"))}
        prefs.edit().apply {
            if(prefs.all.keys.count {it.startsWith(skill.name+"_")}>=12)sharedKeys.firstOrNull()?.let {remove(it)}
            putString(skill.name+"_"+hash,JSONObject().put("type",skill.name).put("visual",JSONObject().put("hash",hash).put("icon",icon).put("box",3).put("shared",true)).toString())
        }.apply()
    }
    private fun learnedIcons():List<Pair<BattleSkill,String>> = prefs.all.values.mapNotNull { value -> runCatching { val o=JSONObject(value as String);BattleSkill.valueOf(o.getString("type")) to o.getJSONObject("visual").getString("icon") }.getOrNull() }
    private fun score(a:JSONObject,b:JSONObject):Int {
        val bits=java.lang.Long.bitCount(a.getString("hash").toLong() xor b.getString("hash").toLong())
        val color=listOf("r","g","b").sumOf { kotlin.math.abs(a.getInt(it)-b.getInt(it)) }
        return bits+color/8
    }
    fun recognize(signature:JSONObject):BattleSkill? {
        // Tight, shift-tolerant icon first; the legacy thumbnail/hash only for entries without one.
        signature.optString("icon").takeIf {it.isNotEmpty()}?.let {icon ->
            val learned=learnedIcons()
            if(learned.isNotEmpty())return IconThumbnails.recognizeIcon(icon,learned)
        }
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
        val icon=visual?.optString("icon").orEmpty()
        if(visual!=null&&icon.isNotEmpty()) {
            // An icon that looks like another skill's (e.g. Morale vs Higher Morale) is simply not stored;
            // learned entries are never erased by a single doubtful read.
            if((IconThumbnails.closestOther(icon,type,learnedIcons())?:99.0)<20.0) return
            val same=learnedThumbs().filter { it.second==type }
            prefs.edit().apply {
                if(same.size>=12) remove(same.first().first)
                putString(type.name+"_"+visual.optString("hash"),JSONObject().put("type",type.name).put("visual",visual).toString())
            }.apply()
        } else if(visual!=null&&thumb.isNotEmpty()) {
            val conflicts=learnedThumbs().filter { (_,other,t) -> other!=type && (IconThumbnails.distance(thumb,t)?:99.0)<12.0 }
            if(conflicts.isNotEmpty()) return
            val same=learnedThumbs().filter { it.second==type }
            prefs.edit().apply {
                if(same.size>=12) remove(same.first().first)
                putString(type.name+"_"+visual.optString("hash"),JSONObject().put("type",type.name).put("visual",visual).toString())
            }.apply()
        } else if(visual!=null) prefs.edit().putString(type.name+"_"+visual.optString("hash"),JSONObject().put("type",type.name).put("visual",visual).toString()).apply()
        val title=title(raw)
        if(title.length>=4 && !title.contains("nie otwarto")) prefs.edit().putString("name_"+title,type.name).apply()
    }
    fun byName(raw:String):BattleSkill?=runCatching { BattleSkill.valueOf(prefs.getString("name_"+title(raw),null) ?: return null) }.getOrNull()
}

