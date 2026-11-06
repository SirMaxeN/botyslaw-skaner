package eu.komars.forgescanner.update

import eu.komars.forgescanner.update.PlayUpdateCheckController.Presentation.*
import eu.komars.forgescanner.update.PlayUpdateCheckController.Status.*
import org.junit.Assert.*
import org.junit.Test

class PlayUpdateCheckControllerTest {
    private class Harness {
        var timeout: (() -> Unit)? = null
        val requests = mutableListOf<(PlayUpdateCheckController.Status) -> Unit>()
        val results = mutableListOf<PlayUpdateCheckController.Status>()
        var cancels = 0
        val controller = PlayUpdateCheckController(PlayUpdateCheckController.Port { requests += it },
            PlayUpdateCheckController.Scheduler { delay, action ->
                assertEquals(3000L, delay)
                timeout = action
                val cancel: () -> Unit = { cancels++ }
                cancel
            })
        fun check() = controller.check(results::add)
    }

    @Test fun availableAndVerifiedCurrentRemainDistinct() {
        for (status in listOf(UPDATE_AVAILABLE, UP_TO_DATE, UNAVAILABLE)) {
            val h = Harness(); h.check()
            h.requests.single()(status)
            assertEquals(listOf(status), h.results)
            assertTrue(h.cancels > 0)
        }
    }

    @Test fun unavailableAndTimeoutNeverClaimTheAppIsCurrent() {
        val h = Harness(); h.check()
        h.timeout!!()
        assertEquals(listOf(UNAVAILABLE), h.results)
        h.requests.single()(UP_TO_DATE)
        assertEquals(listOf(UNAVAILABLE), h.results)
        assertEquals(SHOW_RETRY, PlayUpdateCheckController.presentation(UNAVAILABLE, manual = true))
        assertEquals(OPEN_SCANNER, PlayUpdateCheckController.presentation(UNAVAILABLE, manual = false))
    }

    @Test fun cancellationAndRestartIgnoreAllOldCallbacksAndTimers() {
        val h = Harness(); h.check()
        val old = h.requests.single(); val oldTimer = h.timeout!!
        h.controller.cancel()
        old(UPDATE_AVAILABLE); oldTimer()
        assertTrue(h.results.isEmpty())
        h.check()
        old(UPDATE_AVAILABLE); oldTimer()
        assertTrue(h.results.isEmpty())
        h.requests.last()(UP_TO_DATE)
        assertEquals(listOf(UP_TO_DATE), h.results)
    }

    @Test fun theFirstResultWinsEvenWhenPlayDeliversDuplicates() {
        val h = Harness(); h.check()
        val result = h.requests.single(); val timer = h.timeout!!
        result(UPDATE_AVAILABLE); result(UP_TO_DATE); timer()
        assertEquals(listOf(UPDATE_AVAILABLE), h.results)
    }

    @Test fun synchronousApiFailureIsUnavailableRatherThanCurrent() {
        val results = mutableListOf<PlayUpdateCheckController.Status>()
        val controller = PlayUpdateCheckController(PlayUpdateCheckController.Port { throw IllegalStateException("Unavailable") },
            PlayUpdateCheckController.Scheduler { _, _ -> {} })
        controller.check(results::add)
        assertEquals(listOf(UNAVAILABLE), results)
    }

    @Test fun startupOnlyInterruptsTheUserForAnAvailableUpdate() {
        assertEquals(OFFER_UPDATE, PlayUpdateCheckController.presentation(UPDATE_AVAILABLE, manual = false))
        assertEquals(OPEN_SCANNER, PlayUpdateCheckController.presentation(UP_TO_DATE, manual = false))
        assertEquals(OPEN_SCANNER, PlayUpdateCheckController.presentation(UNAVAILABLE, manual = false))
        assertEquals(OFFER_UPDATE, PlayUpdateCheckController.presentation(UPDATE_AVAILABLE, manual = true))
        assertEquals(SHOW_CURRENT, PlayUpdateCheckController.presentation(UP_TO_DATE, manual = true))
        assertEquals(SHOW_RETRY, PlayUpdateCheckController.presentation(UNAVAILABLE, manual = true))
    }
}
