package eu.komars.forgescanner.vision

import org.junit.Assert.*
import org.junit.Test

class CounterRecoveryPlanTest {
    private val full = CounterBounds(100, 100, 300, 150)

    @Test fun proposalsAreBoundedAndKeepHeadroomForFutureDigits() {
        val old = CounterBounds(160, 105, 220, 145)
        val plan = CounterRecoveryPlan.create(old, full, full, 1080, 2400, .84f)
        assertEquals(3, plan.candidates.size)
        assertEquals(old, plan.candidates.first().crop)
        assertTrue(plan.verifyOriginal)
        assertTrue(plan.candidates.drop(1).all { full.contains(it.crop) && it.persistence == full })
        assertTrue(plan.candidates.drop(1).all { it.crop.left == full.left && it.crop.right == full.right })
    }

    @Test fun extraDigitsGrowingLeftRequireTheFullAnchoredNumber() {
        val old = CounterBounds(130, 100, 300, 150)
        assertTrue(CounterRecoveryPlan.create(old, full, full, 1080, 2400, .84f).verifyOriginal)
    }

    @Test fun aSmallRightHandTruncationAlsoRequiresAnchorVerification() {
        val old = CounterBounds(100, 100, 280, 150)
        assertTrue(CounterRecoveryPlan.create(old, full, full, 1080, 2400, .84f).verifyOriginal)
    }

    @Test fun noAnchorMeansNoGuessingIntoNeighbouringCounters() {
        val plan = CounterRecoveryPlan.create(full, null, null, 1080, 2400, 0f)
        assertEquals(listOf(full), plan.candidates.map { it.crop })
        assertFalse(plan.verifyOriginal)
        val weak = CounterRecoveryPlan.create(full, CounterBounds(500, 500, 700, 550), null, 1080, 2400, .4f)
        assertEquals(1, weak.candidates.size)
    }

    @Test fun identicalRegionsAreNotRepeatedAsIndependentAttempts() {
        val plan = CounterRecoveryPlan.create(full, full, full, 1080, 2400, .84f)
        assertEquals(2, plan.candidates.size)
        assertEquals(plan.candidates.size, plan.candidates.map { it.crop }.distinct().size)
        assertFalse(plan.verifyOriginal)
    }

    @Test fun outOfFrameOriginalCanOnlyBeReplacedByAValidAnchor() {
        val invalid = CounterBounds(-5, 100, 200, 150)
        val plan = CounterRecoveryPlan.create(invalid, full, full, 1080, 2400, .84f)
        assertTrue(plan.candidates.first().anchored)
        assertTrue(plan.candidates.all { it.crop.valid(1080, 2400) })
        assertTrue(CounterRecoveryPlan.create(invalid, null, null, 1080, 2400, 0f).candidates.isEmpty())
    }

    @Test fun nonFiniteConfidenceCannotAuthorizeAnAutomaticCrop() {
        val plan = CounterRecoveryPlan.create(full, full, full, 1080, 2400, Float.NaN)
        assertEquals(1, plan.candidates.size)
    }

    @Test fun conflictingStableValuesRemainConflictingRegardlessOfSizeOrLaterVotes() {
        val agreement = CounterRecoveryAgreement()
        assertTrue(agreement.observeStable(188))
        assertTrue(agreement.observeStable(188))
        assertFalse(agreement.observeStable(5))
        assertFalse(agreement.observeStable(188))
        assertTrue(agreement.conflicting)
    }

    @Test fun truncatedOldCropDoesNotVetoTheIndependentlyAnchoredCompleteNumber() {
        val old = CounterBounds(100, 100, 160, 150)
        val plan = CounterRecoveryPlan.create(old, full, full, 1080, 2400, .86f)
        assertTrue(plan.verifyOriginal)
        val agreement = CounterRecoveryAgreement()
        agreement.observeStable(123, trusted = !plan.verifyOriginal)
        assertTrue(agreement.observeStable(12345, trusted = true))
        assertFalse(agreement.conflicting)
        // Two independently trusted reads must still agree; an old crop is not
        // rejected merely because it contained the smaller numerical value.
        assertFalse(agreement.observeStable(12346, trusted = true))
        assertTrue(agreement.conflicting)
    }

    @Test fun aStableOldCropOutsideTheVerifiedContainerIsNotATrustedVote() {
        val old = CounterBounds(500, 500, 700, 550)
        val plan = CounterRecoveryPlan.create(old, full, full, 1080, 2400, .86f)
        assertTrue(plan.verifyOriginal)
        val agreement = CounterRecoveryAgreement()
        agreement.observeStable(999999, trusted = false)
        assertTrue(agreement.observeStable(188))
        assertFalse(agreement.conflicting)
    }
}
