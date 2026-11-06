package eu.komars.forgescanner.battle

/**
 * Season from 2026-11-03 (FairyHelpers.CalculateRawBonus, community-decoded):
 * steps = floor(|required displayed total| / divider + 0.001); raw = steps * per-level step.
 * Mira: per step of -4% skill cooldown, reduces incoming critical damage; pool total capped at 70%.
 * The Mira window shows only the per-step coefficient, never a current bonus, so it is calculated.
 * Tira converts health into the Damage multiplier, which the profile's displayed damage already includes.
 */
internal object FairyEffects {
 const val MIRA_DIVIDER=4.0
 const val MIRA_CAP=70.0
 private const val TOLERANCE=0.001
 fun steps(required:Double,divider:Double):Int=if(!required.isFinite()||required==0.0)0 else kotlin.math.abs(kotlin.math.floor(kotlin.math.abs(required)/divider+TOLERANCE)).toInt()
 /** Per-step coefficient read from the Mira description, e.g. "o 4.5% (+0.5%) za każde -4%". */
 fun miraCoefficient(detail:String):Double?=FairyText.firstPercent(detail)?.takeIf {it>0&&it<=MIRA_CAP}
 /** Final critical-reduction pool: other sources plus the fairy grant, clamped by the fairy cap. */
 fun miraReduction(coefficient:Double,cooldownReduction:Double,otherReduction:Double=0.0):Double {
  val raw=steps(cooldownReduction,MIRA_DIVIDER)*coefficient
  return if(raw<=0)otherReduction else minOf(otherReduction+raw,MIRA_CAP)
 }
}
