package eu.komars.forgescanner.battle

import android.graphics.Bitmap
import android.graphics.Color
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognizer
import java.util.ArrayDeque

/** Takes ownership of small in-memory crops. Main-loop queue: gestures never wait for its contrast passes. */
internal class CapturedTextReader(private val recognizer:TextRecognizer) {
    private class Job(val image:Bitmap,val inspect:(String)->Boolean,val done:(Result<String>)->Unit) {var last=""}
    private val queue=ArrayDeque<Job>()
    private var current:Job?=null
    private var generation=0
    fun read(image:Bitmap,inspect:(String)->Boolean,done:(Result<String>)->Unit) {
        queue.addLast(Job(image,inspect,done));pump()
    }
    fun cancel() {
        generation++;current?.image?.let {if(!it.isRecycled)it.recycle()};current=null
        while(queue.isNotEmpty())queue.removeFirst().image.let {if(!it.isRecycled)it.recycle()}
    }
    private fun pump() {
        if(current!=null || queue.isEmpty())return
        val job=queue.removeFirst();current=job;pass(job,0,generation)
    }
    private fun pass(job:Job,variant:Int,token:Int) {
        if(token!=generation || current!==job)return
        val scale=if(variant==0)3 else 4
        val boundedScale=minOf(scale.toDouble(),kotlin.math.sqrt(4_000_000.0/(job.image.width.toDouble()*job.image.height)))
        val scaled=Bitmap.createScaledBitmap(job.image,maxOf(1,(job.image.width*boundedScale).toInt()),maxOf(1,(job.image.height*boundedScale).toInt()),true)
        val enlarged=if(scaled===job.image)scaled.copy(Bitmap.Config.ARGB_8888,false)else scaled
        val input=if(variant==0)enlarged else {
            val pixels=IntArray(enlarged.width*enlarged.height);enlarged.getPixels(pixels,0,enlarged.width,0,0,enlarged.width,enlarged.height)
            val threshold=when(variant){1->145;2->195;else->95}
            for(i in pixels.indices) {
                val c=pixels[i];val v=(Color.red(c)*30+Color.green(c)*59+Color.blue(c)*11)/100
                pixels[i]=if(v>=threshold)Color.BLACK else Color.WHITE
            }
            Bitmap.createBitmap(pixels,enlarged.width,enlarged.height,Bitmap.Config.ARGB_8888).also {enlarged.recycle()}
        }
        val task=try {recognizer.process(InputImage.fromBitmap(input,0))} catch(error:Exception) {
            input.recycle();finish(job,Result.failure(error));return
        }
        task.addOnSuccessListener {result ->
            if(token==generation && current===job) {
                val text=BattleOcrRows.text(result.textBlocks.flatMap {it.lines}.mapNotNull {line->line.boundingBox?.let {q->BattleOcrLine(line.text,q.left,q.top,q.right,q.bottom)}})
                job.last=text
                val accepted=runCatching {job.inspect(text)}
                if(accepted.isFailure)finish(job,Result.failure(accepted.exceptionOrNull()!!))
                else if(accepted.getOrDefault(false) || variant>=3)finish(job,Result.success(text))
                else pass(job,variant+1,token)
            }
        }.addOnFailureListener {error ->
            if(token==generation && current===job) {
                if(variant<3)pass(job,variant+1,token)else finish(job,Result.failure(error))
            }
        }.addOnCompleteListener {input.recycle()}
    }
    private fun finish(job:Job,result:Result<String>) {
        if(current!==job)return
        current=null;if(!job.image.isRecycled)job.image.recycle()
        job.done(result);pump()
    }
}
