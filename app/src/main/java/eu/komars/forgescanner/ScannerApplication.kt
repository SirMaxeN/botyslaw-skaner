package eu.komars.forgescanner

import android.app.Application
import eu.komars.forgescanner.capture.CaptureConnection
import eu.komars.forgescanner.capture.CaptureState
import eu.komars.forgescanner.model.ScanSession
import eu.komars.forgescanner.model.GuidedScanFlow
import eu.komars.forgescanner.model.CalibrationStore
import eu.komars.forgescanner.ui.OverlayController
import eu.komars.forgescanner.sync.MobileSyncClient

/** Bump to clear saved screen fields once more after an incompatible overlay/navigation change. */
private const val SETUP_SCHEMA = 3

class ScannerApplication : Application() {
    val knowledge by lazy { eu.komars.forgescanner.sync.SharedKnowledgeSync(this, mobile) }
    var session = ScanSession()
        private set
    lateinit var overlay: OverlayController
        private set
    val flow = GuidedScanFlow { session }
    var battleMode = false
    val battleOverlay by lazy { eu.komars.forgescanner.battle.BattleOverlay(this) }
    val warOverlay by lazy { eu.komars.forgescanner.battle.WarPreparationOverlay(this) }
    private val captureIndicator by lazy { eu.komars.forgescanner.ui.CaptureIndicator(this) }
    private val busyOwners=mutableSetOf<String>()
    fun setScannerBusy(owner:String,busy:Boolean) {if(busy)busyOwners+=owner else busyOwners-=owner;refreshIndicator()}
    private fun refreshIndicator() {if(busyOwners.isNotEmpty() && CaptureConnection.state==CaptureState.READY)captureIndicator.show()else captureIndicator.hide()}
    var automaticMode = false
    lateinit var autoOverlay: eu.komars.forgescanner.ui.AutoScanOverlay
        private set

    fun showScannerOverlay() { if (automaticMode) autoOverlay.show() else overlay.show() }
    fun acceptAutomaticReadings(values: Map<eu.komars.forgescanner.model.ScanTarget, eu.komars.forgescanner.automation.AutoReading>) {
        session = ScanSession().apply {
            values.forEach { (target, reading) -> setValue(target.field, reading.value, confirmed = false,
                approximate = reading.approximate, sourceText = reading.source) }
            goTo(eu.komars.forgescanner.model.ScanStep.REVIEW)
        }
        watchResourceDraft()
        session.onChange?.invoke(session.snapshot())
        flow.reset()
    }
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
                        eu.komars.forgescanner.model.ResourceField.COINS -> 2_120_000L
                        eu.komars.forgescanner.model.ResourceField.GEMS -> 7L
                        eu.komars.forgescanner.model.ResourceField.SHELLS -> 78L
                        eu.komars.forgescanner.model.ResourceField.RED_VIALS -> 46_600L
                        eu.komars.forgescanner.model.ResourceField.GREEN_VIALS -> 1_680L
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
        busyOwners.clear();captureIndicator.hide()
        warOverlay.hide()
        battleOverlay.endSession()
        restartScan = true
        overlay.hide()
        autoOverlay.hide()
        CaptureConnection.stop()
    }

    /**
     * Screen fields saved before the current setup schema must be configured again.
     * Clear them once after the update (before any store reads them); Discord pairing, drafts and
     * learned reading data stay. The test build keeps its calibration (AGENTS.md).
     */
    private fun resetOldSetupOnce() {
        if (BuildConfig.BUILD_TYPE == "autoTest") return
        val migrations = getSharedPreferences("app_migrations_v1", MODE_PRIVATE)
        if (migrations.getInt("setup_schema", 0) >= SETUP_SCHEMA) return
        for (name in listOf("scan_calibrations_v1", "auto_scan_navigation_v1", "auto_read_checkpoints_v1"))
            getSharedPreferences(name, MODE_PRIVATE).edit().clear().commit()
        migrations.edit().putInt("setup_schema", SETUP_SCHEMA).commit()
    }
    override fun onCreate() {
        super.onCreate()
        resetOldSetupOnce()
        eu.komars.forgescanner.battle.GameDigitMemory.shared=eu.komars.forgescanner.battle.GameDigitMemory(this)
        if(BuildConfig.DEBUG)Thread {
            runCatching {val source=java.io.File(cacheDir,"home-rejected.png");if(source.exists()) {
                val image=android.graphics.BitmapFactory.decodeFile(source.absolutePath)
                val reduced=android.graphics.Bitmap.createScaledBitmap(image,360,image.height*360/image.width,true)
                java.io.FileOutputStream(java.io.File(cacheDir,"home-native-360.png")).use {reduced.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};if(reduced!==image)reduced.recycle()
                java.io.File(cacheDir,"home-diagnostic.txt").writeText(eu.komars.forgescanner.battle.BattleHomeProof.diagnostic(image));image.recycle()
            }}
        }.start()
        calibrations = CalibrationStore(this)
        mobile = MobileSyncClient(this, demoMode = { demo })
        // Shared reading knowledge from the bot server: new installs and updates start trained.
        knowledge.syncAsync(force=true)
        if(mobile.paired) eu.komars.forgescanner.model.ResourceDraftStore(this).load(mobile.name.orEmpty())?.let {session=it}
        watchResourceDraft()
        overlay = OverlayController(this, { session }, flow, calibrations)
        autoOverlay = eu.komars.forgescanner.ui.AutoScanOverlay(this, calibrations)
        CaptureConnection.addStateListener { state, _ ->
            if(state==CaptureState.STOPPED || state==CaptureState.ERROR)busyOwners.clear()
            refreshIndicator()
            when (state) {
                CaptureState.READY -> { if (!battleMode) { prepareOverlaySession(); showScannerOverlay() } }
                CaptureState.STOPPED, CaptureState.ERROR -> { restartScan = true; overlay.hide(); autoOverlay.hide(); battleOverlay.endSession() }
                else -> Unit
            }
        }
    }

    private fun watchResourceDraft() {
        val owner=mobile.name.orEmpty()
        session.onChange={snapshot ->
            if(!demo && mobile.paired && mobile.name.orEmpty()==owner) {
                eu.komars.forgescanner.model.ResourceDraftStore(this).save(owner,snapshot)
                val approved=snapshot.values.filter {it.key.isCost && it.value.confirmed && !it.value.approximate}.mapValues {it.value.value}
                if(approved.isNotEmpty())eu.komars.forgescanner.model.ConfirmedCostMemory(this).remember(owner,approved)
            }
        }
    }
    fun startNewSession() {
        overlay.resetSession()
        autoOverlay.resetSession()
        flow.reset()
        session = ScanSession()
        watchResourceDraft()
    }
}
