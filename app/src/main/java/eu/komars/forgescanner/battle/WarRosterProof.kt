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
  }?.takeIf {key(it).length>=2}
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
