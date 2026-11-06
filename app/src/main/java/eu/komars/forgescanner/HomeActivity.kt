package eu.komars.forgescanner

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.WindowInsets
import android.widget.*
import eu.komars.forgescanner.automation.GestureConnection
import eu.komars.forgescanner.ui.ScannerAppearance as S

/** Original scanner appearance, with setup completed before choosing a scanner. */
class HomeActivity : Activity() {
    private val app get() = application as ScannerApplication
    private var pairing = false
    private val handler = Handler(Looper.getMainLooper())
    private val refresh = Runnable { if (!isFinishing && !isDestroyed) home() }

    override fun onCreate(state: Bundle?) { super.onCreate(state); home() }
    override fun onResume() { super.onResume(); home(); handler.postDelayed(refresh, 400) }
    override fun onPause() { handler.removeCallbacks(refresh); super.onPause() }
    override fun onDestroy() {
        handler.removeCallbacks(refresh)
        if (isFinishing && !isChangingConfigurations) app.closeScanner()
        super.onDestroy()
    }
    private fun dp(value: Int) = S.dp(this, value)
    private fun notificationsReady() = Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun home() {
        window.statusBarColor = S.BACKGROUND
        window.navigationBarColor = S.BACKGROUND
        val scroll = ScrollView(this).apply {
            setBackgroundColor(S.BACKGROUND); isFillViewport = true; clipToPadding = false
            setPadding(dp(24), dp(36), dp(24), dp(28))
        }
        if (Build.VERSION.SDK_INT >= 30) scroll.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            view.setPadding(dp(24) + bars.left, dp(24) + bars.top, dp(24) + bars.right, dp(28) + bars.bottom)
            insets
        } else scroll.fitsSystemWindows = true
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(body, FrameLayout.LayoutParams(-1, -2)); setContentView(scroll)
        fun label(value: String, size: Int = 13, color: Int = S.MUTED, bold: Boolean = false, top: Int = 0) {
            body.addView(TextView(this).apply {
                text = value; textSize = size.toFloat(); setTextColor(color)
                setLineSpacing(dp(3).toFloat(), 1f)
                if (bold) setTypeface(typeface, Typeface.BOLD)
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) })
        }
        fun button(value: String, primary: Boolean = false, quiet: Boolean = false, top: Int = 10, action: () -> Unit) {
            val control=S.button(this, value, primary, quiet, action)
            val art=when(value){"Skaner zasobów"->R.drawable.menu_resources;"Walki"->R.drawable.menu_battles;else->0}
            if(art!=0) {
                val icon=getDrawable(art)!!.apply {setBounds(0,0,dp(48),dp(48))}
                control.setCompoundDrawablesRelative(icon,null,null,null)
                control.compoundDrawablePadding=dp(12)
            }
            body.addView(control, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) })
        }
        body.addView(ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher); contentDescription = "Botysław Skaner"
        }, LinearLayout.LayoutParams(dp(76), dp(76)).apply { bottomMargin = dp(18) })
        label("Botysław Skaner", 28, S.FOREGROUND, true)

        val paired = app.mobile.paired
        val overlay = Settings.canDrawOverlays(this)
        val accessibility = GestureConnection.isConnected
        val notifications = notificationsReady()
        val ready = paired && overlay && accessibility && notifications
        if (!ready) {
            label("Połącz konto i przygotuj uprawnienia. Potem wybierzesz skaner zasobów albo walki.", 16, top = 14)
            label(if (paired) "✓ Połączono: ${app.mobile.name}" else "Połącz z Discordem", 17, S.ACCENT, true, 20)
            label(if (paired) "Gotowe do wysyłania zatwierdzonych skanów." else "Użyj /mobile pair na swoim serwerze i połącz aplikację otrzymanym kodem.", top = 5)
            if (!paired) button("Połącz z Discordem", primary = true) { pairPhone() }
            if (!overlay) {
                label("Wyświetlanie nad grą", 17, S.FOREGROUND, true, 22)
                label("Zezwól na wyświetlanie małego okienka skanera nad grą.", top = 5)
                button("Zezwól na wyświetlanie nad aplikacjami", primary = paired) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                }
            }
            if (!accessibility) {
                label("Sterowanie skanowaniem", 17, S.FOREGROUND, true, 22)
                label("Dostępność pozwala skanerowi otwierać wskazane ekrany i przewijać listy. STOP kończy działania i odczyt ekranu.", top = 5)
                button("Włącz dostępność dla Botysława") { startActivity(GestureConnection.settingsIntent()) }
            }
            if (!notifications) {
                label("Powiadomienia skanera", 17, S.FOREGROUND, true, 22)
                label("W powiadomieniu możesz zakończyć udostępnianie ekranu.", top = 5)
                button("Zezwól na powiadomienia") {
                    if (Build.VERSION.SDK_INT >= 33) {
                        if (getPreferences(0).getBoolean("notificationsAsked", false) && !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                            startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
                        } else {
                            getPreferences(0).edit().putBoolean("notificationsAsked", true).apply()
                            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 920)
                        }
                    }
                }
            }
        } else {
            label("Wybierz, co chcesz zeskanować.", 16, top = 14)
            label("✓ Połączono: ${app.mobile.name}", 17, S.ACCENT, true, 20)
            button("Skaner zasobów", primary = true, top = 28) {
                app.battleMode = false; startActivity(Intent(this, MainActivity::class.java))
            }
            button("Walki", primary = true) { startActivity(Intent(this, eu.komars.forgescanner.battle.BattleActivity::class.java)) }
        }
        button("Opcje", quiet = true, top = 14) { startActivity(Intent(this, MainActivity::class.java).putExtra("options", true)) }
        eu.komars.forgescanner.ui.SupportFooter.add(this,body)
    }

    private fun pairPhone() {
        if (pairing) return
        val input = EditText(this).apply { hint = "6 znaków z /mobile pair"; isSingleLine = true }
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Komenda połączenia", "/mobile pair"))
        AlertDialog.Builder(this).setTitle("Połącz z Discordem")
            .setMessage("Skopiowano /mobile pair. Wklej komendę na swoim serwerze w Discordzie, potem wróć tutaj z prywatnym kodem.")
            .setView(input).setPositiveButton("Połącz") { _, _ ->
                pairing = true
                Toast.makeText(this, "Łączenie…", Toast.LENGTH_SHORT).show()
                app.mobile.pair(input.text.toString()) { result ->
                    pairing = false
                    if (!isFinishing && !isDestroyed) {
                        home()
                        Toast.makeText(this, result.getOrElse { it.message ?: "Błąd połączenia" }, Toast.LENGTH_LONG).show()
                    }
                }
            }.setNegativeButton("Wróć", null).show()
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://discord.com/channels/@me"))
        runCatching { startActivity(Intent(intent).setPackage("com.discord")) }.onFailure { runCatching { startActivity(intent) } }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode == 920) home()
    }
}
