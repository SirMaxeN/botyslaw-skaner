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
        assertTrue(CounterOcrConsensus.combine(listOf(frame(188), missing), ScanTarget.MOUNT_COST).requiresConfirmation)
        assertFalse(CounterOcrConsensus.combine(listOf(frame(188), frame(188), missing), ScanTarget.MOUNT_COST).requiresConfirmation)
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
        assertEquals(9383L, settled.candidates.single().value)
        // Two of three game-font frames agreeing with an OCR candidate are enough (fewer prompts, 2026-11-16).
        assertEquals(9383L, CounterOcrConsensus.combine(frames, ScanTarget.HAMMERS, listOf(9383L, null, 9383L)).candidates.single().value)
        assertTrue(CounterOcrConsensus.combine(frames, ScanTarget.HAMMERS, listOf(1234L, 1234L, 1234L)).requiresConfirmation)
    }

    @Test fun disagreementBetweenTwoStableReadersRequiresReview() {
        val result = CounterOcrConsensus.combine(List(3) { frame(9383) }, ScanTarget.HAMMERS, listOf(9883L, 9883L, 9883L))
        assertTrue(result.requiresConfirmation)
        assertEquals(setOf(9383L, 9883L), result.candidates.map { it.value }.toSet())
    }

    @Test fun strictFontCanReadZeroAndRedCountersWhenTextOcrMissesThem() {
        val missing = List(3) { OcrAnalysis(emptyList(), emptyList(), "Brak OCR", true) }
        for (value in listOf(0L, 835L)) {
            val result = CounterOcrConsensus.combine(missing, ScanTarget.HAMMERS, List(3) { value })
            assertFalse(result.requiresConfirmation)
            assertEquals(value, result.candidates.single().value)
        }
        assertTrue(CounterOcrConsensus.combine(missing, ScanTarget.HAMMERS, listOf(0L, null, 0L)).requiresConfirmation)
        assertTrue(CounterOcrConsensus.combine(missing, ScanTarget.MOUNT_COST, List(3) { 0L }).candidates.isEmpty())
    }

    @Test fun aValueInEveryReadingFrameWinsOverAOneFrameAlternative() {
        val result = CounterOcrConsensus.combine(listOf(frame(188), frame(188, 198), frame(188)), ScanTarget.SKILL_COST)
        assertFalse(result.requiresConfirmation)
        assertEquals(188L, result.candidates.single().value)
        val twoFrames = CounterOcrConsensus.combine(listOf(frame(9880), frame(9880), OcrAnalysis(emptyList(), emptyList())), ScanTarget.TICKETS)
        assertEquals(9880L, twoFrames.candidates.single().value)
    }

    @Test fun majorityStillAsksOnRealContradictions() {
        // One frame says something else entirely: no silent guess without the game font.
        assertTrue(CounterOcrConsensus.combine(listOf(frame(888), frame(388), frame(388)), ScanTarget.HAMMERS).requiresConfirmation)
        // The alternative also appears in two frames: a tie.
        assertTrue(CounterOcrConsensus.combine(listOf(frame(188, 198), frame(188, 198), frame(188)), ScanTarget.SKILL_COST).requiresConfirmation)
        // A game-font majority for another value overrules the OCR majority only by asking.
        assertTrue(CounterOcrConsensus.combine(listOf(frame(188), frame(188, 198), frame(188)), ScanTarget.SKILL_COST, listOf(198L, 198L, null)).requiresConfirmation)
        // A different abbreviation contradicts; the same abbreviated value does not.
        assertTrue(CounterOcrConsensus.combine(listOf(frame(9876), frame(9876), frame(9900, approximate = true)), ScanTarget.TICKETS).requiresConfirmation)
        val consistent = CounterOcrConsensus.combine(listOf(frame(9876), frame(9876), frame(9880, approximate = true)), ScanTarget.TICKETS)
        assertFalse(consistent.requiresConfirmation)
        assertEquals(9876L, consistent.candidates.single().value)
    }

    @Test fun gameFontMajorityResolvesAOneFrameOcrSlip() {
        val result = CounterOcrConsensus.combine(listOf(frame(888), frame(388), frame(388)), ScanTarget.HAMMERS, listOf(388L, 388L, null))
        assertFalse(result.requiresConfirmation)
        assertEquals(388L, result.candidates.single().value)
    }

    @Test fun moreBurstsTurnAThreeFrameDisagreementIntoAConfidentReading() {
        val first = listOf(frame(888), frame(388), frame(388))
        assertTrue(CounterOcrConsensus.combine(first, ScanTarget.HAMMERS).requiresConfirmation)
        val nine = first + listOf(frame(388), frame(388), frame(388), frame(388), frame(388, 888), OcrAnalysis(emptyList(), emptyList()))
        val result = CounterOcrConsensus.combine(nine, ScanTarget.HAMMERS)
        assertFalse(result.requiresConfirmation)
        assertEquals(388L, result.candidates.single().value)
    }

    @Test fun moreBurstsStillAskOnARealTieAndKeepFontVotesPerFrame() {
        val tie = List(3) { frame(888) } + List(3) { frame(388) }
        assertTrue(CounterOcrConsensus.combine(tie, ScanTarget.HAMMERS).requiresConfirmation)
        // Font votes are aligned with all six frames and need a half-majority.
        val settled = CounterOcrConsensus.combine(listOf(frame(888), frame(388), frame(388), frame(388), frame(888), frame(388)), ScanTarget.HAMMERS,
            listOf(388L, 388L, null, 388L, null, 388L))
        assertEquals(388L, settled.candidates.single().value)
    }

    private fun read(value: Long, text: String, approximate: Boolean = false) =
        OcrCandidate(null, value, text, ScreenBounds(10, 10, 100, 50), .8f, approximate, "test")
    private fun frameOf(vararg candidates: OcrCandidate) = OcrAnalysis(candidates.toList(), emptyList())

    @Test fun logCase20261116HammersStrayZeroAndLastDigitWobbleIsSettledByTheGameFont() {
        val frames = List(3) { frame(0, 8951, 8950) }
        assertTrue(CounterOcrConsensus.combine(frames, ScanTarget.HAMMERS).requiresConfirmation)
        val settled = CounterOcrConsensus.combine(frames, ScanTarget.HAMMERS, listOf(8951L, 8951L, 8951L))
        assertFalse(settled.requiresConfirmation)
        assertEquals(8951L, settled.candidates.single().value)
        // The stray 0 alone is noise next to a 4-digit number.
        assertEquals(8951L, CounterOcrConsensus.combine(List(3) { frame(0, 8951) }, ScanTarget.HAMMERS).candidates.single().value)
    }

    @Test fun logCase20261116KeysShownAsThousandsAreNotReplacedByTheKReadAsADigit() {
        // "16.2k" on screen: OCR keeps the abbreviation, the font (or OCR) reads the k as 0 -> 1620, a badge as 9.
        val frames = List(3) { frameOf(read(16200, "16.2k", approximate = true), read(1620, "1620"), read(9, "9")) }
        val result = CounterOcrConsensus.combine(frames, ScanTarget.MOUNT_KEYS, List(3) { 1620L })
        assertFalse(result.requiresConfirmation)
        assertEquals(16200L, result.candidates.single().value)
        assertTrue(result.candidates.single().approximate)
        assertTrue(CounterOcrConsensus.shadowedByAbbreviation(162, setOf("162")))
        assertFalse(CounterOcrConsensus.shadowedByAbbreviation(16210, setOf("162")))
    }

    @Test fun abbreviatedOcrDoesNotGetOverriddenByDigitsMissingTheSuffix() {
        val result = CounterOcrConsensus.combine(List(3) { frame(1250, approximate = true) }, ScanTarget.HAMMERS, List(3) { 125L })
        assertTrue(result.requiresConfirmation)
        assertEquals(1250L, result.candidates.first().value)
    }

    @Test fun millionsShownAsMAreNotReplacedByTheWideMReadAsDigits() {
        // "1.25m": the wide m is split by the font into two digits (12500) or dropped by OCR (125).
        val frames = List(3) { frameOf(read(1_250_000, "1.25m", approximate = true), read(12500, "12500"), read(125, "125")) }
        val result = CounterOcrConsensus.combine(frames, ScanTarget.HAMMERS, List(3) { 12500L })
        assertFalse(result.requiresConfirmation)
        assertEquals(1_250_000L, result.candidates.single().value)
        assertTrue(CounterOcrConsensus.shadowedByAbbreviation(12500, mapOf("125" to 2)))
        // "k" stays strict: one extra digit at most.
        assertFalse(CounterOcrConsensus.shadowedByAbbreviation(16200, mapOf("162" to 1)))
    }

    @Test fun logCase20261116HammersValueInEveryFrameBeatsTheThreeReadAsEight() {
        // Every frame read 9307 in one OCR pass; 9807 (3 read as 8) in two of three frames, 49307 once. Font: none.
        val burst = listOf(frameOf(read(9807, "9807"), read(9307, "9307")), frameOf(read(9807, "9807"), read(9307, "9307")),
            frameOf(read(9307, "9307"), read(49307, "49307")))
        val three = CounterOcrConsensus.combine(burst, ScanTarget.HAMMERS, List(3) { null })
        assertTrue("three frames still ask", three.requiresConfirmation)
        val six = CounterOcrConsensus.combine(burst + listOf(frameOf(read(9807, "9807"), read(9307, "9307")), frameOf(read(9307, "9307")),
            frameOf(read(9307, "9307"))), ScanTarget.HAMMERS, List(6) { null })
        assertFalse(six.requiresConfirmation)
        assertEquals(9307L, six.candidates.single().value)
    }
}
