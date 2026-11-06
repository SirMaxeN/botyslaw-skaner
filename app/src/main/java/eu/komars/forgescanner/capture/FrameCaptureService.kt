package eu.komars.forgescanner.capture

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import eu.komars.forgescanner.model.GrayScrollFrame
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager

/**
 * User-started, video-only MediaProjection session. Frames stay in memory and are
 * copied only after an explicit request. The consent Intent is never persisted/reused.
 */
class FrameCaptureService : Service() {
    private lateinit var workerThread: HandlerThread
    private lateinit var worker: Handler
    private val mainHandler = Handler(Looper.getMainLooper())
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var latestImage: Image? = null
    private var latestImageFrameId = 0L
    private var latestImageTimestampNanos = 0L
    private val grayRegionCache = GrayRegionFrameCache()
    private var width = 0
    private var height = 0
    private var density = 0
    private var acceptedStart = false
    private var closed = false
    private var capturedContentVisible = true
    private var capturedContentWasVisible = false
    private var frameRequest: PendingFrame? = null

    private data class PendingFrame(val callback: FrameCallback, val newerThan: Long? = null)

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            finishSession(null, stopProjection = false)
        }

        override fun onCapturedContentResize(width: Int, height: Int) {
            if (Build.VERSION.SDK_INT >= 34 && width > 0 && height > 0) {
                try {
                    resizeCapture(width, height)
                } catch (_: Exception) {
                    finishSession("Nie udało się dostosować odczytu po zmianie ekranu.")
                }
            }
        }

        override fun onCapturedContentVisibilityChanged(isVisible: Boolean) {
            capturedContentVisible = isVisible
            if (isVisible) capturedContentWasVisible = true
            else if (capturedContentWasVisible) { finishSession(null); return }
            if (!isVisible) {
                latestImage?.close()
                latestImage = null
                grayRegionCache.clear()
                cancelPending("Wybrana aplikacja nie jest widoczna. Otwórz grę i ponów odczyt.")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        workerThread = HandlerThread("ForgeFrameCapture").apply { start() }
        worker = Handler(workerThread.looper)
        CaptureConnection.attach(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopCapture()
            return START_NOT_STICKY
        }
        if (acceptedStart) return START_NOT_STICKY
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val consent = if (Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra(EXTRA_CONSENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra<Intent>(EXTRA_CONSENT)
        }
        if (resultCode != Activity.RESULT_OK || consent == null) {
            CaptureConnection.publish(this, CaptureState.ERROR, "Udostępnianie ekranu nie zostało zatwierdzone.")
            stopSelf()
            return START_NOT_STICKY
        }
        acceptedStart = true
        CaptureConnection.publish(this, CaptureState.STARTING)
        try {
            // Android 14+ requires the foreground service BEFORE getMediaProjection.
            val notification = createNotification()
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (_: Exception) {
            CaptureConnection.publish(this, CaptureState.ERROR, "System nie pozwolił rozpocząć odczytu ekranu. Spróbuj ponownie z aplikacji.")
            stopSelf()
            return START_NOT_STICKY
        }
        worker.post {
            if (closed) return@post
            try {
                val manager = getSystemService(MediaProjectionManager::class.java)
                projection = manager.getMediaProjection(resultCode, consent)
                val activeProjection = projection ?: error("No projection")
                activeProjection.registerCallback(projectionCallback, worker)
                density = resources.configuration.densityDpi
                val initialSize = displaySize()
                width = initialSize.first
                height = initialSize.second
                imageReader = newReader(width, height)
                // Exactly one createVirtualDisplay per consent token. Later changes use resize.
                virtualDisplay = activeProjection.createVirtualDisplay(
                    "ForgeScanner", width, height, density,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader!!.surface, null, worker,
                )
                CaptureConnection.publish(this, CaptureState.READY)
            } catch (_: Exception) {
                finishSession("Nie udało się rozpocząć odczytu. Włącz udostępnianie ekranu ponownie.")
            }
        }
        return START_NOT_STICKY
    }

    /** Called by CaptureConnection; pixels are copied only for this explicit request. */
    internal fun requestFrame(callback: FrameCallback, freshOnly: Boolean = false) {
        worker.post {
            val reader = imageReader
            if (closed || reader == null || virtualDisplay == null) {
                deliver(callback, null, "Udostępnianie ekranu zostało zakończone.")
                return@post
            }
            if (!capturedContentVisible) {
                deliver(callback, null, "Wybrana aplikacja nie jest widoczna. Otwórz grę i ponów odczyt.")
                return@post
            }
            if (frameRequest != null) {
                deliver(callback, null, "Poprzedni odczyt jeszcze trwa.")
                return@post
            }
            try {
                updateLatestImage(reader)
            } catch (_: IllegalStateException) {
                deliver(callback, null, "Ekran zmienił rozmiar. Spróbuj odczytu ponownie.")
                return@post
            }
            val pending = PendingFrame(callback, if (freshOnly) latestImage?.timestamp ?: -1L else null)
            frameRequest = pending
            latestImage?.let(::completePendingFrame)
            if (frameRequest !== pending) return@post
            worker.postDelayed({
                if (frameRequest === pending) {
                    frameRequest = null
                    deliver(callback, null, "Brak nowej klatki. Otwórz ekran gry i spróbuj ponownie.")
                }
            }, FRAME_TIMEOUT_MS)
        }
    }

    /** A cancelled prefetch must not occupy the next reader's single pending slot. */
    internal fun cancelFrameRequest(callback: FrameCallback) {
        worker.post {
            if (frameRequest?.callback === callback) frameRequest = null
        }
    }

    /** Dense inventory sampling reads only the calibrated region on the capture worker. */
    internal fun requestGrayRegion(region: Rect, expectedWidth: Int, expectedHeight: Int,
        outputWidth: Int, outputHeight: Int, callback: (GrayScrollFrame?, String?) -> Unit) {
        worker.post {
            var frame: GrayScrollFrame? = null
            var error: String? = null
            try {
                check(!closed && capturedContentVisible && imageReader != null) { "Odczyt ekranu został przerwany." }
                updateLatestImage(imageReader!!)
                val image = latestImage ?: throw IllegalStateException("Brak klatki ekranu.")
                val crop = image.cropRect
                check(crop.width() == expectedWidth && crop.height() == expectedHeight) { "Ekran zmienił rozmiar. Ustaw obszar ponownie." }
                val plane = image.planes.first()
                val source = GrayRegionFrameCache.Source(latestImageFrameId, latestImageTimestampNanos,
                    image.width, image.height, crop.left, crop.top, crop.width(), crop.height())
                val request = GrayRegionFrameCache.Region(region.left, region.top, region.width(), region.height(),
                    outputWidth, outputHeight)
                frame = grayRegionCache.read(source, request) {
                    RgbaPlaneConverter.readGrayRegion(plane.buffer.duplicate(), image.width, image.height,
                        plane.rowStride, plane.pixelStride, crop.left + region.left, crop.top + region.top,
                        region.width(), region.height(), outputWidth, outputHeight)
                }
            } catch (problem: Exception) { error = problem.message ?: "Nie udało się odczytać listy." }
            mainHandler.post { callback(frame, error) }
        }
    }

    internal fun stopCapture() {
        worker.post { finishSession(null) }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Android 14+ supplies the actual app-window dimensions through the projection callback.
        if (Build.VERSION.SDK_INT < 34) {
            worker.post {
                if (!closed && virtualDisplay != null) {
                    try {
                        val size = displaySize()
                        resizeCapture(size.first, size.second)
                    } catch (_: Exception) {
                        finishSession("Nie udało się dostosować odczytu po obróceniu ekranu.")
                    }
                }
            }
        }
    }

    private fun newReader(frameWidth: Int, frameHeight: Int): ImageReader {
        return ImageReader.newInstance(frameWidth, frameHeight, PixelFormat.RGBA_8888, 3).also { reader ->
            reader.setOnImageAvailableListener({ available ->
                if (available !== imageReader || closed) return@setOnImageAvailableListener
                try {
                    updateLatestImage(available)
                    latestImage?.let { completePendingFrame(it) }
                } catch (_: Exception) {
                    cancelPending("Nie udało się odczytać obrazu. Spróbuj ponownie.")
                }
            }, worker)
        }
    }

    private fun updateLatestImage(reader: ImageReader) {
        // Keep just one native frame buffer. A static app may not render another frame
        // after a request, so discarding every frame would make static screens time out.
        val newest = reader.acquireLatestImage() ?: return
        if (!capturedContentVisible) {
            newest.close()
            return
        }
        latestImage?.close()
        latestImage = newest
        latestImageFrameId++
        grayRegionCache.clear()
        // Preserve real source timing even when a polling request reuses this image.
        // Some producers leave the timestamp unset; stamp acquisition once, not each read.
        latestImageTimestampNanos = newest.timestamp.takeIf { it > 0L }
            ?: android.os.SystemClock.elapsedRealtimeNanos()
    }

    private fun completePendingFrame(image: Image) {
        val pending = frameRequest ?: return
        if (pending.newerThan != null && image.timestamp <= pending.newerThan) return
        frameRequest = null
        try {
            deliver(pending.callback, bitmapFromImage(image), null)
        } catch (_: Exception) {
            deliver(pending.callback, null, "Nie udało się odczytać obrazu. Spróbuj ponownie.")
        }
    }

    private fun resizeCapture(newWidth: Int, newHeight: Int) {
        val newDensity = resources.configuration.densityDpi
        if (closed || newWidth <= 0 || newHeight <= 0 || (newWidth == width && newHeight == height && density == newDensity)) return
        val display = virtualDisplay ?: return
        cancelPending("Ekran zmienił rozmiar. Spróbuj odczytu ponownie.")
        latestImage?.close()
        latestImage = null
        grayRegionCache.clear()
        val oldReader = imageReader
        val replacement = newReader(newWidth, newHeight)
        try {
            display.surface = null
            display.resize(newWidth, newHeight, newDensity)
            imageReader = replacement
            width = newWidth
            height = newHeight
            density = newDensity
            display.surface = replacement.surface
            oldReader?.setOnImageAvailableListener(null, null)
            oldReader?.close()
        } catch (error: Exception) {
            imageReader = oldReader
            replacement.close()
            throw error
        }
    }

    private fun bitmapFromImage(image: Image): Bitmap {
        val plane = image.planes.firstOrNull() ?: error("No image plane")
        val crop = image.cropRect
        // Copy the real pixels only, skipping hardware row padding and honoring cropRect.
        // The final row is not required to include padding in the Image plane buffer.
        val pixels = RgbaPlaneConverter.readArgb(
            plane.buffer.duplicate(), image.width, image.height,
            plane.rowStride, plane.pixelStride,
            crop.left, crop.top, crop.width(), crop.height(),
        )
        eu.komars.forgescanner.ui.CaptureIndicator.maskPixels(pixels,crop.width(),crop.height())
        return Bitmap.createBitmap(pixels, crop.width(), crop.height(), Bitmap.Config.ARGB_8888)
    }

    private fun cancelPending(message: String) {
        val pending = frameRequest
        frameRequest = null
        if (pending != null) deliver(pending.callback, null, message)
    }

    private fun deliver(callback: FrameCallback, bitmap: Bitmap?, error: String?) {
        mainHandler.post { callback(bitmap, error) }
    }

    private fun finishSession(error: String?, stopProjection: Boolean = true) {
        if (closed) return
        closed = true
        cancelPending("Udostępnianie ekranu zostało zakończone.")
        releaseCapture(stopProjection)
        CaptureConnection.publish(this, if (error == null) CaptureState.STOPPED else CaptureState.ERROR, error)
        mainHandler.post {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun releaseCapture(stopProjection: Boolean) {
        grayRegionCache.clear()
        runCatching { imageReader?.setOnImageAvailableListener(null, null) }
        runCatching { latestImage?.close() }
        latestImage = null
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        runCatching { imageReader?.close() }
        imageReader = null
        runCatching { projection?.unregisterCallback(projectionCallback) }
        if (stopProjection) runCatching { projection?.stop() }
        projection = null
    }

    private fun displaySize(): Pair<Int, Int> {
        val manager = getSystemService(WindowManager::class.java)
        return if (Build.VERSION.SDK_INT >= 30) {
            val bounds = manager.maximumWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            @Suppress("DEPRECATION")
            val metrics = DisplayMetrics().also { manager.defaultDisplay.getRealMetrics(it) }
            metrics.widthPixels to metrics.heightPixels
        }
    }

    private fun createNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Odczyt ekranu", NotificationManager.IMPORTANCE_LOW))
        val stopIntent = Intent(this, FrameCaptureService::class.java).setAction(ACTION_STOP)
        val stopAction = PendingIntent.getService(this, 1, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("Forge Scanner: odczyt włączony")
            .setContentText("Obraz zostaje na telefonie. Możesz zakończyć odczyt w dowolnej chwili.")
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .addAction(Notification.Action.Builder(null, "Zatrzymaj", stopAction).build())
        packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
            notification.setContentIntent(PendingIntent.getActivity(this, 2, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        }
        return notification.build()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        mainHandler.post {
            (application as? eu.komars.forgescanner.ScannerApplication)?.closeScanner()
            stopCapture()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        CaptureConnection.detach(this)
        worker.post {
            if (!closed) {
                closed = true
                cancelPending("Udostępnianie ekranu zostało zakończone.")
                releaseCapture(stopProjection = true)
            }
            worker.removeCallbacksAndMessages(null)
            workerThread.quitSafely()
        }
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "forge_screen_capture"
        private const val NOTIFICATION_ID = 14
        private const val ACTION_START = "eu.komars.forgescanner.START_CAPTURE"
        private const val ACTION_STOP = "eu.komars.forgescanner.STOP_CAPTURE"
        private const val EXTRA_RESULT_CODE = "projection_result_code"
        private const val EXTRA_CONSENT = "projection_consent"
        private const val FRAME_TIMEOUT_MS = 3000L

        /** Call from a visible Activity immediately after the user's fresh consent result. */
        fun start(context: Context, resultCode: Int, consent: Intent) {
            val intent = Intent(context, FrameCaptureService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_CONSENT, consent)
            context.startForegroundService(intent)
        }
    }
}
