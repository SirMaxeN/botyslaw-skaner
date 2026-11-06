package eu.komars.forgescanner.battle
import org.junit.Assert.*
import org.junit.Test
class SkillReadEvidenceTest {
 @Test fun combinesNameAndLevelFromDifferentPasses() {val e=SkillReadEvidence();e.add(BattleSkill.entries.first(),null);e.add(null,23);e.add(null,null);assertEquals(BattleSkill.entries.first(),e.kind);assertEquals(23,e.level)}
 @Test fun contradictionsRemainManual() {val e=SkillReadEvidence();e.add(BattleSkill.entries.first(),3);e.add(BattleSkill.entries.last(),30);assertNull(e.kind);assertNull(e.level)}
 @Test fun missingOrInvalidLevelNeverBecomesZero() {val e=SkillReadEvidence();e.add(null,0);e.add(null,101);assertNull(e.level)}
 @Test fun clearNameOverridesWrongVisualHintButConflictingNamesStayUnresolved() {
  val e=SkillReadEvidence();e.addVisual(BattleSkill.entries.first());e.add(BattleSkill.entries.last(),12)
  assertEquals(BattleSkill.entries.last(),e.kind);assertEquals(12,e.level)
  e.add(BattleSkill.entries.first(),null);assertNull(e.kind)
 }
}
