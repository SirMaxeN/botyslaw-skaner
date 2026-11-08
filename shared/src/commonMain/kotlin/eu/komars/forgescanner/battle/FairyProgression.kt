package eu.komars.forgescanner.battle
import kotlin.math.abs
import kotlin.math.roundToInt

/** The coefficient immediately before the green (+step%) is distinct from total build bonus. */
object FairyProgression {
 data class Rule(val base:Double,val expectedIncrement:Double?=null)
 data class Reading(val type:String,val base:Double,val coefficient:Double,val increment:Double,val level:Int)
 private val pair=Regex("(?<![0-9.,])\\+?([0-9]+(?:[.,][0-9]+)?)\\s*%\\s*\\(\\s*\\+?([0-9]+(?:[.,][0-9]+)?)\\s*%\\s*\\)")
 fun infer(type:String,text:String,rule:Rule):Reading? {
  if(FairyText.type(text)!=type||!rule.base.isFinite()||rule.base<0)return null
  val description=BattleText.normalize(text).substringBefore("aktualny bonus")
  val pairs=pair.findAll(description).map {it.groupValues[1].replace(',','.').toDouble() to it.groupValues[2].replace(',','.').toDouble()}.distinct().toList()
  val (coefficient,increment)=pairs.singleOrNull()?:return null
  if(!coefficient.isFinite()||!increment.isFinite()||increment<=0||coefficient<rule.base-.0001)return null
  if(rule.expectedIncrement?.let {abs(it-increment)>.0001}==true)return null
  val value=1+(coefficient-rule.base)/increment
  if(!value.isFinite()||value !in .9999..20.0001)return null
  val level=value.roundToInt();if(abs(value-level)>.0001)return null
  return Reading(type,rule.base,coefficient,increment,level)
 }
}
class FairyProgressionEvidence {
 private val observations=mutableListOf<FairyProgression.Reading>()
 fun add(value:FairyProgression.Reading?){if(value!=null)observations+=value}
 val candidates get()=observations.map {it.level}.distinct().sorted()
 fun accepted():FairyProgression.Reading?=observations.firstOrNull()?.takeIf {first->observations.size>=2&&observations.all {it==first}}
 fun agreesWith(level:Int)=candidates.isEmpty()||candidates.singleOrNull()==level
}
object FairyLevelChoices {
 fun values(ocr:List<Int>,calculated:List<Int>)=(ocr+calculated).filter {it in 1..20}.distinct().sorted().take(6)
}
