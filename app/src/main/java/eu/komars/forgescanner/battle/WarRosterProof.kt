package eu.komars.forgescanner.battle

/** Clan identity comes from the bounded modal header, never from a flag colour or a player name. */
internal object WarRosterProof {
 fun key(value:String)=BattleText.normalize(value).filter(Char::isLetterOrDigit)
 fun clan(text:String):String? {
  val lines=text.lines().map {it.trim()}.filter(String::isNotEmpty)
  val metadata=Regex("^(poziom|level|laczna|total|technolog|czlonkowie|members)\\b")
  val end=lines.indexOfFirst {metadata.containsMatchIn(BattleText.normalize(it))}
  if(end<=0)return null
  return lines.take(end).lastOrNull {line ->
   val n=BattleText.normalize(line)
   line.any(Char::isLetter) && !Regex("^(dzien|day|postep|war progress|mvp)\\b").containsMatchIn(n)
  }?.takeIf {key(it).length>=2}?.let(::displayName)
 }
 /**
  * OCR reads the clan tag's square brackets as "(" and "J"/")" and keeps the decorative underscores:
  * "PolskaMoc(_PL_J" and "PolskaMOC[_PL_]" both mean "PolskaMoc[PL]". Only a trailing tag is rewritten.
  */
 fun displayName(raw:String):String {
  val match=Regex("""^(.*?)\s*[\[(]\s*_*\s*([A-Za-z0-9]{1,6})\s*_*\s*[\])Jj|]?\s*$""").find(raw.trim())
   ?:Regex("""^(.*?[A-Za-z0-9])_+([A-Z0-9]{1,6})_+[\])Jj|]\s*$""").find(raw.trim())?:return raw.trim()
  val name=match.groupValues[1].trim();val tag=match.groupValues[2].uppercase()
  return if(name.isEmpty())raw.trim() else "$name[$tag]"
 }
 /** A strict cached-image check also catches the same header when OCR spells its clan tag differently. */
 fun headerSnapshot(w:Int,h:Int,p:IntArray):IntArray {
  require(w>0&&h>0&&p.size==w*h)
  return IntArray(128*64) {i ->
   val x=((i%128+.5)*w/128).toInt().coerceIn(0,w-1)
   val y=((.30+(i/128+.5)*.70/64)*h).toInt().coerceIn(0,h-1)
   p[y*w+x]
  }
 }
 fun sameHeader(a:IntArray?,b:IntArray?):Boolean = a!=null&&b!=null&&a.size==128*64&&a.contentEquals(b)&&a.count {c->minOf(c ushr 16 and 255,c ushr 8 and 255,c and 255)>200}>16
 fun sameClan(ours:String?,enemy:String?)=ours!=null&&enemy!=null&&key(ours)==key(enemy)
 fun complete(ours:List<WarMember>,enemies:List<WarMember>,ourExpected:Int,enemyExpected:Int,ownClan:String?,enemyClan:String?):Boolean {
  if(ownClan.isNullOrBlank()||enemyClan.isNullOrBlank()||sameClan(ownClan,enemyClan))return false
  if(ourExpected !in 1..50||enemyExpected !in 1..50||ours.size!=ourExpected||enemies.size!=enemyExpected)return false
  fun signatures(list:List<WarMember>)=list.map {key(it.name) to it.power}.toSet()
  return signatures(ours)!=signatures(enemies)
 }
}
