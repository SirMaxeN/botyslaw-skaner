package eu.komars.forgescanner.automation

import org.junit.Assert.assertEquals
import org.junit.Test

class ForegroundWindowClassifierTest {
    private val own = "com.sirmaxen.BotyslawSkaner.autotest"
    private val game = "com.hariwn.legendofcivilizations"
    private fun classify(owner: String?, type: String? = null) = ForegroundWindowClassifier.classify(owner, type, own, game)

    @Test fun missingOwnerDoesNotRevokeTheGame() {
        for (owner in listOf(null, "", "   ")) assertEquals(ForegroundWindowClassifier.Decision.IGNORE, classify(owner, "android.view.View"))
    }

    @Test fun scannerSelectorDialogAndTouchShieldDoNotBecomeAnotherApp() {
        for (type in listOf("android.widget.LinearLayout", "android.app.AlertDialog", "android.view.View", null)) {
            assertEquals(ForegroundWindowClassifier.Decision.IGNORE, classify(own, type))
        }
    }

    @Test fun openingEitherRealScannerActivityRevokesTheRun() {
        for (type in listOf("eu.komars.forgescanner.MainActivity", "eu.komars.forgescanner.update.UpdateActivity")) {
            assertEquals(ForegroundWindowClassifier.Decision.OTHER_APP, classify(own, type))
        }
    }

    @Test fun transientSystemUiPopupDoesNotStopTheScanButSystemDialogsDo() {
        assertEquals(ForegroundWindowClassifier.Decision.IGNORE, classify("com.android.systemui", "android.widget.FrameLayout"))
        assertEquals(ForegroundWindowClassifier.Decision.OTHER_APP, classify("com.android.systemui", "com.android.systemui.statusbar.phone.SystemUIDialog"))
        assertEquals(ForegroundWindowClassifier.Decision.OTHER_APP, classify("com.android.systemui", "com.android.systemui.mediaprojection.permission.MediaProjectionPermissionActivity"))
        assertEquals(ForegroundWindowClassifier.Decision.OTHER_APP, classify("com.samsung.android.app.smartcapture", "android.widget.FrameLayout"))
    }

    @Test fun knownGameWindowConfirmsForegroundRegardlessOfItsViewClass() {
        assertEquals(ForegroundWindowClassifier.Decision.GAME, classify(game, "com.unity3d.player.UnityPlayerActivity"))
        assertEquals(ForegroundWindowClassifier.Decision.GAME, classify(game, null))
    }

    @Test fun samsungTransientFrameAfterUnityDoesNotRevokeButOpeningHomeDoes() {
        val decisions = listOf(
            classify(game, "com.unity3d.player.UnityPlayerActivity"),
            classify("com.sec.android.app.launcher", "android.widget.FrameLayout"),
            classify("com.sec.android.app.launcher", "com.sec.android.app.launcher.Launcher")
        )
        assertEquals(listOf(ForegroundWindowClassifier.Decision.GAME,
            ForegroundWindowClassifier.Decision.IGNORE, ForegroundWindowClassifier.Decision.OTHER_APP), decisions)
    }

    @Test fun samsungLauncherActivitiesAndRecentsStillRevokeTheRun() {
        for (type in listOf("com.sec.android.app.launcher.Launcher", "com.android.quickstep.RecentsActivity",
                "com.sec.android.app.launcher.RecentsActivity", "android.widget.LinearLayout", null)) {
            assertEquals(ForegroundWindowClassifier.Decision.OTHER_APP, classify("com.sec.android.app.launcher", type))
        }
    }

    @Test fun genericFrameLayoutExceptionIsLimitedToSamsungLauncherAndSystemUiPopups() {
        for (owner in listOf("com.android.launcher3", "com.google.android.apps.nexuslauncher",
                "com.android.settings", "com.other.app")) {
            assertEquals(ForegroundWindowClassifier.Decision.OTHER_APP, classify(owner, "android.widget.FrameLayout"))
        }
    }

    @Test fun everyOtherOwnedWindowStillRevokesTheRun() {
        for (owner in listOf("com.android.settings", "com.other.game")) {
            assertEquals(ForegroundWindowClassifier.Decision.OTHER_APP, classify(owner, "android.widget.FrameLayout"))
        }
    }
    @Test fun activeKeyboardDoesNotRevokeButItsSettingsAndOtherAppsDo() {
        val keyboard="com.samsung.android.honeyboard"
        for(type in listOf("android.inputmethodservice.SoftInputWindow","android.inputmethodservice.InputMethodService","android.widget.FrameLayout")) {
            assertEquals(ForegroundWindowClassifier.Decision.IGNORE, ForegroundWindowClassifier.classify(keyboard,type,own,game,keyboard))
        }
        assertEquals(ForegroundWindowClassifier.Decision.OTHER_APP, ForegroundWindowClassifier.classify(keyboard,"KeyboardSettingsActivity",own,game,keyboard))
        assertEquals(ForegroundWindowClassifier.Decision.OTHER_APP, ForegroundWindowClassifier.classify("com.other.app","android.inputmethodservice.SoftInputWindow",own,game,keyboard))
        assertEquals(ForegroundWindowClassifier.Decision.OTHER_APP, classify(own,"eu.komars.forgescanner.battle.BattleActivity"))
    }
}