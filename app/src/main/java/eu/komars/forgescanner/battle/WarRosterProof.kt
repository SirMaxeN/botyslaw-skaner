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
  * A clan name in the game always ends with its tag in square brackets. OCR reads those brackets as "(", "J",
  * ")", "|", a space or nothing at all and keeps the decorative underscores: "PolskaMoc(_PL_J",
  * "PolskaMOC[_PL_]", "PolskaMoc PL J" and "PolskaMoc PL]" all mean "PolskaMoc[PL]". Only a trailing tag is
  * rewritten; without any bracket left, a final upper-case word after a space or "_" is taken as the tag.
  */
 fun displayName(raw:String):String {
  val text=raw.trim()
  val match=
   // Opening bracket kept; the closing one may be lost or read as "J" ("PolskaMoc(PLJ").
   Regex("""^(.*?)\s*[\[({]\s*_*\s*([A-Za-z0-9]{1,6}?)\s*_*\s*[\])}Jj|]?\s*$""").find(text)
   // Opening bracket lost: the tag stands apart (space, "_" or "|") and ends with a bracket or a separate "J".
   ?:Regex("""^(.*?[\p{L}\p{N}])(?:\s*\|\s*|[\s_]+)_*([A-Z0-9]{1,6})_*\s*(?:[\])}|]|(?<=[\s_])[Jj])\s*$""").find(text)
   // Opening bracket lost without a gap: "PolskaMocPL]" (a real closing bracket, the tag in capitals).
   ?:Regex("""^(.*?\p{Ll})([A-Z0-9]{1,6})_*\s*[\])}]\s*$""").find(text)
   // Both brackets lost: "PolskaMoc PL", "PolskaMOC_PL_", "PolskaMoc PLJ" (the closing bracket read as "J").
   ?:Regex("""^(.*?[\p{L}\p{N}])[\s_]+_*([A-Z0-9]{2,6}?)_*\s*[Jj]?$""").find(text)
   ?:return text
  val name=match.groupValues[1].trim().trimEnd('_').trim();val tag=match.groupValues[2].uppercase()
  return if(name.isEmpty())text else "$name[$tag]"
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
