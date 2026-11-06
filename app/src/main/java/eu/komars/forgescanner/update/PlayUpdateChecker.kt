package eu.komars.forgescanner.update

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.UpdateAvailability

/** Official Play metadata only: no APK download, installer, or website update request. */
class PlayUpdateChecker(context: Context) {
    private val application = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val controller = PlayUpdateCheckController(PlayUpdateCheckController.Port { complete ->
        AppUpdateManagerFactory.create(application).appUpdateInfo
            .addOnSuccessListener { info ->
                val status = when (info.updateAvailability()) {
                    UpdateAvailability.UPDATE_AVAILABLE -> PlayUpdateCheckController.Status.UPDATE_AVAILABLE
                    UpdateAvailability.UPDATE_NOT_AVAILABLE -> PlayUpdateCheckController.Status.UP_TO_DATE
                    else -> PlayUpdateCheckController.Status.UNAVAILABLE
                }
                complete(status)
            }
            .addOnFailureListener { complete(PlayUpdateCheckController.Status.UNAVAILABLE) }
    }, PlayUpdateCheckController.Scheduler { delay, action ->
        val task = Runnable(action)
        handler.postDelayed(task, delay)
        val cancel: () -> Unit = { handler.removeCallbacks(task) }
        cancel
    })

    fun check(complete: (PlayUpdateCheckController.Status) -> Unit) = controller.check(complete)
    fun cancel() = controller.cancel()
}
