package eu.komars.forgescanner.vision

import eu.komars.forgescanner.model.ResourceField
import eu.komars.forgescanner.model.ScanStep
import org.junit.Assert.*
import org.junit.Test

class OcrCandidateExtractorTest {
    @Test fun matchesLabelsButLeavesIconOnlyCountersUnassigned() {
        val found = OcrCandidateExtractor.extract(listOf(OcrLine("Bilety: 9,88k", ScreenBounds(0, 0, 200, 30))), ScanStep.SKILL_CURRENCY)
        assertEquals(9880L, found.single().value)
        assertEquals(ResourceField.TICKETS, found.single().field)
        assertTrue(found.single().approximate)
        val unlabeled = OcrCandidateExtractor.extract(listOf(OcrLine("4040", ScreenBounds(0, 0, 80, 30))), ScanStep.MOUNT_CURRENCY)
        assertNull(unlabeled.single().field)
    }

    @Test fun ignoresPackMultipliers() {
        val found = OcrCandidateExtractor.extract(listOf(OcrLine("x5 188", ScreenBounds(0, 0, 200, 30))), ScanStep.SKILL_CURRENCY)
        assertFalse(found.any { it.value == 5L })
        assertTrue(found.any { it.value == 188L })
    }

    @Test fun readsTightLabelsAndSkipsClockComponents() {
        val found = OcrCandidateExtractor.extract(listOf(
            OcrLine("Koszt:188", ScreenBounds(0, 0, 100, 30)),
            OcrLine("12:30", ScreenBounds(0, 500, 60, 530))
        ), ScanStep.SKILL_CURRENCY)
        assertEquals(188L, found.single().value)
        assertEquals(ResourceField.SKILL_COST, found.single().field)
    }
}
