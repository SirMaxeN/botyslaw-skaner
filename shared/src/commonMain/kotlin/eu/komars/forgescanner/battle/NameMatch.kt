package eu.komars.forgescanner.battle

/**
 * Nickname agreement between the roster card and the opened profile. The avatar was already matched
 * visually before the tap, so OCR-only differences are tolerated: spaces, 0/o, 1/l/i, 5/s and at most
 * one other edit for names of five or more characters. Short names must match exactly.
 */
object NameMatch {
 private fun canonical(value:String)=BattleText.normalize(value).replace(Regex("""\s+"""),"")
  .map {when(it){'0'->'o';'1','i','|'->'l';'5'->'s';else->it}}.joinToString("")
 private val flipped=mapOf('a' to 'e','e' to 'a','d' to 'p','p' to 'd','b' to 'q','q' to 'b','n' to 'u','u' to 'n','m' to 'w','w' to 'm','h' to 'y','y' to 'h')
 /** Reads a nickname written upside down (rotated 180°) the way OCR sees it. */
 fun rotated(value:String)=canonical(value).reversed().map {flipped[it]?:it}.joinToString("")
 fun same(ocr:String,expected:String):Boolean {
  val a=canonical(ocr);val b=canonical(expected)
  if(a.isEmpty()||b.isEmpty())return false
  if(a==b||(a.length>=4&&a==rotated(expected)))return true
  return minOf(a.length,b.length)>=5&&(distance(a,b)<=1||distance(a,rotated(expected))<=1)
 }
 private fun distance(a:String,b:String):Int {
  if(kotlin.math.abs(a.length-b.length)>1)return 2
  var previous=IntArray(b.length+1){it}
  for(i in 1..a.length) {
   val current=IntArray(b.length+1);current[0]=i
   for(j in 1..b.length)current[j]=minOf(previous[j]+1,current[j-1]+1,previous[j-1]+if(a[i-1]==b[j-1])0 else 1)
   previous=current
  }
  return previous[b.length]
 }
}
