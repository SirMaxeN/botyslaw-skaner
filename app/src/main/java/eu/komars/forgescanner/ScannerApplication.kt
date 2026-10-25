package eu.komars.forgescanner

import android.app.Application
import eu.komars.forgescanner.capture.CaptureConnection
import eu.komars.forgescanner.capture.CaptureState
import eu.komars.forgescanner.model.ScanSession
import eu.komars.forgescanner.model.GuidedScanFlow
import eu.komars.forgescanner.model.CalibrationStore
import eu.komars.forgescanner.ui.OverlayController
import eu.komars.forgescanner.sync.MobileSyncClient

class ScannerApplication : Application() {
    var session = ScanSession()
        private set
    lateinit var overlay: OverlayController
        private set
    val flow = GuidedScanFlow { session }
    lateinit var calibrations: CalibrationStore
        private set
    lateinit var mobile: MobileSyncClient
        private set

    @Volatile var demo = false
        private set
    private var realSession: ScanSession? = null

    fun setDemo(enabled: Boolean) {
        if (demo == enabled) return
        closeScanner()
        if (enabled) {
            realSession = session
            session = ScanSession().apply {
                eu.komars.forgescanner.model.ResourceField.entries.forEach { field ->
                    setValue(field, when(field) {
                        eu.komars.forgescanner.model.ResourceField.HAMMERS -> 11026L
                        eu.komars.forgescanner.model.ResourceField.MOUNT_KEYS -> 2840L
                        eu.komars.forgescanner.model.ResourceField.MOUNT_COST -> 50L
                        eu.komars.forgescanner.model.ResourceField.MOUNTS -> 5L
                        eu.komars.forgescanner.model.ResourceField.TICKETS -> 9880L
                        eu.komars.forgescanner.model.ResourceField.SKILL_COST -> 188L
                        eu.komars.forgescanner.model.ResourceField.PETS -> 28L
                    })
                }
            }
        } else { session = realSession ?: ScanSession(); realSession = null }
        demo = enabled
        flow.reset()
    }

    private var restartScan = true

    fun prepareOverlaySession() {
        if (restartScan) { startNewSession(); restartScan = false }
        flow.start()
    }

    fun closeScanner() {
        restartScan = true
        overlay.hide()
        CaptureConnection.stop()
    }

    override fun onCreate() {
        super.onCreate()
        calibrations = CalibrationStore(this)
        mobile = MobileSyncClient(this, demoMode = { demo })
        overlay = OverlayController(this, { session }, flow, calibrations)
        CaptureConnection.addStateListener { state, _ ->
            when (state) {
                CaptureState.READY -> { prepareOverlaySession(); overlay.show() }
                CaptureState.STOPPED, CaptureState.ERROR -> { restartScan = true; overlay.hide() }
                else -> Unit
            }
        }
    }

    fun startNewSession() {
        overlay.resetSession()
        flow.reset()
        session = ScanSession()
    }
}
