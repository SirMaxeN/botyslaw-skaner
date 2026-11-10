package eu.komars.forgescanner.vision

import eu.komars.forgescanner.model.ResourceField
import eu.komars.forgescanner.model.ScanTarget
import org.junit.Assert.*
import org.junit.Test

class CounterOcrConsensusTest {
    private fun frame(vararg values: Long, field: ResourceField? = null, approximate: Boolean = false) = OcrAnalysis(values.map {
        OcrCandidate(field, it, it.toString(), ScreenBounds(10, 10, 100, 50), .8f, approximate, "test")
    }, emptyList())

    @Test fun allFiveNumericTargetsUseThreeIndependentFrames() {
        val targets = ScanTarget.entries.filterNot { it.isInventory }
        assertEquals(10, targets.size) // 5 scored counters + coins, gems, shells, red and green vials
        for (target in targets) {
            val result = CounterOcrConsensus.combine(List(3) { frame(188) }, target)
            assertFalse(target.title, result.requiresConfirmation)
            assertEquals(target.field, result.candidates.single().field)
        }
    }

    @Test fun resourceZeroIsValidButCostZeroIsNeverACandidate() {
        assertFalse(CounterOcrConsensus.combine(List(3) { frame(0) }, ScanTarget.TICKETS).requiresConfirmation)
        val cost = CounterOcrConsensus.combine(List(3) { frame(0) }, ScanTarget.SKILL_COST)
        assertTrue(cost.requiresConfirmation); assertTrue(cost.candidates.isEmpty()); assertNotNull(cost.error)
    }

    @Test fun contrastVariantsCannotOutvoteTwoOtherSourceFrames() {
        val result = CounterOcrConsensus.combine(listOf(frame(888, 888, 888, 888), frame(388), frame(388)), ScanTarget.MOUNT_KEYS)
        assertTrue(result.requiresConfirmation)
        assertEquals(388L, result.candidates.first().value)
        assertTrue(result.candidates.first().reason.contains("2/3"))
    }

    @Test fun threeConsistentButAmbiguousFramesRequireReview() {
        assertTrue(CounterOcrConsensus.combine(List(3) { frame(188, 198) }, ScanTarget.SKILL_COST).requiresConfirmation)
    }

    @Test fun staleOrMissingFrameNeverCreatesConsensus() {
        assertTrue(CounterOcrConsensus.combine(listOf(frame(188)), ScanTarget.MOUNT_COST).requiresConfirmation)
        val missing = OcrAnalysis(emptyList(), emptyList(), "no fresh source image", true)
        assertTrue(CounterOcrConsensus.combine(listOf(frame(188), frame(188), missing), ScanTarget.MOUNT_COST).requiresConfirmation)
    }

    @Test fun mislabeledValuesFromTheOtherCounterAreExcluded() {
        val result = CounterOcrConsensus.combine(List(3) { frame(188, field = ResourceField.TICKETS) }, ScanTarget.SKILL_COST)
        assertTrue(result.candidates.isEmpty()); assertNotNull(result.error)
    }

    @Test fun priorUncertaintyIsNotSilentlyRemovedAndApproximationSurvives() {
        val uncertain = frame(188).copy(requiresConfirmation = true)
        assertTrue(CounterOcrConsensus.combine(List(3) { uncertain }, ScanTarget.HAMMERS).requiresConfirmation)
        val approximate = CounterOcrConsensus.combine(List(3) { frame(9880, approximate = true) }, ScanTarget.TICKETS)
        assertTrue(approximate.candidates.single().approximate)
    }

    @Test fun legacyPairedListPreservesUncertaintyEvenForASingleValue() {
        val uncertain = CounterOcrConsensus.combine(listOf(frame(188)), ScanTarget.SKILL_COST)
        val choices = CounterOcrConsensus.forManualPair(uncertain, ScanTarget.SKILL_COST)
        assertEquals(1, choices.size)
        assertTrue(choices.single().approximate)
        assertTrue(choices.single().reason.contains("ręcznie"))
        val stable = CounterOcrConsensus.combine(List(3) { frame(188) }, ScanTarget.SKILL_COST)
        assertFalse(CounterOcrConsensus.forManualPair(stable, ScanTarget.SKILL_COST).single().approximate)
    }
    @Test fun verificationRequiresACompleteCleanSourceFrameFirst() {
        assertFalse(CounterOcrPassPolicy.canVerify(emptyList(), ScanTarget.HAMMERS))
        assertTrue(CounterOcrPassPolicy.canVerify(listOf(frame(888)), ScanTarget.HAMMERS))
        assertTrue(CounterOcrPassPolicy.canVerify(listOf(frame(888), frame(888)), ScanTarget.HAMMERS))
        assertFalse(CounterOcrPassPolicy.canVerify(listOf(frame(888, 388)), ScanTarget.HAMMERS))
        assertFalse(CounterOcrPassPolicy.canVerify(listOf(frame(888), frame(388)), ScanTarget.HAMMERS))
        assertFalse(CounterOcrPassPolicy.canVerify(listOf(frame(888).copy(requiresConfirmation = true)), ScanTarget.HAMMERS))
    }

    @Test fun shortPathNeedsTwoDifferentImagePassesWithOneMatchingAnswerEach() {
        assertFalse(CounterOcrPassPolicy.verificationAgrees(listOf(frame(888)), ScanTarget.HAMMERS))
        assertTrue(CounterOcrPassPolicy.verificationAgrees(listOf(frame(888), frame(888)), ScanTarget.HAMMERS))
        assertFalse(CounterOcrPassPolicy.verificationAgrees(listOf(frame(888), frame(388)), ScanTarget.HAMMERS))
        assertFalse(CounterOcrPassPolicy.verificationAgrees(listOf(frame(888, 388), frame(888)), ScanTarget.HAMMERS))
        assertFalse(CounterOcrPassPolicy.verificationAgrees(listOf(frame(888), frame()), ScanTarget.HAMMERS))
        assertFalse(CounterOcrPassPolicy.verificationAgrees(listOf(frame(888), frame(888).copy(error = "failed transform")), ScanTarget.HAMMERS))
    }

    @Test fun verificationFiltersOtherFieldsAndRejectsZeroPrices() {
        assertFalse(CounterOcrPassPolicy.canVerify(listOf(frame(188, field = ResourceField.TICKETS)), ScanTarget.SKILL_COST))
        assertFalse(CounterOcrPassPolicy.verificationAgrees(List(2) { frame(0) }, ScanTarget.MOUNT_COST))
        assertTrue(CounterOcrPassPolicy.verificationAgrees(List(2) { frame(0) }, ScanTarget.MOUNT_KEYS))
    }

    @Test fun imageShortcutNeverReplacesMissingFreshScreenshotVotes() {
        val imagePasses = List(2) { frame(888) }
        assertTrue(CounterOcrPassPolicy.verificationAgrees(imagePasses, ScanTarget.HAMMERS))
        assertTrue(CounterOcrConsensus.combine(imagePasses, ScanTarget.HAMMERS).requiresConfirmation)
        val conflictingSourceFrames = listOf(frame(888), frame(388), frame(388))
        assertTrue(CounterOcrConsensus.combine(conflictingSourceFrames, ScanTarget.HAMMERS).requiresConfirmation)
    }


    @Test fun gameFontSettlesThreeFiveEightOnlyWhenEveryFrameAgreesWithAnOcrCandidate() {
        val frames = listOf(frame(9883), frame(9383), frame(9383))
        assertTrue(CounterOcrConsensus.combine(frames, ScanTarget.HAMMERS).requiresConfirmation)
        val settled = CounterOcrConsensus.combine(frames, ScanTarget.HAMMERS, listOf(9383L, 9383L, 9383L))
        assertFalse(settled.requiresConfirmation)
        assertEquals(9383L, settled.candidates.first().value)
        assertTrue(CounterOcrConsensus.combine(frames, ScanTarget.HAMMERS, listOf(9383L, null, 9383L)).requiresConfirmation)
        assertTrue(CounterOcrConsensus.combine(frames, ScanTarget.HAMMERS, listOf(1234L, 1234L, 1234L)).requiresConfirmation)
    }

    @Test fun gameFontNeverOverridesAStableOcrResult() {
        val stable = CounterOcrConsensus.combine(List(3) { frame(9383) }, ScanTarget.HAMMERS, listOf(9883L, 9883L, 9883L))
        assertFalse(stable.requiresConfirmation)
        assertEquals(9383L, stable.candidates.single().value)
    }
}
