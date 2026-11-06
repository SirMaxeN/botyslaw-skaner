package eu.komars.forgescanner.automation

import org.junit.Assert.*
import org.junit.Test

class SavedNavigationRunnerTest {
    private class Harness {
        data class Job(val at: Long, val id: Int, val action: () -> Unit, var cancelled: Boolean = false)
        var now = 0L
        private var serial = 0
        val jobs = mutableListOf<Job>()
        val taps = mutableListOf<AutoScanStep>()
        val callbacks = mutableListOf<(Result<Unit>) -> Unit>()
        var results = mutableListOf<Result<Unit>>()
        var automaticResult: Result<Unit>? = Result.success(Unit)
        var throwFromTap: Exception? = null
        var cancels = 0
        val runner = SavedNavigationRunner(object : SavedNavigationRunner.Port {
            override fun tap(step: AutoScanStep, complete: (Result<Unit>) -> Unit) {
                taps += step; callbacks += complete
                throwFromTap?.let { throw it }
                automaticResult?.let(complete)
            }
            override fun cancel() { cancels++ }
        }, SavedNavigationRunner.Scheduler { delay, action ->
            val job = Job(now + delay, serial++, action); jobs += job
            val cancel: () -> Unit = { job.cancelled = true }
            cancel
        })
        fun start(step: AutoScanStep) = runner.start(step) { results += it }
        fun advance(target: Long) {
            while (true) {
                val job = jobs.filter { !it.cancelled && it.at <= target }
                    .minWithOrNull(compareBy<Job> { it.at }.thenBy { it.id }) ?: break
                job.cancelled = true; now = job.at; job.action()
            }
            now = target
        }
    }

    @Test fun everyNavigationUsesOneTapAndOnlyItsFixedSettlingGap() {
        val timings = listOf(AutoScanStep.CLOSE_MOUNTS to 600L,
            AutoScanStep.CLOSE_RESEARCH to 600L, AutoScanStep.OPEN_RESEARCH to 450L,
            AutoScanStep.OPEN_MOUNTS to 400L, AutoScanStep.OPEN_SKILLS to 320L,
            AutoScanStep.OPEN_PETS to 320L)
        for ((step, duration) in timings) {
            val h = Harness(); h.start(step)
            assertEquals(listOf(step), h.taps)
            assertTrue(h.results.isEmpty())
            h.advance(duration - 1); assertTrue(h.results.isEmpty())
            h.advance(duration)
            assertTrue(step.name, h.results.single().isSuccess)
            h.advance(20_000)
            assertEquals(listOf(step), h.taps); assertEquals(1, h.results.size)
        }
    }

    @Test fun settlingStartsAfterGestureCompletionRatherThanDispatch() {
        val h = Harness(); h.automaticResult = null
        h.start(AutoScanStep.OPEN_RESEARCH); h.advance(800)
        assertTrue(h.results.isEmpty())
        h.callbacks.single()(Result.success(Unit))
        h.advance(1249); assertTrue(h.results.isEmpty())
        h.advance(1250); assertTrue(h.results.single().isSuccess)
    }

    @Test fun failedTapCompletesOnceWithoutRetryingOrWaitingForSettle() {
        val h = Harness()
        val problem = IllegalStateException("Gesture rejected")
        h.automaticResult = Result.failure(problem)
        h.start(AutoScanStep.OPEN_MOUNTS)
        assertSame(problem, h.results.single().exceptionOrNull())
        h.callbacks.single()(Result.success(Unit))
        h.advance(20_000)
        assertEquals(1, h.results.size); assertEquals(1, h.taps.size)
    }

    @Test fun synchronousTapFailureDoesNotEscapeOrCauseAnotherTap() {
        val h = Harness(); val problem = IllegalStateException("Gesture failed")
        h.throwFromTap = problem
        h.start(AutoScanStep.OPEN_PETS)
        assertSame(problem, h.results.single().exceptionOrNull())
        h.advance(20_000); assertEquals(1, h.taps.size); assertEquals(1, h.results.size)
    }

    @Test fun duplicateTapCallbacksCannotRescheduleOrChangeTheFirstResult() {
        val h = Harness(); h.start(AutoScanStep.OPEN_SKILLS)
        h.callbacks.single()(Result.success(Unit))
        h.callbacks.single()(Result.failure(IllegalStateException("Duplicate")))
        h.advance(319); assertTrue(h.results.isEmpty())
        h.advance(320)
        assertTrue(h.results.single().isSuccess); assertEquals(1, h.taps.size)
    }

    @Test fun aHungTapHasOneTenSecondDeadlineAndIgnoresItsLateCompletion() {
        val h = Harness(); h.automaticResult = null
        h.start(AutoScanStep.CLOSE_MOUNTS); h.advance(9999)
        assertTrue(h.results.isEmpty())
        h.advance(10_000); assertTrue(h.results.single().isFailure)
        h.callbacks.single()(Result.success(Unit)); h.advance(20_000)
        assertEquals(1, h.results.size); assertEquals(1, h.taps.size)
    }

    @Test fun theGlobalDeadlineAlsoBoundsALateSuccessfulTapAndItsSettle() {
        val h = Harness(); h.automaticResult = null
        h.start(AutoScanStep.CLOSE_RESEARCH); h.advance(9600)
        h.callbacks.single()(Result.success(Unit))
        h.advance(10_000)
        assertTrue(h.results.single().isFailure)
        h.advance(10_500); assertEquals(1, h.results.size); assertEquals(1, h.taps.size)
    }

    @Test fun stopWhileTappingOrSettlingNeverCompletesOrClicksAgain() {
        for (pendingTap in listOf(true, false)) {
            val h = Harness()
            if (pendingTap) h.automaticResult = null
            h.start(AutoScanStep.OPEN_MOUNTS); h.advance(100)
            h.runner.cancel(); h.callbacks.single()(Result.success(Unit)); h.advance(20_000)
            assertTrue(h.results.isEmpty()); assertEquals(1, h.taps.size)
        }
    }

    @Test fun callbacksAndTimersFromAnOldGenerationCannotFinishTheNewStep() {
        val h = Harness(); h.automaticResult = null
        h.start(AutoScanStep.OPEN_MOUNTS)
        val old = h.callbacks.single()
        h.advance(100)
        h.automaticResult = Result.success(Unit)
        h.start(AutoScanStep.OPEN_PETS)
        old(Result.success(Unit)); old(Result.failure(IllegalStateException("Old")))
        h.advance(419); assertTrue(h.results.isEmpty())
        h.advance(420); assertTrue(h.results.single().isSuccess)
        h.advance(20_000)
        assertEquals(listOf(AutoScanStep.OPEN_MOUNTS, AutoScanStep.OPEN_PETS), h.taps)
        assertEquals(1, h.results.size)
    }

    @Test fun nonNavigationStepsCannotReachTheGesturePort() {
        val h = Harness()
        for (step in AutoScanStep.entries.filter { !it.isNavigation }) {
            assertThrows(IllegalArgumentException::class.java) { h.start(step) }
            assertThrows(IllegalArgumentException::class.java) { SavedNavigationRunner.settleMillis(step) }
        }
        assertTrue(h.taps.isEmpty()); assertTrue(h.results.isEmpty())
    }
}
