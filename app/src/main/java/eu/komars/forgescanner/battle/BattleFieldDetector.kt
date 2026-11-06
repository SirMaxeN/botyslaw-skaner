package eu.komars.forgescanner.battle

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognizer
import eu.komars.forgescanner.vision.CloseButtonDetector
import kotlin.math.roundToInt

/** Same contract as resource setup: suggest, show, confirm; never save or navigate here. */
class BattleFieldDetector(private val recognizer:TextRecognizer) {
    data class Proposal(val region:Rect,val reason:String)
    private fun layout(bitmap:Bitmap):BattleSetupGeometry.Layout {
        val scale=minOf(1.0,480.0/bitmap.width,1200.0/bitmap.height)
        val w=(bitmap.width*scale).roundToInt();val h=(bitmap.height*scale).roundToInt()
        val reduced=Bitmap.createScaledBitmap(bitmap,w,h,true)
        return try {
            val pixels=IntArray(w*h);reduced.getPixels(pixels,0,w,0,0,w,h)
            val found=BattleSetupGeometry(w,h,pixels).detect()
            BattleSetupGeometry.Layout(found.regions.mapValues { (_,q) ->
                eu.komars.forgescanner.model.PixelRegion((q.left/scale).roundToInt(),(q.top/scale).roundToInt(),(q.right/scale).roundToInt(),(q.bottom/scale).roundToInt())
            },found.profile,found.panel?.let { q -> eu.komars.forgescanner.model.PixelRegion((q.left/scale).roundToInt(),(q.top/scale).roundToInt(),(q.right/scale).roundToInt(),(q.bottom/scale).roundToInt()) })
        } finally { if(reduced!==bitmap) reduced.recycle() }
    }
    fun observe(bitmap:Bitmap)=layout(bitmap)
    fun detect(bitmap:Bitmap,key:String,callback:(Proposal?)->Unit) {
        val found=layout(bitmap)
        fun bounds(key:String)=found.regions[key]?.let { Rect(it.left,it.top,it.right,it.bottom) }
        val geometric=bounds(key)
        if(key=="close") {
            callback(CloseButtonDetector().detect(bitmap)?.let { Proposal(it,"Rozpoznano czerwone zamknięcie z białym X.") });return
        }
        if(key=="back") {
            val target=found.panel?.takeUnless { found.profile }?.let { WeaponDetailLayout.outsideArea(it,bitmap.width,bitmap.height) }
            callback(target?.let { Proposal(Rect(it.left,it.top,it.right,it.bottom),"Punkt poza dymkiem broni.") });return
        }
        if(key in listOf("open","weapon","header","skill1","skill2","skill3","fairy")) {
            callback(geometric?.let { Proposal(it,if(key=="open") "Rozpoznano małe i pod ekwipunkiem." else if(key=="header") "Cały prawy górny blok: kuźnia, łączne obrażenia i zdrowie." else if(key=="fairy") "Wróżka po prawej stronie rzędu — ikona i poziom pod nią." else if(key.startsWith("skill")) "Pole umiejętności — trzy lewe ikony, przed zwierzakami." else "Rozpoznano pole broni w drugim rzędzie ekwipunku.") });return
        }
        val ocr=bitmap.copy(Bitmap.Config.ARGB_8888,false)
        recognizer.process(InputImage.fromBitmap(ocr,0)).addOnSuccessListener { result ->
            if(bitmap.isRecycled) { callback(null); return@addOnSuccessListener }
            val lines=result.textBlocks.flatMap { it.lines }
            val rect=when(key) {
                "damage","health" -> {
                    val areaD=bounds("damage"); val areaH=bounds("health")
                    if(areaD==null || areaH==null) null else {
                        val tokens=lines.flatMap { it.elements }.filter { element ->
                            val q=element.boundingBox
                            q!=null && q.centerX()>areaD.left && q.centerY() in (areaD.top-bitmap.width*.04).toInt()..(areaH.bottom+bitmap.width*.025).toInt() &&
                                Regex("^\\d+(?:[.,]\\d+)?[kmbt]$",RegexOption.IGNORE_CASE).matches(element.text.replace(" ",""))
                        }.sortedBy { it.boundingBox!!.top }
                        if(tokens.size==2) padNumber(tokens[if(key=="damage") 0 else 1].boundingBox!!,bitmap) else null
                    }
                }
                "name" -> geometric?.let { area ->
                    lines.filter { line -> line.boundingBox?.let { Rect.intersects(it,area) }==true && line.text.any(Char::isLetter) }
                        .minByOrNull { it.boundingBox!!.top }?.boundingBox?.let { trimName(pad(it,bitmap),bitmap) }
                }
                "bonuses" -> geometric?.let { area ->
                    val first=lines.filter { line -> line.boundingBox?.let { q -> q.bottom>area.top && q.top<area.bottom && q.centerX() in area.left..area.right }==true &&
                        (line.text.contains('%') || Bonus.entries.any { b -> b.aliases.any { BattleText.normalize(line.text).contains(it) } }) }
                        .minByOrNull { it.boundingBox!!.top }
                    if(first==null) area else Rect(area.left,minOf(area.top,first.boundingBox!!.top-bitmap.width/150).coerceAtLeast(0),area.right,area.bottom)
                }
                "title","back" -> {
                    val panel=found.panel?.takeUnless { found.profile }
                    if(panel==null) null else {
                        val region=if(key=="back") WeaponDetailLayout.outsideArea(panel,bitmap.width,bitmap.height)
                        else {
                            eu.komars.forgescanner.model.PixelRegion(panel.left+3,panel.top+3,panel.right-3,panel.bottom-3)
                        }
                        region?.let { Rect(it.left,it.top,it.right,it.bottom) }
                    }
                }
                else -> null
            }
            val fallback=geometric
            callback((rect ?: fallback)?.let { Proposal(it,if(key=="back") "Proponuję punkt poza oknem broni. Sprawdź, czy ramka nie nachodzi na broń." else if(key=="title") "Ramka obejmuje okno broni i wiersz obrażeń z typem." else if(rect!=null) "Rozpoznano pole na podstawie tekstu i układu okna." else "Rozpoznano układ okna. Sprawdź i popraw ramkę.") })
        }.addOnFailureListener { callback(geometric?.let { Proposal(it,"Rozpoznano układ okna. Sprawdź ramkę.") }) }.addOnCompleteListener { ocr.recycle() }
    }
    private fun trimName(q:Rect,bitmap:Bitmap):Rect {
        val pixels=IntArray(q.width()*q.height())
        bitmap.getPixels(pixels,0,q.width(),q.left,q.top,q.width(),q.height())
        return Rect(q.left,q.top,q.left+PlayerNameRegion.rightEdge(q.width(),q.height(),pixels),q.bottom)
    }
    private fun padNumber(q:Rect,bitmap:Bitmap):Rect {
        val x=maxOf(8,bitmap.width/40);val y=maxOf(3,bitmap.width/180)
        return Rect((q.left-x).coerceAtLeast(0),(q.top-y).coerceAtLeast(0),(q.right+x).coerceAtMost(bitmap.width),(q.bottom+y).coerceAtMost(bitmap.height))
    }
    private fun pad(q:Rect,bitmap:Bitmap):Rect {
        val p=maxOf(3,bitmap.width/150)
        return Rect((q.left-p).coerceAtLeast(0),(q.top-p).coerceAtLeast(0),(q.right+p).coerceAtMost(bitmap.width),(q.bottom+p).coerceAtMost(bitmap.height))
    }
}
