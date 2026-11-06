package eu.komars.forgescanner.automation

import org.junit.Assert.*
import org.junit.Test

class GestureSafetyTest {
    @Test fun onlyTheCurrentSessionCanInjectWithinItsTimeAndBounds() {
        val policy = GestureSafety()
        val first = policy.arm(1080, 2400, 100)
        policy.validate(first, 110, 100f, 100f)
        val second = policy.arm(1080, 2400, 120)
        assertRejected { policy.validate(first, 130, 100f, 100f) }
        policy.validate(second, 130, 100f, 100f)
        assertRejected { policy.validate(second.copy(), 130, 100f, 100f) }
        assertRejected { policy.validate(second, 119, 100f, 100f) }
        assertRejected { policy.validate(second, 300_121, 100f, 100f) }
        assertRejected { policy.validate(second, 130, Float.NaN, 100f) }
        assertRejected { policy.validate(second, 130, -1f, 100f) }
        assertRejected { policy.validate(second, 130, 1080f, 100f) }
        policy.validate(second, 300_120)
        policy.revoke()
        assertRejected { policy.validate(second, 130, 100f, 100f) }
    }

    @Test fun scrollsAreBoundedAndCannotBecomeRapidOrCrossScreenSwipes() {
        val policy = GestureSafety()
        val session = policy.arm(1080, 2400, 0)
        policy.drag(session, 500f, 1500f, 500f, 1450f, 180, 1)
        assertRejected { policy.drag(session, 500f, 1500f, 500f, 1450f, 10, 1) }
        assertRejected { policy.drag(session, 500f, 1500f, 500f, 1450f, 5000, 1) }
        assertRejected { policy.drag(session, 500f, 1500f, 500f, 100f, 180, 1) }
        assertRejected { policy.drag(session, 500f, 1500f, 900f, 1450f, 180, 1) }
    }

    @Test fun anUnboundedCallerCannotKeepAGestureRunAliveForever() {
        val policy = GestureSafety()
        val session = policy.arm(1080, 2400, 0)
        repeat(2000) { policy.actionStarted() }
        assertRejected { policy.validate(session, 1, 100f, 100f) }
    }

    @Test fun malformedScreenDimensionsAreNeverArmed() {
        val policy = GestureSafety()
        assertRejected { policy.arm(0, 2400, 0) }
        assertRejected { policy.arm(1080, 20000, 0) }
        assertRejected { policy.arm(1080, 2400, -1) }
    }

    @Test fun longerReleaseStillHonoursStopSessionExpiryAndBoundedHoldTime() {
        val policy=GestureSafety();val session=policy.arm(1080,2340,100)
        policy.finishHold(session,180,101);policy.finishHold(session,360,102)
        assertRejected {policy.finishHold(session,5000,103)}
        assertRejected {policy.finishHold(session,360,300101)}
        policy.revoke();assertRejected {policy.finishHold(session,360,104)}
    }

    @Test fun activeClanGesturesCanContinueForTwentyFourMinutesButIdleStillExpires() {
        val policy=GestureSafety();val session=policy.arm(1080,2340,0)
        var now=0L
        repeat(6){now+=240_000;policy.validate(session,now,100f,100f);policy.actionStarted()}
        assertTrue(now>300_000)
        assertRejected {policy.validate(session,now+300_001,100f,100f)}
    }
    @Test fun merelyCheckingOrRejectingCoordinatesCannotRenewAnIdleGrant() {
        val policy=GestureSafety();val session=policy.arm(1080,2340,0)
        policy.validate(session,200_000,100f,100f)
        assertRejected {policy.validate(session,290_000,-1f,100f)}
        assertRejected {policy.validate(session,300_001,100f,100f)}
    }

    private fun assertRejected(action: () -> Unit) {
        try { action(); fail("The unsafe gesture should have been rejected") }
        catch (_: IllegalArgumentException) { }
    }
}
