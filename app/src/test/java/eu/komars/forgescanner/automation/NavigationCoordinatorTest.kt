package eu.komars.forgescanner.automation

import eu.komars.forgescanner.model.GameScreenKind
import eu.komars.forgescanner.model.GameScreenKind.*
import org.junit.Assert.*
import org.junit.Test

class NavigationCoordinatorTest {
    private class Harness {
        data class Job(val at: Long, val id: Int, val action: () -> Unit, var cancelled: Boolean = false)
        var now = 0L; var serial = 0; var screen = HOME
        val jobs = mutableListOf<Job>()
        val taps = mutableListOf<Pair<AutoScanStep, Boolean>>()
        var tapAction: (AutoScanStep, Boolean) -> Unit = { step, _ -> screen = when (step) {
            AutoScanStep.OPEN_MOUNTS -> MOUNTS
            AutoScanStep.CLOSE_MOUNTS, AutoScanStep.CLOSE_RESEARCH -> HOME
            AutoScanStep.OPEN_RESEARCH, AutoScanStep.OPEN_SKILLS -> SKILLS
            AutoScanStep.OPEN_PETS -> PETS
            else -> UNKNOWN
        } }
        var holdInspections = false
        var holdTaps = false
        var duplicateInspections = false
        val held = mutableListOf<(Result<GameScreenKind>) -> Unit>()
        val heldTaps = mutableListOf<(Result<Unit>) -> Unit>()
        var result: Result<Unit>? = null
        var completions = 0
        val scheduler = NavigationCoordinator.Scheduler { delay, action ->
            val job = Job(now + delay, serial++, action); jobs += job; { job.cancelled = true }
        }
        val coordinator = NavigationCoordinator(object : NavigationCoordinator.Port {
            override fun inspect(complete: (Result<GameScreenKind>) -> Unit) {
                if (holdInspections) held += complete else {
                    complete(Result.success(screen))
                    if (duplicateInspections) complete(Result.success(screen))
                }
            }
            override fun tap(step: AutoScanStep, recovering: Boolean, complete: (Result<Unit>) -> Unit) {
                taps += step to recovering
                if (holdTaps) heldTaps += complete else {
                    tapAction(step, recovering); complete(Result.success(Unit))
                }
            }
            override fun cancel() { }
        }, scheduler, { now })
        fun start(step: AutoScanStep) = coordinator.start(step) { result = it; completions++ }
        fun advance(target: Long) {
            while (true) {
                val job = jobs.filter { !it.cancelled && it.at <= target }.minWithOrNull(compareBy<Job> { it.at }.thenBy { it.id }) ?: break
                job.cancelled = true; now = job.at; job.action()
            }
            now = target
        }
    }

    @Test fun alreadyReachedDestinationsAreIdempotentAndNeverTapped() {
        val cases = listOf(AutoScanStep.OPEN_MOUNTS to MOUNTS,
            AutoScanStep.CLOSE_MOUNTS to HOME, AutoScanStep.CLOSE_RESEARCH to HOME,
            AutoScanStep.OPEN_RESEARCH to SKILLS, AutoScanStep.OPEN_RESEARCH to PETS,
            AutoScanStep.OPEN_RESEARCH to TECHNOLOGY, AutoScanStep.OPEN_SKILLS to SKILLS,
            AutoScanStep.OPEN_PETS to PETS)
        for ((step, screen) in cases) {
            val h = Harness(); h.screen = screen; h.start(step); h.advance(500)
            assertTrue(step.name, h.result!!.isSuccess)
            assertTrue(step.name, h.taps.isEmpty())
            assertEquals(1, h.completions)
        }
    }

    @Test fun alreadyHomeCloseCannotSkipItsSettleByTakingAnEmptyRecoveryRoute() {
        val h = Harness(); h.screen = HOME
        h.start(AutoScanStep.CLOSE_MOUNTS); h.advance(120)
        assertNull(h.result); assertTrue(h.taps.isEmpty())
        h.advance(449)
        assertNull(h.result); assertTrue(h.taps.isEmpty())
        h.advance(500)
        assertTrue(h.result!!.isSuccess); assertTrue(h.taps.isEmpty()); assertEquals(1, h.completions)
    }
    @Test fun preflightNeedsTwoIndependentConsistentObservationsBeforeATap() {
        val h = Harness(); h.duplicateInspections = true
        h.start(AutoScanStep.OPEN_RESEARCH); h.advance(119)
        assertTrue(h.taps.isEmpty())
        h.advance(120)
        assertEquals(listOf(AutoScanStep.OPEN_RESEARCH to false), h.taps)
        assertNull(h.result)
    }

    @Test fun unknownInitialScreenWaitsForAKnownSourceWithoutTappingItBlindly() {
        val h = Harness(); h.screen = UNKNOWN
        h.scheduler.after(800) { h.screen = HOME }
        h.start(AutoScanStep.OPEN_RESEARCH); h.advance(850)
        assertTrue(h.taps.isEmpty())
        h.advance(1500)
        assertTrue(h.result!!.isSuccess); assertEquals(1, h.taps.size)
    }

    @Test fun slowOpeningIsObservedBeforeAnotherActionCanRun() {
        val h = Harness()
        h.tapAction = { _, _ -> h.scheduler.after(700) { h.screen = SKILLS } }
        h.start(AutoScanStep.OPEN_RESEARCH); h.advance(800)
        assertNull(h.result); assertEquals(1, h.taps.size)
        h.advance(1200); assertTrue(h.result!!.isSuccess); assertEquals(1, h.completions)
    }

    @Test fun lateTargetCanSettleDuringGraceWithoutARecoveryTap() {
        val h = Harness(); h.screen = MOUNTS
        h.tapAction = { _, _ -> h.scheduler.after(3220) { h.screen = HOME } }
        h.start(AutoScanStep.CLOSE_MOUNTS); h.advance(3200)
        assertNull(h.result); assertEquals(1, h.taps.size)
        h.advance(3700)
        assertTrue(h.result!!.isSuccess); assertEquals(1, h.taps.size)
        assertEquals(1, h.completions)
    }

    @Test fun missedTapRetriesOnceOnlyAfterTheOriginalHomeScreenIsStable() {
        val h = Harness()
        h.tapAction = { _, recovering -> if (recovering) h.screen = SKILLS }
        h.start(AutoScanStep.OPEN_RESEARCH); h.advance(3800)
        assertEquals(1, h.taps.size)
        h.advance(5000); assertTrue(h.result!!.isSuccess)
        assertEquals(listOf(AutoScanStep.OPEN_RESEARCH to false, AutoScanStep.OPEN_RESEARCH to true), h.taps)
    }

    @Test fun wrongKnownWindowIsRestoredBeforeAnyMismatchedSavedCoordinateIsTapped() {
        val h = Harness(); h.screen = MOUNTS
        h.start(AutoScanStep.OPEN_PETS); h.advance(4000)
        assertTrue(h.result!!.isSuccess)
        assertEquals(listOf(AutoScanStep.CLOSE_MOUNTS to true,
            AutoScanStep.OPEN_RESEARCH to true, AutoScanStep.OPEN_PETS to true), h.taps)
    }

    @Test fun unknownScreensNeverAuthorizeAnyNavigationOrRecoveryTap() {
        val h = Harness(); h.screen = UNKNOWN; h.tapAction = { _, _ -> }
        h.start(AutoScanStep.OPEN_RESEARCH); h.advance(9000)
        assertTrue(h.result!!.isFailure); assertTrue(h.taps.isEmpty()); assertEquals(1, h.completions)
    }

    @Test fun aFailedRecoveryDoesNotLoop() {
        val h = Harness(); h.tapAction = { _, _ -> }
        h.start(AutoScanStep.OPEN_RESEARCH); h.advance(9000)
        assertTrue(h.result!!.isFailure); assertEquals(2, h.taps.size); assertEquals(1, h.completions)
    }

    @Test fun stoppedOrTimedOutCapturesCannotAdvanceTheRouteLater() {
        val h = Harness(); h.holdInspections = true
        h.start(AutoScanStep.OPEN_RESEARCH); h.advance(200)
        h.coordinator.cancel(); h.held.forEach { it(Result.success(HOME)) }; h.advance(9000)
        assertNull(h.result); assertTrue(h.taps.isEmpty())
        val timed = Harness(); timed.holdInspections = true
        timed.start(AutoScanStep.OPEN_RESEARCH); timed.advance(9000)
        timed.held.forEach { it(Result.success(HOME)) }
        assertTrue(timed.result!!.isFailure); assertEquals(1, timed.completions); assertTrue(timed.taps.isEmpty())
    }

    @Test fun theGlobalDeadlineIncludesATapThatNeverCompletes() {
        val h = Harness(); h.holdTaps = true
        h.start(AutoScanStep.OPEN_RESEARCH); h.advance(8499)
        assertNull(h.result); assertEquals(1, h.taps.size)
        h.advance(8500)
        assertTrue(h.result!!.isFailure); assertEquals(1, h.completions)
        h.heldTaps.forEach { it(Result.success(Unit)) }; h.advance(12000)
        assertEquals(1, h.completions); assertEquals(1, h.taps.size)
    }

    @Test fun stopDuringRecoverySuppressesEveryQueuedTap() {
        val h = Harness(); h.tapAction = { _, _ -> }
        h.start(AutoScanStep.OPEN_RESEARCH); h.advance(3900)
        assertEquals(1, h.taps.size)
        h.coordinator.cancel(); h.advance(12000)
        assertEquals(1, h.taps.size); assertNull(h.result)
    }

    @Test fun anOldPhaseCannotUseItsLateCaptureToClickInANewRun() {
        val h = Harness(); h.holdInspections = true
        h.start(AutoScanStep.OPEN_RESEARCH); h.advance(0)
        val stale = h.held.single()
        h.coordinator.cancel()
        h.screen = PETS; h.holdInspections = false
        h.start(AutoScanStep.OPEN_PETS)
        stale(Result.success(HOME)); stale(Result.success(HOME))
        h.advance(1000)
        assertTrue(h.result!!.isSuccess); assertTrue(h.taps.isEmpty()); assertEquals(1, h.completions)
    }
}
