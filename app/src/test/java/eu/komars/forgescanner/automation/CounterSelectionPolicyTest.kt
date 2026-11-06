package eu.komars.forgescanner.automation

import org.junit.Assert.assertEquals
import org.junit.Test

class CounterSelectionPolicyTest {
    @Test fun aVerifiedSingleValueAcceptsTheConfirmedGeometry() {
        assertEquals(CounterSelectionPolicy.Action.ACCEPT, CounterSelectionPolicy.decide(1, true, 0))
    }

    @Test fun anUncertainReadRetriesTheSameRectangleBeforeAskingTheUser() {
        assertEquals(CounterSelectionPolicy.Action.RETRY_SAME_REGION, CounterSelectionPolicy.decide(2, false, 0))
        assertEquals(CounterSelectionPolicy.Action.RETRY_SAME_REGION, CounterSelectionPolicy.decide(0, false, 0))
    }

    @Test fun conflictingDigitsStillRequireExplicitChoiceAfterTheRetry() {
        assertEquals(CounterSelectionPolicy.Action.CONFIRM_VALUE, CounterSelectionPolicy.decide(2, false, 1))
        // Even a lone candidate is not automatic evidence if source frames were incomplete.
        assertEquals(CounterSelectionPolicy.Action.CONFIRM_VALUE, CounterSelectionPolicy.decide(1, false, 1))
    }

    @Test fun unreadableResultsOfferRetryWithoutClaimingTheGeometryIsWrong() {
        assertEquals(CounterSelectionPolicy.Action.RETRY_OR_REDRAW, CounterSelectionPolicy.decide(0, false, 1))
    }
}
