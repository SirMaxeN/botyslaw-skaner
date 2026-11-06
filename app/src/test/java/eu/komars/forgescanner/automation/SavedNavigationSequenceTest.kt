package eu.komars.forgescanner.automation

import eu.komars.forgescanner.model.ScanTarget
import org.junit.Assert.*
import org.junit.Test

/** Exercise the real scan sequencer and saved-navigation runner together, with no UI/game. */
class SavedNavigationSequenceTest {
    private class Clock {
        private data class Alarm(val at: Long, val order: Int, val action: () -> Unit, var cancelled: Boolean = false)
        var now = 0L
            private set
        private var order = 0
        private val alarms = mutableListOf<Alarm>()
        fun after(delay: Long, action: () -> Unit): () -> Unit {
            val alarm = Alarm(now + delay, order++, action); alarms += alarm
            return { alarm.cancelled = true }
        }
        fun advanceTo(target: Long) {
            while (true) {
                val alarm = alarms.filter { !it.cancelled && it.at <= target }
                    .minWithOrNull(compareBy<Alarm> { it.at }.thenBy { it.order }) ?: break
                alarm.cancelled = true; now = alarm.at; alarm.action()
            }
            now = target
        }
    }

    private data class Event(val at: Long, val action: String, val step: AutoScanStep)
    private class Harness {
        val clock = Clock()
        val events = mutableListOf<Event>()
        val states = mutableListOf<AutoScanStep>()
        val taps = mutableListOf<AutoScanStep>()
        val tapCallbacks = mutableMapOf<AutoScanStep, (Result<Unit>) -> Unit>()
        val results = mutableListOf<Map<ScanTarget, AutoReading>>()
        private var published: AutoScanStep? = null
        private val navigation = SavedNavigationRunner(object : SavedNavigationRunner.Port {
            override fun tap(step: AutoScanStep, complete: (Result<Unit>) -> Unit) {
                // This is the point where the platform would dispatch a gesture.
                assertEquals("The new scan state must be published before its tap", step, published)
                events += Event(clock.now, "tap", step)
                taps += step; tapCallbacks[step] = complete
                if (step != AutoScanStep.CLOSE_MOUNTS) complete(Result.success(Unit))
            }
            override fun cancel() { }
        }, SavedNavigationRunner.Scheduler(clock::after))
        val scan = AutoScanRunner(object : AutoScanRunner.Port {
            override fun perform(step: AutoScanStep, complete: (Result<AutoReading?>) -> Unit) {
                assertEquals("State publication must precede every operation", step, published)
                events += Event(clock.now, "perform", step)
                if (step.isNavigation) navigation.start(step) { complete(it.map { null }) }
                else complete(Result.success(AutoReading(42)))
            }
            override fun cancel() = navigation.cancel()
        }, AutoScanRunner.Scheduler(clock::after), { state ->
            if (state.status == AutoScanRunner.Status.RUNNING) {
                val step = requireNotNull(state.step)
                published = step; states += step
                events += Event(clock.now, "publish", step)
            }
        }, results::add)
    }

    @Test fun closeStateIsPublishedBeforeItsTapAndResearchWaitsForGesturePlusSettle() {
        val h = Harness(); h.scan.start(); h.clock.advanceTo(400)
        assertEquals(AutoScanStep.CLOSE_MOUNTS, h.scan.state.step)
        val closePublish = h.events.indexOfFirst { it.action == "publish" && it.step == AutoScanStep.CLOSE_MOUNTS }
        val closeTap = h.events.indexOfFirst { it.action == "tap" && it.step == AutoScanStep.CLOSE_MOUNTS }
        assertTrue(closePublish >= 0 && closePublish < closeTap)
        // The close gesture is still pending, regardless of how long the label is visible.
        h.clock.advanceTo(900)
        assertFalse(h.states.contains(AutoScanStep.OPEN_RESEARCH))
        h.tapCallbacks.getValue(AutoScanStep.CLOSE_MOUNTS)(Result.success(Unit))
        h.clock.advanceTo(1499)
        assertEquals(AutoScanStep.CLOSE_MOUNTS, h.scan.state.step)
        assertFalse(h.taps.contains(AutoScanStep.OPEN_RESEARCH))
        h.clock.advanceTo(1500)
        assertEquals(AutoScanStep.OPEN_RESEARCH, h.scan.state.step)
        assertEquals(1500L, h.events.single { it.action == "tap" && it.step == AutoScanStep.OPEN_RESEARCH }.at)
        val researchPublish = h.events.indexOfFirst { it.action == "publish" && it.step == AutoScanStep.OPEN_RESEARCH }
        val researchTap = h.events.indexOfFirst { it.action == "tap" && it.step == AutoScanStep.OPEN_RESEARCH }
        assertTrue(researchPublish < researchTap)
    }

    @Test fun duplicateCloseCallbacksCannotDoubleAdvanceTheIntegratedRoute() {
        val h = Harness(); h.scan.start(); h.clock.advanceTo(400)
        val close = h.tapCallbacks.getValue(AutoScanStep.CLOSE_MOUNTS)
        close(Result.success(Unit)); close(Result.success(Unit))
        h.clock.advanceTo(999)
        assertEquals(AutoScanStep.CLOSE_MOUNTS, h.scan.state.step)
        h.clock.advanceTo(1000)
        close(Result.success(Unit)); close(Result.failure(IllegalStateException("Late duplicate")))
        h.clock.advanceTo(10_000)
        assertEquals(AutoScanStep.entries.toList(), h.states)
        assertEquals(AutoScanStep.entries.filter { it.isNavigation }, h.taps)
        assertEquals(AutoScanRunner.Status.COMPLETE, h.scan.state.status)
        assertEquals(1, h.results.size)
        assertEquals(ScanTarget.entries.toSet(), h.results.single().keys)
    }

    @Test fun stopBetweenCloseCompletionAndSettleNeverPublishesOrTapsTheNextStep() {
        val h = Harness(); h.scan.start(); h.clock.advanceTo(400)
        val close = h.tapCallbacks.getValue(AutoScanStep.CLOSE_MOUNTS)
        close(Result.success(Unit))
        h.clock.advanceTo(700); h.scan.cancel()
        close(Result.success(Unit)); h.clock.advanceTo(20_000)
        assertFalse(h.states.contains(AutoScanStep.OPEN_RESEARCH))
        assertFalse(h.taps.contains(AutoScanStep.OPEN_RESEARCH))
        assertEquals(AutoScanRunner.Status.CANCELLED, h.scan.state.status)
        assertTrue(h.results.isEmpty())
    }
}
