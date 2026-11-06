package eu.komars.forgescanner.battle
/** Separate views, confirmed effect and learned glyphs; contrast passes alone cannot arbitrate a conflict. */
internal class FairyLevelEvidence {
 private val profile=mutableListOf<Int>()
 private val detail=mutableListOf<Int>()
 private var detailReads=0
 private val effects=mutableListOf<Double>()
 private var effectConflict=false
 fun addLoraEffect(description:Double?,current:Double?) {
  if(description!=null&&current!=null&&kotlin.math.abs(description-current)>.01)effectConflict=true
  (current?:description)?.takeIf {it.isFinite()&&it>0}?.let {effects+=it}
 }
 fun acceptedWithGlyph(level:Int?):Int?=level?.takeIf {it in setOf(3,5,8)&&detailReads>=2&&candidates.all {n->n in setOf(3,5,8)}}
 fun add(level:Int?,fromDetail:Boolean) {if(fromDetail)detailReads++;if(level!=null && level in 1..20)(if(fromDetail)detail else profile).add(level)}
 val candidates get()=(profile+detail).distinct().sorted()
 fun acceptedWithLoraBonus(bonus:Double?):Int? {
  if(bonus==null||!bonus.isFinite()||profile.isEmpty()||detail.isEmpty())return null
  val matching=candidates.filter {kotlin.math.abs(bonus-(30.0+10.0*(it-1)))<.01}
  val value=matching.singleOrNull()?:return null
  val confirmedEffect=!effectConflict&&effects.size>=2&&effects.all {kotlin.math.abs(it-bonus)<.01}
  return value.takeIf {confirmedEffect||(candidates.size==1&&it !in setOf(3,5,8))}
 }
 fun accepted():Int? {
  val value=candidates.singleOrNull()?:return null
  if(value in setOf(3,5,8))return null
  // Two agreeing reads, including the fairy window itself, are enough for an unambiguous digit.
  return value.takeIf {detail.isNotEmpty() && profile.size+detail.size>=2}
 }
 /** The only remaining suggestion is chosen automatically unless it is the known 3/5/8 OCR confusion. */
 fun automaticChoice(calculated:List<Int>):Int? {
  val single=FairyLevelChoices.values(candidates,calculated).singleOrNull()?:return null
  return single.takeIf {it !in setOf(3,5,8)||calculated==listOf(it)}
 }
}
