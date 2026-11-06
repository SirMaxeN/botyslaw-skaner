package eu.komars.forgescanner.battle
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import org.json.JSONObject

/** Only confirmed numeric glyph masks are stored; no avatars, screenshots or player identifiers. */
internal class FairyLevelMemory(private val context:Context,namespace:String="fairy_level_glyphs_v1") {
 private val prefs=context.getSharedPreferences(namespace,0)
 fun capture(image:Bitmap,r:Rect):FairyLevelGlyph.Signature? {
  if(r.isEmpty||!Rect(0,0,image.width,image.height).contains(r))return null
  val p=IntArray(r.width()*r.height());image.getPixels(p,0,r.width(),r.left,r.top,r.width(),r.height())
  return FairyLevelGlyph.extract(r.width(),r.height(),p)
 }
 private fun examples()=(prefs.all.values.toList()+runCatching {val a=org.json.JSONArray(context.assets.open("fairy_level_glyphs_v1.json").bufferedReader().use {it.readText()});(0 until a.length()).map {a.getJSONObject(it).toString()}}.getOrDefault(emptyList())).mapNotNull {value->runCatching {val o=JSONObject(value.toString());o.getInt("level") to FairyLevelGlyph.Signature(o.getInt("digits"),o.getDouble("aspect"),o.getString("bits"))}.getOrNull()}
 fun recognize(s:FairyLevelGlyph.Signature?)=FairyLevelGlyph.match(s,examples())
 fun remember(level:Int,s:FairyLevelGlyph.Signature?) {
  if(level !in setOf(3,5,8)||s==null||s.digits!=1)return
  val key="$level-"+FullWarStore.hash(s.bits+"|"+s.aspect)
  val edit=prefs.edit();val own=prefs.all.keys.filter {it.startsWith("$level-")};if(own.size>=12&&!prefs.contains(key))own.firstOrNull()?.let {edit.remove(it)}
  edit.putString(key,JSONObject().put("level",level).put("digits",s.digits).put("aspect",s.aspect).put("bits",s.bits).toString()).apply()
 }
 fun preview(s:FairyLevelGlyph.Signature?):Bitmap?=s?.takeIf {it.bits.length==560}?.let {glyph->Bitmap.createBitmap(IntArray(560){if(glyph.bits[it]=='1')Color.BLACK else Color.WHITE},20,28,Bitmap.Config.ARGB_8888)}
}
