package eu.komars.forgescanner.vision

import org.junit.Assert.*
import org.junit.Test

class CounterFramePrefetchTest {
    private class Harness {
        val released = mutableListOf<Int>()
        var producer: ((Int?, String?) -> Unit)? = null
        var cancelled: ((Int?, String?) -> Unit)? = null
        val slot = CounterFramePrefetch<Int>({ released += it }, { cancelled = it })
        fun start() = slot.start { producer = it }
    }

    @Test fun captureCanFinishDuringOcrAndWaitForItsSingleConsumer() {
        val h = Harness(); h.start(); h.producer!!(2, null)
        var delivered: Int? = null
        h.slot.take { value, _ -> delivered = value }
        assertEquals(2, delivered); assertTrue(h.slot.finished)
        h.slot.close(); assertTrue(h.released.isEmpty())
    }

    @Test fun fastOcrWaitsForTheIndependentFreshFrameInsteadOfReusingItsCurrentImage() {
        val h = Harness(); h.start()
        var delivered: Int? = null
        h.slot.take { value, _ -> delivered = value }
        assertNull(delivered)
        h.producer!!(3, null)
        assertEquals(3, delivered)
    }

    @Test fun missingFreshFrameRemainsAnErrorRatherThanAnotherVote() {
        val h = Harness(); h.start()
        var deliveredError: String? = null
        h.slot.take { value, error -> assertNull(value); deliveredError = error }
        h.producer!!(null, "no fresh image")
        assertEquals("no fresh image", deliveredError)
    }

    @Test fun cancellingBeforeScheduledStartDoesNotMakeAnyCaptureRequest() {
        val h = Harness(); h.slot.close(); h.start()
        assertNull(h.producer); assertNull(h.cancelled)
    }

    @Test fun cancellingAnOutstandingRequestUsesItsExactCallbackAndReleasesLateDelivery() {
        val h = Harness(); h.start(); h.slot.close()
        assertSame(h.producer, h.cancelled)
        h.producer!!(4, null)
        assertEquals(listOf(4), h.released)
        assertTrue(h.slot.finished)
    }

    @Test fun cancellationReleasesAnAlreadyCapturedImageWithoutCallingItsConsumer() {
        val h = Harness(); h.start(); h.producer!!(5, null); h.slot.close()
        var called = false
        h.slot.take { _, _ -> called = true }
        assertEquals(listOf(5), h.released); assertFalse(called); assertNull(h.cancelled)
    }

    @Test fun duplicateProducerCallbacksCannotOverwriteTheOwnedImage() {
        val h = Harness(); h.start(); h.producer!!(6, null); h.producer!!(7, null)
        var delivered: Int? = null
        h.slot.take { value, _ -> delivered = value }
        h.producer!!(8, null)
        assertEquals(6, delivered); assertEquals(listOf(7, 8), h.released)
    }

    @Test fun schedulingTwiceStillMakesOnlyOneFreshCaptureRequest() {
        val h = Harness(); var requests = 0
        repeat(2) { h.slot.start { requests++; h.producer = it } }
        assertEquals(1, requests)
    }
}
