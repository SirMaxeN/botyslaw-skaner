package eu.komars.forgescanner.capture

import android.graphics.Bitmap
import android.graphics.Rect
import eu.komars.forgescanner.model.GrayScrollFrame
import android.os.Handler
import android.os.Looper

/** The result bitmap belongs to the recipient and must be recycled after recognition. */
typealias FrameCallback = (bitmap: Bitmap?, error: String?) -> Unit
typealias CaptureStateListener = (state: CaptureState, detail: String?) -> Unit

enum class CaptureState { STOPPED, STARTING, READY, ERROR }

/**
 * In-process connection between the activity/overlay and the capture service.
 * Every callback is delivered on the main thread. No Activity or screenshot is retained.
 */
object CaptureConnection {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val listeners = LinkedHashSet<CaptureStateListener>()
    private var service: FrameCaptureService? = null

    @Volatile
    var state: CaptureState = CaptureState.STOPPED
        private set

    @Volatile
    var detail: String? = null
        private set

    fun addStateListener(listener: CaptureStateListener) = onMain {
        listeners.add(listener)
        listener(state, detail)
    }

    fun removeStateListener(listener: CaptureStateListener) = onMain {
        listeners.remove(listener)
    }

    /** Hide the overlay and let its removal render before requesting the latest frame. */
    fun requestFrame(callback: FrameCallback) = onMain {
        val current = service
        if (current == null || state != CaptureState.READY) {
            callback(null, "Najpierw rozpocznij udostępnianie ekranu.")
        } else {
            current.requestFrame(callback)
        }
    }

    fun requestGrayRegion(region: Rect, width: Int, height: Int, outputWidth: Int, outputHeight: Int,
        callback: (GrayScrollFrame?, String?) -> Unit) = onMain {
        val current = service
        if (current == null || state != CaptureState.READY) callback(null, "Najpierw rozpocznij udostępnianie ekranu.")
        else current.requestGrayRegion(Rect(region), width, height, outputWidth, outputHeight, callback)
    }

    fun stop() = onMain { service?.stopCapture() }

    internal fun attach(owner: FrameCaptureService) = onMain {
        service = owner
    }

    internal fun publish(owner: FrameCaptureService, next: CaptureState, message: String? = null) = onMain {
        if (service !== owner) return@onMain
        state = next
        detail = message
        listeners.toList().forEach { listener -> runCatching { listener(next, message) } }
    }

    internal fun detach(owner: FrameCaptureService) = onMain {
        if (service !== owner) return@onMain
        service = null
        if (state != CaptureState.ERROR) {
            state = CaptureState.STOPPED
            detail = null
            listeners.toList().forEach { listener -> runCatching { listener(state, detail) } }
        }
    }

    private inline fun onMain(crossinline action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action()
        else mainHandler.post { action() }
    }
}
