package eu.komars.forgescanner.battle

import kotlin.math.floor
internal data class RosterBox(val left:Int,val top:Int,val right:Int,val bottom:Int) {val width get()=right-left;val height get()=bottom-top}
internal data class RosterCard(val name:RosterBox,val power:RosterBox,val visual:RosterVisual,val avatar:RosterBox)
internal object RosterCardLayout {
 fun fullCards(w:Int,h:Int,p:IntArray):List<RosterCard> =
  cards(w,h,p).filter { it.avatar.top>1 && it.avatar.bottom<h-1 }

 fun cards(w:Int,h:Int,p:IntArray):List<RosterCard> = RosterAvatarGeometry.find(w,h,p).mapNotNull {located ->
  val avatar=located.avatar;val row=located.row
  val size=avatar.width.toDouble()
  val left=(avatar.right+size*.07).toInt();val top=(avatar.top-size*.04).toInt().coerceAtLeast(0)
  val bottom=(avatar.top+size*.37).toInt();val limit=(row.right-row.height*.12).toInt().coerceAtMost(w)
  val bgColor=p[(avatar.top+size*.5).toInt()*w+maxOf(0,(avatar.left-size*.10).toInt())]
  val br=bgColor ushr 16 and 255;val bg=bgColor ushr 8 and 255;val bb=bgColor and 255
  fun badge(r:Int,g:Int,b:Int):Boolean {
   if(maxOf(br,bg,bb)-minOf(br,bg,bb)<20)return true
   val ur=(br-bb).toDouble();val ug=(bg-bb).toDouble();val vr=(r-b).toDouble();val vg=(g-b).toDouble()
   val ratio=(vr*ur+vg*ug)/(ur*ur+ug*ug)
   // Antialiased white/black letters on a blue card are blends of that background, not badges.
   return maxOf(kotlin.math.abs(vr-ratio*ur),kotlin.math.abs(vg-ratio*ug))>20
  }
  var nameEnd=limit
  // Scan only the nickname line: the gold power line must not be mistaken for a coloured badge.
  for(xx in left until limit) {
   var colored=0
   for(yy in top until top+((bottom-top)*.7).toInt()) {val c=p[yy*w+xx];val r=c ushr 16 and 255;val g=c ushr 8 and 255;val b=c and 255
    if(maxOf(r,g,b)-minOf(r,g,b)>65&&badge(r,g,b))colored++
   }
   if(colored>=maxOf(2,(size*.025).toInt())){nameEnd=(xx-size*.025).toInt();break}
  }
  val lastWhite=(left until nameEnd).lastOrNull {xx ->(top until bottom).any {yy ->val c=p[yy*w+xx];val r=c ushr 16 and 255;val g=c ushr 8 and 255;val b=c and 255;minOf(r,g,b)>242&&maxOf(r,g,b)-minOf(r,g,b)<20}}
  if(lastWhite!=null)nameEnd=minOf(nameEnd,(lastWhite+1+size*.04).toInt())
  if(nameEnd-left<maxOf(4,(size*.07).toInt()))return@mapNotNull null
  val name=RosterBox(left,top,nameEnd,bottom)
  val power=RosterBox((avatar.right+size*.5).toInt(),(avatar.top+size*.36).toInt(),minOf(row.right-(size*.15).toInt(),(avatar.right+size*3.4).toInt()),(avatar.top+size*.72).toInt())
  if(power.width<8||power.height<6)return@mapNotNull null
  fun channel(c:Int,shift:Int)=c ushr shift and 255
  fun sample(xx:Double,yy:Double):Int {
   val fx=xx.coerceIn(0.0,w-1.001);val fy=yy.coerceIn(0.0,h-1.001);val x0=floor(fx).toInt();val y0=floor(fy).toInt();val dx=fx-x0;val dy=fy-y0
   var out=0xff000000.toInt()
   for(shift in intArrayOf(0,8,16)) {val a=channel(p[y0*w+x0],shift)*(1-dx)+channel(p[y0*w+x0+1],shift)*dx;val b=channel(p[(y0+1)*w+x0],shift)*(1-dx)+channel(p[(y0+1)*w+x0+1],shift)*dx;out=out or (((a*(1-dy)+b*dy).toInt()) shl shift)}
   return out
  }
  val background=(br+bg+bb)/3
  val portrait=IntArray(32*32) {i ->sample(avatar.left+size*(.08+(i%32+.5)*.84/32),avatar.top+size*(.08+(i/32+.5)*.84/32))}
  val raw=IntArray(128*32) {i ->sample(left+(i%128+.5)*name.width/128,top+(i/128+.5)*name.height/32).let {((it ushr 16 and 255)*299+(it ushr 8 and 255)*587+(it and 255)*114)/1000}}
  // Small smoothing suppresses subpixel scroll/antialiasing differences, not the nickname's shape.
  val smooth=IntArray(raw.size) {i ->val x=i%128;val y=i/128;var sum=0;var count=0
   for(yy in maxOf(0,y-1)..minOf(31,y+1))for(xx in maxOf(0,x-1)..minOf(127,x+1)){sum+=raw[yy*128+xx];count++};sum/count
  }
  RosterCard(name,power,RosterVisual(portrait,smooth,background,name.width/size),avatar)
 }
}
internal object RosterCardText {
 fun name(text:String):String? = text.lines().firstOrNull {it.isNotBlank()}?.trim()?.takeIf {
  it.any(Char::isLetterOrDigit) && BattleText.normalize(it) !in listOf("r4","lider","dowodca","kapitan","online")
 }
 fun power(text:String):Double? = Regex("^([0-9]+(?:[.,][0-9]+)?\\s*[kmbt])$",RegexOption.IGNORE_CASE).matchEntire(text.trim())?.groupValues?.get(1)?.let(BattleText::number)?.takeIf {it>0}
}
