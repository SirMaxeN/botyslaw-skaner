package eu.komars.forgescanner.battle

import org.junit.Assert.*
import org.junit.Test

class VerifiedWarTransitionTest {
    @Test fun savedRouteNeedsOneDestinationButStillRejectsWrongScreen() {
        val fast=Harness(1);fast.start();fast.advance(250)
        assertTrue(fast.result?.isSuccess==true);assertEquals(1,fast.taps)
        val wrong=Harness(1);wrong.screen=setOf("home");wrong.start()
        assertTrue(wrong.result?.isFailure==true);assertEquals(0,wrong.taps)
    }
    private class Harness(confirmations:Int=2) {
        data class Job(val at: Long, val id: Int, val action: () -> Unit, var cancelled: Boolean = false)
        var now = 0L
        var serial = 0
        var screen = setOf("war")
        var taps = 0
        var completions = 0
        var result: Result<Unit>? = null
        var holdObservations = false
        var holdTaps = false
        var duplicateCallbacks = false
        var observeError: Exception? = null
        var tapError: Exception? = null
        var tapAction: () -> Unit = { screen = setOf("bonus") }
        val held = mutableListOf<(Result<Set<String>>) -> Unit>()
        val heldTaps = mutableListOf<(Result<Unit>) -> Unit>()
        val jobs = mutableListOf<Job>()
        val runner = VerifiedWarTransition(object : VerifiedWarTransition.Port {
            override fun observe(complete: (Result<Set<String>>) -> Unit) {
                observeError?.let { throw it }
                if (holdObservations) held += complete else {
                    complete(Result.success(screen))
                    if (duplicateCallbacks) complete(Result.success(screen))
                }
            }
            override fun tap(complete: (Result<Unit>) -> Unit) {
                taps++
                tapError?.let { throw it }
                if (holdTaps) heldTaps += complete else {
                    tapAction()
                    complete(Result.success(Unit))
                    if (duplicateCallbacks) complete(Result.success(Unit))
                }
            }
            override fun cancel() = Unit
        }, VerifiedWarTransition.Scheduler { delay, action ->
            val job = Job(now + delay, serial++, action)
            jobs += job;
            { job.cancelled = true }
        }, { now }, timeoutMillis = 2_000L,requiredDestinationObservations=confirmations)

        fun start(allowAlready: Boolean = false) = runner.start(setOf("war"), "bonus", allowAlready) {
            result = it; completions++
        }
        fun advance(target: Long) {
            while (true) {
                val job = jobs.filter { !it.cancelled && it.at <= target }
                    .minWithOrNull(compareBy<Job> { it.at }.thenBy { it.id }) ?: break
                job.cancelled = true
                now = job.at
                job.action()
            }
            now = target
        }
    }

    @Test fun unknownSourceNeverAuthorizesTapAndHasDeadline() {
        val h = Harness(); h.screen = emptySet(); h.start(); h.advance(1_999)
        assertEquals(0, h.taps); assertNull(h.result)
        h.advance(2_000)
        assertTrue(h.result!!.isFailure); assertEquals(1, h.completions)
    }

    @Test fun unknownSourceMaySettleBeforeSingleTap() {
        val h = Harness(); h.screen = emptySet(); h.start(); h.advance(500)
        assertEquals(0, h.taps)
        h.screen = setOf("war"); h.advance(1_250)
        assertEquals(1, h.taps); assertTrue(h.result!!.isSuccess)
    }

    @Test fun recognizedWrongSourceFailsImmediatelyWithoutTap() {
        val h = Harness(); h.screen = setOf("research"); h.start()
        assertEquals(0, h.taps); assertTrue(h.result!!.isFailure)
    }

    @Test fun completedGestureWithoutDestinationDoesNotAdvanceOrRetap() {
        val h = Harness(); h.tapAction = {}; h.start(); h.advance(1_999)
        assertEquals(1, h.taps); assertNull(h.result)
        h.advance(2_000)
        assertTrue(h.result!!.isFailure); assertEquals(1, h.taps)
    }

    @Test fun twoSeparateDestinationObservationsAreRequired() {
        val h = Harness(); h.start(); h.advance(250)
        assertNull(h.result)
        h.advance(499); assertNull(h.result)
        h.advance(500)
        assertTrue(h.result!!.isSuccess); assertEquals(1, h.completions); assertEquals(1, h.taps)
    }

    @Test fun intermittentDestinationResetsStability() {
        val h = Harness(); h.start(); h.advance(250)
        h.screen = emptySet(); h.advance(500)
        h.screen = setOf("bonus"); h.advance(750); assertNull(h.result)
        h.screen = setOf("war"); h.advance(1_000)
        h.screen = setOf("bonus"); h.advance(1_250); assertNull(h.result)
        h.advance(1_500); assertTrue(h.result!!.isSuccess); assertEquals(1, h.taps)
    }

    @Test fun slowTransitionThroughUnrelatedScreenStillTapsOnlyOnce() {
        val h = Harness(); h.tapAction = { h.screen = setOf("animation") }
        h.start(); h.advance(1_000); assertNull(h.result); assertEquals(1, h.taps)
        h.screen = setOf("bonus"); h.advance(1_500)
        assertTrue(h.result!!.isSuccess); assertEquals(1, h.taps)
    }

    @Test fun alreadyDestinationMustBeExplicitAndStillBeStable() {
        val rejected = Harness(); rejected.screen = setOf("bonus"); rejected.start()
        assertTrue(rejected.result!!.isFailure); assertEquals(0, rejected.taps)
        val h = Harness(); h.screen = setOf("bonus"); h.start(allowAlready = true)
        h.advance(249); assertNull(h.result)
        h.advance(250); assertTrue(h.result!!.isSuccess); assertEquals(0, h.taps)
    }

    @Test fun stopIgnoresHeldObservationsAndEvenCancelledTimers() {
        val h = Harness(); h.holdObservations = true; h.start()
        val callbacks = h.jobs.map { it.action }
        h.runner.cancel()
        h.held.single()(Result.success(setOf("war")))
        callbacks.forEach { it() }
        assertEquals(0, h.taps); assertEquals(0, h.completions)
    }

    @Test fun stopIgnoresHeldTapCompletion() {
        val h = Harness(); h.holdTaps = true; h.start(); h.runner.cancel()
        h.heldTaps.single()(Result.success(Unit)); h.advance(5_000)
        assertEquals(1, h.taps); assertEquals(0, h.completions)
    }

    @Test fun newStartInvalidatesPreviousObservationAndTimers() {
        val h = Harness(); h.holdObservations = true; h.start()
        val old = h.held.single(); val oldTimers = h.jobs.map { it.action }
        h.holdObservations = false; h.start()
        old(Result.success(setOf("war"))); oldTimers.forEach { it() }
        h.advance(500)
        assertTrue(h.result!!.isSuccess); assertEquals(1, h.taps); assertEquals(1, h.completions)
    }

    @Test fun duplicateCallbacksCannotCountAsIndependentObservations() {
        val h = Harness(); h.duplicateCallbacks = true; h.start(); h.advance(250)
        assertNull(h.result); assertEquals(1, h.taps)
        h.advance(5_000)
        assertTrue(h.result!!.isSuccess); assertEquals(1, h.completions); assertEquals(1, h.taps)
    }

    @Test fun deadlineAlsoBoundsMissingObservationOrTapCallback() {
        val waitingFrame = Harness(); waitingFrame.holdObservations = true; waitingFrame.start()
        waitingFrame.advance(2_000)
        assertTrue(waitingFrame.result!!.isFailure)
        waitingFrame.held.single()(Result.success(setOf("war")))
        assertEquals(0, waitingFrame.taps); assertEquals(1, waitingFrame.completions)
        val waitingTap = Harness(); waitingTap.holdTaps = true; waitingTap.start(); waitingTap.advance(2_000)
        waitingTap.heldTaps.single()(Result.success(Unit))
        assertTrue(waitingTap.result!!.isFailure); assertEquals(1, waitingTap.completions)
    }

    @Test fun portExceptionsBecomeSingleFailure() {
        val observing = Harness(); observing.observeError = IllegalStateException("capture"); observing.start()
        assertTrue(observing.result!!.isFailure); assertEquals(0, observing.taps)
        val tapping = Harness(); tapping.tapError = IllegalStateException("gesture"); tapping.start()
        tapping.advance(5_000)
        assertTrue(tapping.result!!.isFailure); assertEquals(1, tapping.completions)
    }

    @Test fun failedObservationAndTapResultsStopImmediately() {
        val h = Harness(); h.holdObservations = true; h.start()
        h.held.single()(Result.failure(IllegalStateException("frame")))
        assertTrue(h.result!!.isFailure); assertEquals(0, h.taps)
        val t = Harness(); t.holdTaps = true; t.start()
        t.heldTaps.single()(Result.failure(IllegalStateException("tap")))
        assertTrue(t.result!!.isFailure); assertEquals(1, t.completions)
    }

    @Test fun schedulerExceptionIsReportedEvenWhenCancellationHookThrows() {
        var result: Result<Unit>? = null
        var completions = 0
        val runner = VerifiedWarTransition(object : VerifiedWarTransition.Port {
            override fun observe(complete: (Result<Set<String>>) -> Unit) = fail("Must not observe")
            override fun tap(complete: (Result<Unit>) -> Unit) = fail("Must not tap")
            override fun cancel() { throw IllegalStateException("cancel") }
        }, VerifiedWarTransition.Scheduler { _, _ -> throw IllegalStateException("schedule") }, { 0L })
        runner.start(setOf("war"), "bonus") { result = it; completions++ }
        assertTrue(result!!.isFailure); assertEquals(1, completions)
    }
}
