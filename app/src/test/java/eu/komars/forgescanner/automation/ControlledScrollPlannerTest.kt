package eu.komars.forgescanner.automation

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.ceil

class ControlledScrollPlannerTest {
    @Test fun cachedPollsNeverAuthorizeAnAdditionalPass() {
        val planner = ControlledScrollPlanner(100.0)
        assertEquals(ControlledScrollPlanner.Kind.DRAG, planner.next(1_000_000_000L, null).kind)
        repeat(20) { assertEquals(ControlledScrollPlanner.Kind.WAIT, planner.next(1_000_000_000L, 10.0).kind) }
        assertEquals(ControlledScrollPlanner.Kind.DRAG,
            planner.next(1_220_000_000L, 45.0, completedGestureId = 1).kind)
    }

    @Test fun cachedImagesNeverProveABlockedBottomEvenAfterCompletedPasses() {
        val planner = ControlledScrollPlanner(100.0)
        val timestamp = 1_000_000_000L
        planner.next(timestamp, null)
        repeat(10) { index ->
            assertEquals(ControlledScrollPlanner.Kind.WAIT, planner.next(timestamp, 0.0, completedGestureId = index + 1L).kind)
        }
    }

    @Test fun freshNativeImagesConfirmThreeBlockedPassesAndUseShorterTailProbes() {
        val planner = ControlledScrollPlanner(100.0)
        val timestamp = 1_000_000_000L
        val first = planner.next(timestamp, null)
        assertEquals(715.0, first.distancePx, .001)
        val tail = planner.next(timestamp + 200_000_000, 0.0, completedGestureId = 1)
        assertEquals(ControlledScrollPlanner.Kind.DRAG, tail.kind)
        assertEquals(115.0, tail.distancePx, .001)
        assertTrue(tail.durationMillis < first.durationMillis)
        assertEquals(ControlledScrollPlanner.Kind.DRAG, planner.next(timestamp + 400_000_000, 0.0, completedGestureId = 2).kind)
        assertEquals(ControlledScrollPlanner.Kind.CHECK_END, planner.next(timestamp + 600_000_000, 0.0, completedGestureId = 3).kind)
    }

    @Test fun ambiguousMatchingAndLongStaticHoldsDoNotRejectTheSharedRecordersScan() {
        val planner = ControlledScrollPlanner(100.0)
        planner.next(1_000_000_000L, null)
        repeat(5) { index ->
            assertEquals(ControlledScrollPlanner.Kind.DRAG,
                planner.next(10_000_000_000L + index * 3_000_000_000L, null,
                    completedGestureId = index + 1L).kind)
        }
    }

    @Test fun aKnownMovingPassClearsTheBlockedEndProbeAndRestoresFullTravel() {
        val planner = ControlledScrollPlanner(100.0)
        planner.next(1_000_000_000L, null)
        planner.next(1_220_000_000L, 0.0, completedGestureId = 1)
        planner.next(1_440_000_000L, 0.0, completedGestureId = 2)
        val moving = planner.next(1_660_000_000L, 45.0, completedGestureId = 3)
        assertEquals(ControlledScrollPlanner.Kind.DRAG, moving.kind)
        assertEquals(715.0, moving.distancePx, .001)
        assertEquals(ControlledScrollPlanner.Kind.DRAG,
            planner.next(1_880_000_000L, 0.0, completedGestureId = 4).kind)
    }

    @Test fun unknownCaptureUsesALongPassAndConservativeThirtyFpsDuration() {
        val planner = ControlledScrollPlanner(200.0)
        val first = planner.next(1_000_000_000L, null)
        assertEquals(1430.0, first.distancePx, .001)
        assertEquals(852L, first.durationMillis)
        val next = planner.next(2_000_000_000L, 90.0, completedGestureId = 1)
        assertEquals(first.distancePx, next.distancePx, .001)
        assertEquals(first.durationMillis, next.durationMillis)
    }

    @Test fun onlyMotionFramesCanAccelerateAndCachedPollsCannotFakeEvidence() {
        val planner = ControlledScrollPlanner(200.0)
        var native = 1_000_000_000L
        repeat(20) { planner.observeSourceFrame(native); native += 16_666_667L }
        assertEquals(852L, planner.next(native, null).durationMillis)
        planner.beginMotion()
        repeat(6) {
            planner.observeSourceFrame(native)
            repeat(10) { planner.observeSourceFrame(native) }
            native += 16_666_667L
        }
        planner.endMotion()
        assertEquals(852L, planner.next(native, null, completedGestureId = 1).durationMillis)
        planner.beginMotion()
        planner.observeSourceFrame(native)
        native += 16_666_667L
        planner.observeSourceFrame(native)
        planner.endMotion()
        assertEquals(600L, planner.next(native + 1, null, completedGestureId = 2).durationMillis)
    }

    @Test fun releasesAndIdleGapsNeverPoisonMovingCadenceOrShrinkThePass() {
        val planner = ControlledScrollPlanner(200.0, 837.0, 750.0)
        var native = 1_000_000_000L
        repeat(8) { pass ->
            planner.beginMotion()
            repeat(12) { planner.observeSourceFrame(native); native += 16_666_667L }
            planner.endMotion()
            // Source frames may arrive during release, or stop rendering for a second.
            native += 180_000_000L
            planner.observeSourceFrame(native)
            native += 1_000_000_000L
            planner.observeSourceFrame(native)
            val decision = planner.next(native + 1, if (pass == 0) null else 90.0, completedGestureId = pass.toLong())
            assertEquals(ControlledScrollPlanner.Kind.DRAG, decision.kind)
            assertEquals(1430.0, decision.distancePx, .001)
            assertEquals(600L, decision.durationMillis)
            native += 16_666_667L
        }
    }

    @Test fun slowCaptureLengthensTheWholePassInsteadOfMakingMillimetreStrokes() {
        for (interval in listOf(16_666_667L, 33_333_334L, 66_666_667L, 125_000_000L)) {
            val planner = ControlledScrollPlanner(200.0, 837.0, 750.0)
            val source = motionSamples(planner, List(24) { interval })
            val drag = planner.next(source + 1, null)
            assertEquals(ControlledScrollPlanner.Kind.DRAG, drag.kind)
            assertEquals(1430.0, drag.distancePx, .001)
            assertTrue(drag.durationMillis in 600..4000)
            val rowsPerSourceFrame = drag.distancePx / 200 * interval / (drag.durationMillis * 1_000_000.0)
            assertTrue("Capture interval $interval: $rowsPerSourceFrame", rowsPerSourceFrame <= .280001)
        }
    }

    @Test fun oneDelayedMotionFrameIsRobustButRepeatedGapsLengthenThePass() {
        val isolated = ControlledScrollPlanner(200.0)
        val once = motionSamples(isolated, List(12) { 16_666_667L } + 500_000_000L + List(12) { 16_666_667L })
        assertEquals(600L, isolated.next(once + 1, null).durationMillis)
        val repeated = ControlledScrollPlanner(200.0)
        val twice = motionSamples(repeated, List(6) { 16_666_667L } + listOf(125_000_000L, 125_000_000L))
        val slow = repeated.next(twice + 1, null)
        assertEquals(ControlledScrollPlanner.Kind.DRAG, slow.kind)
        assertEquals(1430.0, slow.distancePx, .001)
        assertEquals(3192L, slow.durationMillis)
    }

    @Test fun repeatedUnusableMotionCaptureFailsInsteadOfShrinkingOrWaitingForever() {
        val planner = ControlledScrollPlanner(200.0)
        val source = motionSamples(planner, listOf(250_000_000L, 250_000_000L))
        assertEquals(ControlledScrollPlanner.Kind.FAILED, planner.next(source + 1, null).kind)
    }

    @Test fun physicalStrokeLimitRetainsViewportOverlapAndScreenBounds() {
        for ((viewport, maximum) in listOf(837.0 to 700.0, 500.0 to 650.0, 900.0 to 400.0)) {
            val planner = ControlledScrollPlanner(200.0, viewport, maximum)
            val pass = planner.next(1_000_000_000L, null)
            assertTrue(planner.maximumSegmentDistancePx <= maximum)
            assertTrue(viewport - planner.maximumSegmentDistancePx >= 200 * .45 - .001)
            assertTrue(pass.distancePx > planner.maximumSegmentDistancePx)
            assertTrue(pass.distancePx / 200 % 1 in .149.. .851)
        }
    }

    @Test fun longListBudgetIncludesPhysicalSplitsWithoutChangingLogicalTravel() {
        // Transport estimate only: images still count every item. Include two
        // physical releases/initial downs per normal pass at this phone's ROI.
        for (items in listOf(700, 1200)) for (interval in listOf(16_666_667L, 33_333_334L)) {
            val planner = ControlledScrollPlanner(200.0, 837.0, 750.0)
            var source = motionSamples(planner, List(24) { interval }) + 1
            val scrollRows = items / 5.0 - 837.0 / 200
            var exposedRows = 0.0
            var passes = 0L
            var transportMillis = 0L
            while (exposedRows < scrollRows) {
                val pass = planner.next(source, if (passes == 0L) null else 90.0, completedGestureId = passes)
                assertEquals(ControlledScrollPlanner.Kind.DRAG, pass.kind)
                val physicalStrokes = maxOf(ceil(pass.distancePx / planner.maximumSegmentDistancePx).toInt(),
                    ceil(pass.durationMillis / 1000.0).toInt())
                exposedRows += pass.distancePx / 200
                transportMillis += pass.durationMillis + physicalStrokes * (180 + 64) + 8
                passes++
                source += 2_000_000_000L
            }
            assertTrue("$items items: $transportMillis ms", transportMillis < if (items == 700) 35_000L else 60_000L)
            assertTrue(passes < 50)
        }
    }

    @Test fun freshnessGateWaitsForRecoveryThenExplicitlyRejectsAFrozenCapture() {
        val gate = InventoryCaptureFreshnessGate()
        val first = 1_000_000_000L
        assertEquals(InventoryCaptureFreshnessGate.Status.FRESH, gate.observe(first, 0))
        assertEquals(InventoryCaptureFreshnessGate.Status.WAIT, gate.observe(first, 200))
        assertEquals(InventoryCaptureFreshnessGate.Status.WAIT, gate.observe(first, 949))
        assertEquals(InventoryCaptureFreshnessGate.Status.STALLED, gate.observe(first, 950))
        assertEquals(InventoryCaptureFreshnessGate.Status.FRESH, gate.observe(first + 16_000_000L, 960))
        assertEquals(InventoryCaptureFreshnessGate.Status.WAIT, gate.observe(first + 16_000_000L, 1000))
        assertEquals(InventoryCaptureFreshnessGate.Status.WAIT, gate.observe(first + 16_000_000L, 1749))
        assertEquals(InventoryCaptureFreshnessGate.Status.STALLED, gate.observe(first + 16_000_000L, 1750))
    }

    @Test fun backwardsSourceOrWallClockCannotCreateFreshCaptureEvidence() {
        val source = InventoryCaptureFreshnessGate()
        source.observe(100, 1000)
        assertEquals(InventoryCaptureFreshnessGate.Status.STALLED, source.observe(99, 1001))
        val clock = InventoryCaptureFreshnessGate()
        clock.observe(100, 1000)
        assertEquals(InventoryCaptureFreshnessGate.Status.STALLED, clock.observe(101, 999))
    }

    @Test fun reversalInvalidMovementAndOldPassCallbacksRemainRejected() {
        for (shift in listOf(-10.0, 90.0, Double.NaN)) {
            val planner = ControlledScrollPlanner(100.0)
            planner.next(1_000_000_000L, null)
            assertEquals(ControlledScrollPlanner.Kind.FAILED,
                planner.next(1_220_000_000L, shift, completedGestureId = 1).kind)
        }
        val planner = ControlledScrollPlanner(100.0)
        planner.next(1_000_000_000L, null)
        planner.next(1_220_000_000L, 45.0, completedGestureId = 2)
        assertEquals(ControlledScrollPlanner.Kind.FAILED,
            planner.next(1_440_000_000L, 45.0, completedGestureId = 1).kind)
    }

    @Test fun anUnfinishedPassCannotQueueAnotherAndVisibleEndStopsImmediately() {
        val planner = ControlledScrollPlanner(100.0)
        planner.next(1_000_000_000L, null)
        assertEquals(ControlledScrollPlanner.Kind.WAIT,
            planner.next(1_220_000_000L, 0.0, gestureCompleted = false).kind)
        assertEquals(ControlledScrollPlanner.Kind.END_VISIBLE,
            planner.next(1_440_000_000L, 0.0, endVisible = true).kind)
    }

    @Test fun longerStrokesReduceTransportBudgetWithoutCountingAssumedRows() {
        for (interval in listOf(16_666_667L, 33_333_334L)) {
            val planner = ControlledScrollPlanner(200.0, 837.0, 2340.0 * .32)
            val time = motionSamples(planner, List(24) { interval })
            val pass = planner.next(time + 1, null)
            val strokes = ScrollPassSegments.split(pass.distancePx, pass.durationMillis,
                planner.maximumSegmentDistancePx)
            assertEquals(2, strokes.size)
            assertTrue(strokes.all { it.distancePx >= 700 && it.distancePx <= 748.8 })
            val remainingRows = 101.0 - 837.0 / 200
            val oldPassMs = maxOf(550L, ceil(5.15 * interval / (.25 * 1_000_000.0)).toLong()) + 2 * 180 + 8
            val oldBudget = ceil(remainingRows / 5.15).toLong() * oldPassMs
            val newBudget = ceil(remainingRows / (pass.distancePx / 200)).toLong() *
                (strokes.sumOf { it.durationMillis } + strokes.size * 180 + 8)
            assertTrue("New transport budget $newBudget should improve on $oldBudget", newBudget < oldBudget * .9)
        }
    }

    @Test fun shorterViewportsDoNotRequireAnExtraStopPerPass() {
        for ((viewport, maximum) in listOf(837.0 to 700.0, 500.0 to 650.0, 900.0 to 400.0)) {
            val planner = ControlledScrollPlanner(200.0, viewport, maximum)
            val time = motionSamples(planner, List(24) { 16_666_667L })
            val pass = planner.next(time + 1, null)
            val strokes = ScrollPassSegments.split(pass.distancePx, pass.durationMillis,
                planner.maximumSegmentDistancePx)
            assertEquals(2, strokes.size)
            assertTrue(strokes.all { it.distancePx <= maximum })
        }
    }

    private fun motionSamples(planner: ControlledScrollPlanner, intervals: List<Long>): Long {
        var timestamp = 1_000_000_000L
        planner.beginMotion()
        planner.observeSourceFrame(timestamp)
        for (interval in intervals) { timestamp += interval; planner.observeSourceFrame(timestamp) }
        planner.endMotion()
        return timestamp
    }
}
