package eu.komars.forgescanner.battle
import org.junit.Assume.assumeTrue
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * Real-screenshot regression for the game's "Poz. N" font. Private fixtures live in ignored
 * .local/font-fixtures; the test is skipped when they are absent. Set FONT_WRITE_ASSET=1 to
 * regenerate app/src/main/assets/game_digits_v1.txt from all labelled crops.
 */
class GameDigitFontFixtureTest {
 private data class Label(val file:String,val l:Int,val t:Int,val r:Int,val b:Int,val value:Int)
 private val labels=listOf(
  Label("failure-test66-000534.argb",150,1083,292,1130,49),Label("failure-test66-000534.argb",312,1083,448,1130,93),
  Label("failure-test66-000534.argb",474,1083,608,1130,57),Label("failure-test66-000534.argb",620,1083,778,1130,104),
  Label("failure-test66-000534.argb",793,1083,928,1130,55),Label("failure-test66-000534.argb",143,1248,298,1294,106),
  Label("failure-test66-000534.argb",303,1248,460,1294,104),Label("failure-test66-000534.argb",464,1248,620,1294,108),
  Label("failure-test66-000534.argb",705,1240,855,1290,39),Label("failure-test66-000534.argb",158,1418,240,1460,1),
  Label("failure-test66-000534.argb",258,1418,340,1460,1),Label("failure-test66-000534.argb",358,1418,442,1460,1),
  Label("failure-test66-000534.argb",456,1418,542,1460,3),Label("failure-test66-000534.argb",548,1418,647,1460,49),
  Label("failure-test66-000534.argb",649,1418,750,1460,35),Label("failure-test66-000534.argb",843,1418,930,1460,7),
  Label("dzikiroman-170502.argb",786,1082,936,1130,113),Label("dzikiroman-170502.argb",836,1426,936,1464,10),
 )
 private val root=listOf(File("../.local/font-fixtures"),File(".local/font-fixtures")).firstOrNull {it.isDirectory}
 private val images=mutableMapOf<String,Triple<Int,Int,IntArray>>()
 private fun image(file:String)=images.getOrPut(file) {
  java.io.DataInputStream(java.io.BufferedInputStream(File(root,file).inputStream())).use {input ->
   val w=input.readInt();val h=input.readInt();Triple(w,h,IntArray(w*h){input.readInt()})
  }
 }
 private fun crop(label:Label):Triple<Int,Int,IntArray> {
  val (iw,_,pixels)=image(label.file);val w=label.r-label.l;val h=label.b-label.t
  return Triple(w,h,IntArray(w*h){i->pixels[(label.t+i/w)*iw+label.l+i%w]})
 }
 @Test fun realLabelsSegmentAndReadLeavingEachScreenshotOut() {
  assumeTrue(root!=null)
  val report=StringBuilder()
  val learned=labels.map {label ->val (w,h,p)=crop(label);label to GameDigitFont.learn(w,h,p,label.value)}
  for((label,samples) in learned)report.append("${label.file} ${label.value}: segmented=${samples.size}/${label.value.toString().length}\n")
  var correct=0;var wrong=0;var unknown=0
  for((label,_) in learned) {
   val training=learned.filter {it.first.file!=label.file||it.first!==label}.filter {it.first!==label}.flatMap {it.second}
   val (w,h,p)=crop(label);val read=GameDigitFont.read(w,h,p,training)
   when(read){label.value->correct++;null->unknown++;else->wrong++}
   report.append("read ${label.value} -> $read\n")
  }
  report.append("correct=$correct unknown=$unknown wrong=$wrong\n")
  File(root,"font-report.txt").writeText(report.toString())
  if(System.getenv("FONT_WRITE_ASSET")=="1")File("src/main/assets/game_digits_v1.txt").writeText(GameDigitFont.encode(learned.flatMap {it.second})+"\n")
  assertEquals("no wrong reads",0,wrong)
  assertTrue("segmentation works on most real labels",learned.count {it.second.isNotEmpty()}>=labels.size*3/4)
 }
}
