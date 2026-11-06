package eu.komars.forgescanner.model
import org.junit.Assert.*
import org.junit.Test
class ResourceDraftRestoreTest {
 @Test fun restoringDoesNotInventConfirmationOrFreshTimestamp() {val saved=ScanSnapshot(ScanStep.REVIEW,mapOf(ResourceField.HAMMERS to ResourceObservation(120,false,true)),100,150);val s=ScanSession.restore(saved);assertEquals(saved,s.snapshot());assertFalse(s.snapshot().readyToExport)}
 @Test fun editsAndRemovalPublishCurrentDraft() {val s=ScanSession();val updates=mutableListOf<ScanSnapshot>();s.onChange={updates+=it};s.setValue(ResourceField.SKILL_COST,188,false);s.confirm(ResourceField.SKILL_COST);s.removeValue(ResourceField.SKILL_COST);assertEquals(3,updates.size);assertFalse(updates[0].values.getValue(ResourceField.SKILL_COST).confirmed);assertTrue(updates[1].values.getValue(ResourceField.SKILL_COST).confirmed);assertTrue(updates[2].values.isEmpty())}
}
