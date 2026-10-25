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

/** Checks metadata only. Download is an explicit user action handled by a browser. */
class UpdateActivity : Activity() {
    private val manual: Boolean get() = intent.getBooleanExtra("manual", false)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (eu.komars.forgescanner.BuildConfig.PLAY_DISTRIBUTION) {
            if (manual) {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")).apply { addCategory(Intent.CATEGORY_BROWSABLE) })
                } catch (_: Exception) { Toast.makeText(this, "Nie udało się otworzyć Google Play.", Toast.LENGTH_LONG).show() }
            }
            openScanner(); return
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(40,40,40,40); setBackgroundColor(0xff101827.toInt())
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
    private fun openScanner() {
        if (isFinishing || isDestroyed) return
        if (!manual) startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
