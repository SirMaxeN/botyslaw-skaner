package eu.komars.forgescanner.update

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import eu.komars.forgescanner.MainActivity

/** Play checks use official store availability; website downloads remain explicit browser actions. */
class UpdateActivity : Activity() {
    private val manual: Boolean get() = intent.getBooleanExtra("manual", false)
    private var playChecker: PlayUpdateChecker? = null
    private var playDialog: AlertDialog? = null
    private var playCheckPending = false
    private var playNeedsResumeCheck = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (eu.komars.forgescanner.BuildConfig.BUILD_TYPE == "autoTest" && !manual) {
            startActivity(Intent(this, eu.komars.forgescanner.HomeActivity::class.java))
            finish(); return
        }
        if (eu.komars.forgescanner.BuildConfig.PLAY_DISTRIBUTION) {
            checkPlayUpdates(); return
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(40,40,40,40); setBackgroundColor(eu.komars.forgescanner.ui.ScannerAppearance.BACKGROUND)
        }
        layout.addView(ProgressBar(this))
        layout.addView(TextView(this).apply {
            text = "Sprawdzam aktualizacje…"; textSize = 18f; setTextColor(-1); gravity = Gravity.CENTER
        })
        setContentView(layout)
        // Remove APKs cached by the old in-app installer after an upgrade.
        java.io.File(cacheDir, "update.apk").delete()
        java.io.File(cacheDir, "update.part").delete()
        Thread {
            val result = runCatching { UpdateClient(this).latest() }
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    val release = result.getOrNull()
                    if (release != null) showUpdate(release)
                    else if (manual) {
                        AlertDialog.Builder(this)
                            .setTitle(if (result.isSuccess) "Aplikacja jest aktualna" else "Nie udało się sprawdzić aktualizacji")
                            .setMessage(if (result.isSuccess) "Masz najnowszą dostępną wersję aplikacji." else "Sprawdź połączenie z internetem i spróbuj ponownie później.")
                            .setPositiveButton("OK") { _, _ -> openScanner() }
                            .setOnCancelListener { openScanner() }.show()
                    } else openScanner()
                }
            }
        }.start()
    }
    private fun showUpdate(release: UpdateClient.Release) {
        AlertDialog.Builder(this)
            .setTitle("Dostępna aktualizacja ${release.name}")
            .setMessage("Możesz pobrać nową wersję z komars.eu. Pobieranie otworzy się w przeglądarce, a instalację zatwierdzisz w Androidzie. Twoje ustawienia pozostaną zapisane.")
            .setPositiveButton("Pobierz") { _, _ ->
                openScanner()
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.url)).apply {
                        addCategory(Intent.CATEGORY_BROWSABLE)
                    })
                } catch (_: Exception) {
                    Toast.makeText(this, "Nie udało się otworzyć przeglądarki.", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Później") { _, _ -> openScanner() }
            .setOnCancelListener { openScanner() }
            .show()
    }

    private fun checkPlayUpdates() {
        playDialog?.dismiss(); playDialog = null
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(40, 40, 40, 40); setBackgroundColor(eu.komars.forgescanner.ui.ScannerAppearance.BACKGROUND)
        }
        layout.addView(ProgressBar(this))
        layout.addView(TextView(this).apply {
            text = "Sprawdzam aktualizacje w Google Play…"; textSize = 18f
            setTextColor(-1); gravity = Gravity.CENTER
        })
        setContentView(layout)
        val checker = playChecker ?: PlayUpdateChecker(this).also { playChecker = it }
        playCheckPending = true
        checker.check { status ->
            playCheckPending = false
            if (isFinishing || isDestroyed) return@check
            when (PlayUpdateCheckController.presentation(status, manual)) {
                PlayUpdateCheckController.Presentation.OFFER_UPDATE -> showPlayDialog(AlertDialog.Builder(this)
                    .setTitle("Nowa wersja dostępna w Google Play")
                    .setMessage("Możesz otworzyć Google Play i zaktualizować aplikację. Instalację obsłuży sklep.")
                    .setPositiveButton("Otwórz Google Play") { _, _ -> openGooglePlay() }
                    .setNegativeButton("Później") { _, _ -> openScanner() }
                    .setOnCancelListener { openScanner() })
                PlayUpdateCheckController.Presentation.SHOW_CURRENT -> showPlayDialog(AlertDialog.Builder(this)
                    .setTitle("Aplikacja jest aktualna")
                    .setMessage("Google Play nie udostępnia teraz nowszej wersji dla tego konta.")
                    .setPositiveButton("OK") { _, _ -> openScanner() }
                    .setOnCancelListener { openScanner() })
                PlayUpdateCheckController.Presentation.SHOW_RETRY -> showPlayDialog(AlertDialog.Builder(this)
                    .setTitle("Nie udało się sprawdzić aktualizacji")
                    .setMessage("Nie potwierdzono dostępności nowej wersji. Spróbuj ponownie lub sprawdź ją w Google Play.")
                    .setPositiveButton("Spróbuj ponownie") { _, _ -> checkPlayUpdates() }
                    .setNeutralButton("Google Play") { _, _ -> openGooglePlay() }
                    .setNegativeButton("Zamknij") { _, _ -> openScanner() }
                    .setOnCancelListener { openScanner() })
                PlayUpdateCheckController.Presentation.OPEN_SCANNER -> openScanner()
            }
        }
    }

    private fun showPlayDialog(builder: AlertDialog.Builder) {
        playDialog?.dismiss()
        playDialog = builder.create().also { it.show() }
    }

    private fun openGooglePlay() {
        // Automatic startup may launch MainActivity; put the explicitly requested store above it.
        openScanner()
        try {
            startActivity(Intent(Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=$packageName"))
                .apply { addCategory(Intent.CATEGORY_BROWSABLE) })
        } catch (_: Exception) {
            Toast.makeText(this, "Nie udało się otworzyć Google Play.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onStop() {
        if (playCheckPending) {
            playChecker?.cancel(); playCheckPending = false
            playNeedsResumeCheck = true
        }
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        if (playNeedsResumeCheck && !isFinishing && !isDestroyed) {
            playNeedsResumeCheck = false
            checkPlayUpdates()
        }
    }

    override fun onDestroy() {
        playCheckPending = false; playNeedsResumeCheck = false
        playChecker?.cancel(); playDialog?.dismiss(); playDialog = null
        super.onDestroy()
    }

    private fun openScanner() {
        if (isFinishing || isDestroyed) return
        playChecker?.cancel()
        if (!manual) startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
