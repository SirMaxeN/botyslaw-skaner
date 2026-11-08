package eu.komars.forgescanner.battle

import java.time.*
data class WarMember(val name:String,val power:Double)
object WarPreparationModel {
 fun day(now:Instant=Instant.now()):String? {
  val local=now.atZone(ZoneId.of("Europe/Warsaw"));val date=if(local.hour<1) local.toLocalDate().minusDays(1) else local.toLocalDate()
  return date.takeIf { it.dayOfWeek==DayOfWeek.SUNDAY }?.toString()
 }
 fun bonus(text:String):Double?=Regex("[+]([0-9]+(?:[.,][0-9]+)?\\s*[kmbt]?)\\s*%",RegexOption.IGNORE_CASE).find(text.replace(Regex("\\([^)]*\\)"),""))?.groupValues?.get(1)?.let(BattleText::number)
 fun researchBonus(text:String,health:Boolean):Double? {
  val normalized=BattleText.normalize(text)
  if(!normalized.contains("wojn") && !normalized.contains("clan war"))return null
  val expected=if(health) listOf("zdrow","health") else listOf("obrazen","damage")
  if(expected.none { normalized.contains(it) })return null
  val clean=text.replace(Regex("\\([^)]*\\)"),"")
  val values=Regex("[+]?\\s*([0-9]+(?:[.,][0-9]+)?\\s*[kmbt]?)\\s*%",RegexOption.IGNORE_CASE)
   .findAll(clean).mapNotNull {BattleText.number(it.groupValues[1].trim())}.toList()
  return values.singleOrNull()?.takeIf {it in 0.0..100000.0}
 }
 fun bonuses(text:String):Pair<Double,Double>? {
  // A rival without tech shows "0% Obrażenia i 0% Zdrowie" (no plus sign); OCR may read that zero as "O"
  // and the percent sign as "°/o" or "o/o".
  val normalized=BattleText.normalize(text).replace(Regex("\\s*(?:°\\s*/\\s*o|o\\s*/\\s*o|0\\s*/\\s*0)(?![a-z])"),"%")
   .replace(Regex("(?<![a-z0-9])o(\\s*%)"),"0$1")
  val amount="([0-9]+(?:[.,][0-9]+)?\\s*[kmbt]?)\\s*%"
  val all=Regex("(?<![0-9.,])[+]?"+amount).findAll(normalized).toList()
  if(all.size!=2)return null
  // Bare percentages without any trace of the bonus window are not trusted.
  if(!Regex("premi|rywaliz|technolog|obraz|zdrow|damage|health").containsMatchIn(normalized))return null
  fun named(label:String):Double? {
   val after=Regex("(?<![0-9.,])[+]?"+amount+"\\s*(?:"+label+")").find(normalized)
   val before=Regex("(?:"+label+")\\s*[+:]?\\s*"+amount).find(normalized)
   return (after?:before)?.groupValues?.get(1)?.let(BattleText::number)
  }
  // The game always lists damage first, then health; use that order when OCR garbles a label.
  val ordered=all.map {it.groupValues[1]}.map(BattleText::number)
  val damage=named("obraz|damage")?:ordered[0]?:return null
  val health=named("zdrow|health")?:ordered[1]?:return null
  return if(damage in 0.0..100000.0 && health in 0.0..100000.0) damage to health else null
 }
 fun memberCount(text:String):Int? {
  val counts=Regex("(?:czlonkowie|members)\\s*[:]?\\s*(\\d{1,3})\\s*/\\s*(\\d{1,3})(?![0-9])")
   .findAll(BattleText.normalize(text)).mapNotNull {m ->val n=m.groupValues[1].toInt();val capacity=m.groupValues[2].toInt();n.takeIf {capacity in 1..50&&n in 0..capacity}}.distinct().toList()
  return counts.singleOrNull()
 }

 fun members(text:String):List<WarMember> {
  val out=mutableListOf<WarMember>();var previous=""
  for(line in text.lines()) {
   val n=BattleText.normalize(line)
   if(n.contains("laczna") || n.contains("technolog") || n.contains("czlonkowie")) { previous="";continue }
   val number=Regex("(?<![a-z0-9])([0-9]+(?:[.,][0-9]+)?\\s*[kmbt])(?![a-z])",RegexOption.IGNORE_CASE).find(line)
   if(number!=null) {
    val inline=line.substring(0,number.range.first).trim().trim { !it.isLetterOrDigit() }
    val name=BattleText.playerName(if(inline.any(Char::isLetter))inline else previous).replace(Regex("[♂♀].*$"),"").trim()
    if(name.isNotBlank())BattleText.number(number.groupValues[1])?.let {out+=WarMember(name,it)}
    previous=""
   }
   else if(line.any(Char::isLetter) && !Regex("^(r[0-9]+(?:/[0-9]+)?|lider|dowodca|kapitan|online|[0-9]+.*temu|[0-9]+.*ago)$").matches(n)) previous=line.trim()
  }
  return out
 }
}
