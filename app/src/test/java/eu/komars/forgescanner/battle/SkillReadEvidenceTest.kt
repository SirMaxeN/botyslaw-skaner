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
 @Test fun gameFontLevelWinsOnlyWithoutContradictingOcr() {
  val missing=SkillReadEvidence();missing.add(BattleSkill.LIGHTNING,null);missing.addFont(18);assertEquals(18,missing.level)
  val agreeing=SkillReadEvidence();agreeing.add(null,18);agreeing.add(null,16);agreeing.addFont(18);assertEquals(18,agreeing.level)
  val contradicting=SkillReadEvidence();contradictingSetup(contradicting);assertEquals(null,contradicting.level)
 }
 private fun contradictingSetup(e:SkillReadEvidence){e.add(null,16);e.add(null,13);e.addFont(18)}
}
