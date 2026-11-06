package eu.komars.forgescanner.automation

import eu.komars.forgescanner.model.ScanTarget
import org.junit.Assert.*
import org.junit.Test

class AutoScanRunnerTest {
    private class Clock : AutoScanRunner.Scheduler {
        data class Alarm(val due: Long, val action: () -> Unit, var cancelled: Boolean = false)
        private var time = 0L
        private val alarms = mutableListOf<Alarm>()
        override fun after(delayMillis: Long, action: () -> Unit): () -> Unit {
            val alarm = Alarm(time + delayMillis, action); alarms += alarm
            return { alarm.cancelled = true }
        }
        fun advance(milliseconds: Long) {
            time += milliseconds
            while (true) {
                val due = alarms.firstOrNull { !it.cancelled && it.due <= time } ?: break
                due.cancelled = true; due.action()
            }
        }
    }
    private class Port : AutoScanRunner.Port {
        val calls = mutableListOf<AutoScanStep>()
        val callbacks = mutableListOf<(Result<AutoReading?>) -> Unit>()
        var cancellations = 0
        override fun perform(step: AutoScanStep, complete: (Result<AutoReading?>) -> Unit) { calls += step; callbacks += complete }
        override fun cancel() { cancellations++ }
        fun finish(value: AutoReading? = if (calls.last().target != null) AutoReading(42) else null) = callbacks.last()(Result.success(value))
    }

    @Test fun completeFixedRoutePublishesAllSevenReadingsOnlyAfterReturningHome() {
        val clock = Clock(); val port = Port(); val complete = mutableListOf<Map<ScanTarget, AutoReading>>()
        val runner = AutoScanRunner(port, clock, {}, complete::add)
        runner.start()
        repeat(AutoScanStep.entries.size - 1) { port.finish(); assertTrue(complete.isEmpty()) }
        assertEquals(AutoScanStep.CLOSE_RESEARCH, port.calls.last())
        port.finish()
        assertEquals(AutoScanStep.entries.toList(), port.calls)
        assertEquals(ScanTarget.entries.toSet(), complete.single().keys)
        assertEquals(AutoScanRunner.Status.COMPLETE, runner.state.status)
        clock.advance(400_000)
        assertEquals(1, complete.size)
    }

    @Test fun ambiguousCounterFailsWithoutContinuingOrPublishingPartialValues() {
        val port = Port(); var complete = false
        val runner = AutoScanRunner(port, Clock(), {}, { complete = true })
        runner.start(); port.finish(); port.finish()
        assertEquals(AutoScanStep.MOUNT_KEYS, port.calls.last())
        port.callbacks.last()(Result.failure(IllegalStateException("Dwie różne liczby")))
        assertEquals(AutoScanRunner.Status.FAILED, runner.state.status)
        assertEquals(3, port.calls.size)
        assertFalse(complete)
    }

    @Test fun timeoutCancellationAndLateDuplicateCallbacksNeverAdvanceTheRoute() {
        val clock = Clock(); val port = Port(); var complete = false
        val runner = AutoScanRunner(port, clock, {}, { complete = true })
        runner.start()
        val late = port.callbacks.single()
        clock.advance(AutoScanStep.HAMMERS.timeoutMillis + 1)
        late(Result.success(AutoReading(20)))
        assertEquals(1, port.calls.size)
        assertEquals(AutoScanRunner.Status.FAILED, runner.state.status)
        runner.start()
        val current = port.callbacks.last()
        late(Result.success(AutoReading(20)))
        assertEquals(2, port.calls.size)
        current(Result.success(AutoReading(10)))
        current(Result.success(AutoReading(10)))
        assertEquals(3, port.calls.size)
        runner.cancel()
        port.callbacks.last()(Result.success(null))
        assertEquals(3, port.calls.size)
        assertEquals(AutoScanRunner.Status.CANCELLED, runner.state.status)
        assertFalse(complete)
    }

    @Test fun malformedCostAndUnexpectedNavigationResultStopBeforeAnyFurtherTap() {
        val port = Port(); val runner = AutoScanRunner(port, Clock(), {}, {})
        runner.start(); port.finish()
        port.finish(AutoReading(1)) // Navigation cannot supply a resource result.
        assertEquals(AutoScanRunner.Status.FAILED, runner.state.status)
        runner.start(); port.finish(); port.finish(); port.finish()
        assertEquals(AutoScanStep.MOUNT_COST, port.calls.last())
        port.finish(AutoReading(0))
        assertEquals(AutoScanRunner.Status.FAILED, runner.state.status)
    }

    @Test fun explicitManualListFinishPreservesApproximationForFinalReview() {
        val port = Port(); var values: Map<ScanTarget, AutoReading>? = null
        val runner = AutoScanRunner(port, Clock(), {}, { values = it })
        runner.start()
        for (step in AutoScanStep.entries) {
            port.finish(step.target?.let { AutoReading(42, approximate = step.isInventory) })
        }
        assertTrue(values?.get(ScanTarget.PETS)?.approximate == true)
        assertTrue(values?.get(ScanTarget.MOUNTS)?.approximate == true)
    }
    @Test fun progressingResourceStepsCanCompleteAfterMoreThanFiveMinutesTotal() {
        val clock=Clock();val port=Port();var completed=0;var elapsed=0L
        val runner=AutoScanRunner(port,clock,{},{completed++});runner.start()
        for(step in AutoScanStep.entries){val delay=step.timeoutMillis*3/4;clock.advance(delay);elapsed+=delay;assertEquals(AutoScanRunner.Status.RUNNING,runner.state.status);port.finish()}
        assertTrue(elapsed>300_000);assertEquals(1,completed);assertEquals(AutoScanRunner.Status.COMPLETE,runner.state.status)
    }
}
