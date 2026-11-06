package eu.komars.forgescanner.battle
/** Missing text in a later contrast is not evidence that an earlier result was absent. Conflicts stay unresolved. */
class SkillReadEvidence {
 private val kinds=mutableSetOf<BattleSkill>();private val visuals=mutableSetOf<BattleSkill>();private val levels=mutableSetOf<Int>()
 val kind:BattleSkill? get()=if(kinds.isNotEmpty())kinds.singleOrNull()else visuals.singleOrNull()
 val level:Int? get()=levels.singleOrNull()
 fun addVisual(kind:BattleSkill?) {if(kind!=null)visuals+=kind}
 fun add(kind:BattleSkill?,level:Int?) {if(kind!=null)kinds+=kind;if(level!=null&&level in 1..100)levels+=level}
}
