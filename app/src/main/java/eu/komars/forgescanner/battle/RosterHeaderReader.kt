package eu.komars.forgescanner.battle

import android.graphics.Bitmap
import android.graphics.Color
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognizer
import java.io.File

/** Owns one bounded header crop; contrast recovery never navigates or reacquires the screen. */
internal class RosterHeaderReader(private val ocr:TextRecognizer,private val diagnostic:File?=null) {
 data class Reading(val text:String,val snapshot:IntArray,val attempts:Int)
 fun read(source:Bitmap,valid:()->Boolean,attempted:(Int,String)->Unit={_,_->},firstPass:Int=0,done:(Reading)->Unit) {
  require(firstPass in 0..2)
  val pixels=IntArray(source.width*source.height);source.getPixels(pixels,0,source.width,0,0,source.width,source.height)
  val snapshot=WarRosterProof.headerSnapshot(source.width,source.height,pixels)
  val notes=StringBuilder()
  diagnostic?.let {dir->runCatching {File(dir,"war-header-last.png").outputStream().use {source.compress(Bitmap.CompressFormat.PNG,100,it)}}}
  fun pass(at:Int) {
   if(!valid()){source.recycle();return}
   val scale=if(at==0)2 else 3
   val image=Bitmap.createScaledBitmap(source,source.width*scale,source.height*scale,true)
   if(at>0) {
    val colors=IntArray(image.width*image.height);image.getPixels(colors,0,image.width,0,0,image.width,image.height)
    val threshold=if(at==1)220 else 180
    for(i in colors.indices){val c=colors[i];val gray=((c ushr 16 and 255)*299+(c ushr 8 and 255)*587+(c and 255)*114)/1000;colors[i]=if(gray>=threshold)Color.BLACK else Color.WHITE}
    image.setPixels(colors,0,image.width,0,0,image.width,image.height)
   }
   fun accept(text:String) {
    if(!valid()){source.recycle();return}
    notes.append("pass=$at\n$text\n")
    diagnostic?.let {dir->runCatching {File(dir,"war-header-last.txt").writeText(notes.toString())}}
    attempted(at,text)
    if((WarPreparationModel.memberCount(text) in 1..50&&WarRosterProof.clan(text)!=null)||at==2) {source.recycle();done(Reading(text,snapshot,at-firstPass+1))}
    else pass(at+1)
   }
   ocr.process(InputImage.fromBitmap(image,0)).addOnSuccessListener {result->
    accept(BattleOcrRows.text(result.textBlocks.flatMap {it.lines}.mapNotNull {line->line.boundingBox?.let {BattleOcrLine(line.text,it.left,it.top,it.right,it.bottom)}}))
   }.addOnFailureListener {accept("")}.addOnCompleteListener {image.recycle()}
  }
  pass(firstPass)
 }
}
