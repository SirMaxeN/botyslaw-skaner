package eu.komars.forgescanner.automation

/** Window metadata only; never examines accessibility node contents or text. */
object ForegroundWindowClassifier {
    enum class Decision { IGNORE, GAME, OTHER_APP }

    fun classify(owner: String?, windowClass: String?, ownPackage: String, gamePackage: String, keyboardPackage: String? = null): Decision {
        // A window without an owner is not evidence that the game lost the foreground.
        if (owner.isNullOrBlank()) return Decision.IGNORE
        // Samsung emits this transient generic launcher window above Unity while
        // the game still owns focus. Real launcher/recents activities still revoke.
        if (owner == "com.sec.android.app.launcher" && windowClass == "android.widget.FrameLayout") return Decision.IGNORE
        // Heads-up notifications, status-bar chips and toasts are generic SystemUI FrameLayouts drawn
        // above the game without taking it out of the foreground (observed every few minutes on
        // Samsung during scans). The shade, dialogs and permission screens use their own classes.
        if (owner == "com.android.systemui" && windowClass == "android.widget.FrameLayout") return Decision.IGNORE
        if (owner == keyboardPackage && windowClass in setOf("android.inputmethodservice.SoftInputWindow", "android.inputmethodservice.InputMethodService", "android.widget.FrameLayout", "android.widget.LinearLayout", "android.inputmethodservice.KeyboardWindow")) return Decision.IGNORE
        if (owner == ownPackage) {
            val activity = windowClass.orEmpty().substringAfterLast('.')
            // Scanner overlays/dialogs temporarily take focus above the game.
            return if (activity in setOf("MainActivity", "UpdateActivity", "HomeActivity", "BattleActivity")) Decision.OTHER_APP else Decision.IGNORE
        }
        return if (owner == gamePackage) Decision.GAME else Decision.OTHER_APP
    }
}