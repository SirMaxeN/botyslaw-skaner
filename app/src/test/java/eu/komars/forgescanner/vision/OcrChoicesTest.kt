package eu.komars.forgescanner.vision

import org.junit.Assert.*
import org.junit.Test

class OcrChoicesTest {
    private fun candidate(value: Long, left: Int, confidence: Float = 0.8f, approximate: Boolean = false) =
        OcrCandidate(null, value, value.toString(), ScreenBounds(left, 20, left+40, 60), confidence, approximate, "test")
    @Test fun sameValueFromDifferentPassesAppearsOnce() {
        val choices = OcrChoices.unique(listOf(candidate(2997, 10, 0.5f), candidate(2997, 12, 0.9f)), false)
        assertEquals(1, choices.size); assertEquals(12, choices.single().bounds.left)
    }
    @Test fun genuinelyDifferentValuesStillRequireChoice() {
        assertEquals(2, OcrChoices.unique(listOf(candidate(2997, 10), candidate(2991, 12)), false).size)
    }
    @Test fun exactReadingWinsOverApproximateAndZeroIsAllowedForResourcesOnly() {
        val choices = OcrChoices.unique(listOf(candidate(0,10),candidate(200,20,1f,true),candidate(200,30,0.5f)),false)
        assertEquals(2, choices.size); assertFalse(choices.first { it.value == 200L }.approximate)
        assertEquals(1, OcrChoices.unique(choices,true).size)
    }
}
