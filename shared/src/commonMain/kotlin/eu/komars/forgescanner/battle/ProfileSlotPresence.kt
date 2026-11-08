package eu.komars.forgescanner.battle
/** Three states: a failed classification must never become an empty slot. */
object ProfileSlotPresence {
 fun occupied(w:Int,h:Int,p:IntArray,bg:Int):Boolean? {
  if(w<8||h<8||p.size!=w*h)return null
  var same=0;var ink=0;var light=0;var dark=255
  for(y in 0 until 16)for(x in 0 until 16) {
   val c=p[((y+.5)*h/16).toInt()*w+((x+.5)*w/16).toInt()]
   val d=(0..2).maxOf {k->kotlin.math.abs((c ushr (8*k) and 255)-(bg ushr (8*k) and 255))}
   if(d<=10)same++;if(d>35)ink++;val grey=((c ushr 16 and 255)+(c ushr 8 and 255)+(c and 255))/3;light=maxOf(light,grey);dark=minOf(dark,grey)
  }
  return when {same>=254->false;ink>=64&&light-dark>=35->true;else->null}
 }
}
