package eu.komars.forgescanner.battle

import android.graphics.*
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognizer
import java.io.File

/** Owns a capture. Tiny row snapshots allow missing fields to be repaired without navigating again. */
internal class RosterFrameReader(private val ocr:TextRecognizer,private val diagnostic:File?=null,private val scaleFactor:Int=3,private val nameThreshold:Int=244,private val dilation:Int=1) {
 private data class Pass(val scale:Int,val threshold:Int,val radius:Int)
 private fun box(q:RosterBox,offset:Rect)=Rect(offset.left+q.left,offset.top+q.top,offset.left+q.right,offset.top+q.bottom)
 private fun snapshot(image:Bitmap,r:Rect,card:RosterCard):RosterSnapshot {
  val slot=maxOf(card.name.height,card.power.height)+24;val width=maxOf(card.name.width,card.power.width)+16
  val bitmap=Bitmap.createBitmap(width,slot*2,Bitmap.Config.ARGB_8888);val canvas=Canvas(bitmap);canvas.drawColor(Color.WHITE)
  listOf(card.name,card.power).forEachIndexed {i,q ->canvas.drawBitmap(image,box(q,r),Rect(8,i*slot+8,8+q.width,i*slot+8+q.height),null)}
  val pixels=IntArray(width*slot*2);bitmap.getPixels(pixels,0,width,0,0,width,slot*2);bitmap.recycle()
  return RosterSnapshot(width,slot,card.name.width,card.name.height,card.power.width,card.power.height,pixels)
 }
 fun read(image:Bitmap,r:Rect,valid:()->Boolean,reuse:((RosterVisual)->RosterReading?)?=null,done:(Result<List<RosterObservation>>)->Unit) {
  if(!Rect(0,0,image.width,image.height).contains(r)){image.recycle();done(Result.failure(IllegalArgumentException("Pole listy poza ekranem")));return}
  val pixels=IntArray(r.width()*r.height());image.getPixels(pixels,0,r.width(),r.left,r.top,r.width(),r.height())
  val cards=if(reuse==null)RosterCardLayout.cards(r.width(),r.height(),pixels)else RosterCardLayout.fullCards(r.width(),r.height(),pixels)
  if(cards.isEmpty()){image.recycle();done(Result.failure(IllegalStateException("Nie znaleziono pełnych pól graczy. Popraw bieżące pole.")));return}
  val snapshots=cards.map {snapshot(image,r,it)}
  diagnostic?.let {dir ->runCatching {File(dir,"roster-latest.png").outputStream().use {stream ->
   val crop=Bitmap.createBitmap(image,r.left,r.top,r.width(),r.height());crop.compress(Bitmap.CompressFormat.PNG,100,stream);if(crop!==image)crop.recycle()
  }}}
  image.recycle()
  // Filled letters/digits passed the real-image tests. The outline-only pass added cost and noise.
  val saved=cards.map {reuse?.invoke(it.visual)}
  val pending=cards.indices.filter {saved[it]==null}
  analyze(pending.map {snapshots[it]},listOf(Pass(scaleFactor,nameThreshold,0),Pass(scaleFactor,nameThreshold,dilation)),valid,"roster-latest") {result ->
   done(result.map {readings ->
    val fresh=pending.zip(readings).toMap()
    cards.mapIndexed {i,c ->
     val values=fresh[i]?:listOfNotNull(saved[i],saved[i])
     RosterObservation(c.visual,values,c.name.top,snapshots[i])
    }
   })
  }
 }
 fun retry(snapshots:List<RosterSnapshot>,attempt:Int=0,valid:()->Boolean,done:(Result<List<List<RosterReading>>>)->Unit) {
  val passes=if(attempt==0)listOf(Pass(2,244,0),Pass(4,244,1),Pass(3,240,2))else listOf(Pass(3,242,0),Pass(2,240,1),Pass(4,244,2))
  analyze(snapshots,passes,valid,"roster-repair-$attempt",done)
 }
 private fun analyze(snapshots:List<RosterSnapshot>,passes:List<Pass>,valid:()->Boolean,label:String,done:(Result<List<List<RosterReading>>>)->Unit) {
  if(snapshots.isEmpty()){done(Result.success(emptyList()));return}
  val slot=snapshots.maxOf {maxOf(it.nameHeight,it.powerHeight)}+24
  val width=snapshots.maxOf {maxOf(it.nameWidth,it.powerWidth)}+16
  val source=Bitmap.createBitmap(width,slot*snapshots.size*2,Bitmap.Config.ARGB_8888);val canvas=Canvas(source);canvas.drawColor(Color.WHITE)
  snapshots.forEachIndexed {i,s ->
   val bitmap=Bitmap.createBitmap(s.pixels,s.width,s.slot*2,Bitmap.Config.ARGB_8888)
   canvas.drawBitmap(bitmap,Rect(8,8,8+s.nameWidth,8+s.nameHeight),Rect(8,i*slot*2+8,8+s.nameWidth,i*slot*2+8+s.nameHeight),null)
   canvas.drawBitmap(bitmap,Rect(8,s.slot+8,8+s.powerWidth,s.slot+8+s.powerHeight),Rect(8,(i*2+1)*slot+8,8+s.powerWidth,(i*2+1)*slot+8+s.powerHeight),null)
   bitmap.recycle()
  }
  val readings=Array(snapshots.size){mutableListOf<RosterReading>()};val notes=StringBuilder()
  fun pass(at:Int) {
   if(!valid()){source.recycle();return}
   val config=passes[at];val scale=config.scale.coerceIn(2,5)
   val scaled=Bitmap.createScaledBitmap(source,source.width*scale,source.height*scale,true)
   val colors=IntArray(scaled.width*scaled.height);scaled.getPixels(colors,0,scaled.width,0,0,scaled.width,scaled.height)
   for(i in colors.indices) {
    val y=i/scaled.width;val x=i%scaled.width;val field=(y/scale)/slot;val snap=snapshots[field/2]
    val fieldWidth=if(field%2==0)snap.nameWidth else snap.powerWidth;val fieldHeight=if(field%2==0)snap.nameHeight else snap.powerHeight
    val localY=y/scale-field*slot;val localX=x/scale
    val inside=localX>=8&&localX<8+fieldWidth&&localY>=8&&localY<8+fieldHeight
    val c=colors[i];val r=c ushr 16 and 255;val g=c ushr 8 and 255;val b=c and 255
    val ink=inside&&if(field%2==0)RosterInkMask.name(r,g,b,config.threshold)else RosterInkMask.power(r,g,b)
    colors[i]=if(ink)Color.BLACK else Color.WHITE
   }
   if(config.radius>0) {
    val expanded=colors.copyOf();val radius=config.radius.coerceAtMost(2)
    for(i in colors.indices)if(colors[i]==Color.BLACK){val x=i%scaled.width;val y=i/scaled.width
     for(yy in maxOf(0,y-radius)..minOf(scaled.height-1,y+radius))for(xx in maxOf(0,x-radius)..minOf(scaled.width-1,x+radius))expanded[yy*scaled.width+xx]=Color.BLACK
    };expanded.copyInto(colors)
   }
   scaled.setPixels(colors,0,scaled.width,0,0,scaled.width,scaled.height)
   ocr.process(InputImage.fromBitmap(scaled,0)).addOnSuccessListener {result ->
    if(!valid()){source.recycle();return@addOnSuccessListener}
    val lines=result.textBlocks.flatMap {it.lines}.mapNotNull {line ->line.boundingBox?.let {BattleOcrLine(line.text,it.left,it.top,it.right,it.bottom)}}
    fun text(index:Int)=BattleOcrRows.text(lines.filter {((it.top+it.bottom)/2)/(slot*scale)==index})
    snapshots.indices.forEach {index ->val name=text(index*2);val power=text(index*2+1)
     readings[index]+=RosterReading(RosterCardText.name(name),RosterCardText.power(power));notes.append("pass=$at row=$index name=$name power=$power\n")
    }
    if(at+1<passes.size)pass(at+1)else {
     source.recycle();diagnostic?.let {dir ->runCatching {File(dir,"$label.txt").writeText("rows=${snapshots.size} passes=${passes.size}\n$notes")}}
     done(Result.success(readings.map {it.toList()}))
    }
   }.addOnFailureListener {source.recycle();if(valid())done(Result.failure(it))}.addOnCompleteListener {scaled.recycle()}
  }
  pass(0)
 }
}
