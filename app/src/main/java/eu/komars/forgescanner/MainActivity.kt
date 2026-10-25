package eu.komars.forgescanner

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import eu.komars.forgescanner.capture.CaptureConnection
import eu.komars.forgescanner.capture.CaptureState
import eu.komars.forgescanner.capture.CaptureStateListener
import eu.komars.forgescanner.capture.FrameCaptureService
import eu.komars.forgescanner.model.ClipboardReportFormatter
import eu.komars.forgescanner.model.GameNumberParser
import eu.komars.forgescanner.model.ResourceField
import eu.komars.forgescanner.model.ScanStep

/** A permission-first launcher. Resource editing is a separate, explicitly opened screen. */
class MainActivity : Activity() {
    private enum class Screen { PERMISSION, HOME, RESULTS }

    private val scanner: ScannerApplication get() = application as ScannerApplication
    private val fields = linkedMapOf<ResourceField, EditText>()
    private val fieldNotes = linkedMapOf<ResourceField, TextView>()
    private val drafts = linkedMapOf<ResourceField, String>()
    private val dirtyFields = mutableSetOf<ResourceField>()
    private lateinit var scroll: ScrollView
    private var startButton: Button? = null
    private var captureStatus: TextView? = null
    private var currentScreen: Screen? = null
    private var requestedScreen = Screen.HOME
    private var bindingValues = false
    private var permissionFlowPending = false
    private var openGameWhenReady = false
    private var renderedSessionStart = 0L
    private var syncBusy = false

    private val stateListener: CaptureStateListener = { state, detail ->
        renderCaptureState(state, detail)
        if (state == CaptureState.READY && openGameWhenReady) {
            openGameWhenReady = false
            scanner.overlay.show()
            openForgeMaster()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = BACKGROUND
        window.navigationBarColor = BACKGROUND
        restoreDraft(savedInstanceState)
        if (intent.getBooleanExtra("home", false)) { requestedScreen = Screen.HOME; intent.removeExtra("home") }
        consumeReviewIntent()
        renderScreen()
    }

    override fun onStart() {
        super.onStart()
        CaptureConnection.addStateListener(stateListener)
    }

    override fun onResume() {
        super.onResume()
        renderScreen()
    }

    override fun onStop() {
        rememberDraft()
        CaptureConnection.removeStateListener(stateListener)
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("home", false)) { requestedScreen = Screen.HOME; intent.removeExtra("home") }
        consumeReviewIntent()
        renderScreen()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        rememberDraft()
        outState.putLong("sessionStart", scanner.session.snapshot().startedAtMillis)
        outState.putBoolean("results", requestedScreen == Screen.RESULTS)
        outState.putBoolean("permissionFlowPending", permissionFlowPending)
        outState.putBoolean("openGameWhenReady", openGameWhenReady)
        drafts.forEach { (field, value) -> outState.putString("draft_${field.name}", value) }
        super.onSaveInstanceState(outState)
    }

    @Deprecated("Native Activity back navigation is sufficient for this small application.")
    override fun onBackPressed() {
        if (currentScreen == Screen.RESULTS) showHome() else super.onBackPressed()
    }

    private fun renderScreen() {
        val sessionStart = scanner.session.snapshot().startedAtMillis
        if (renderedSessionStart != 0L && renderedSessionStart != sessionStart) {
            drafts.clear()
            dirtyFields.clear()
            fields.clear()
            fieldNotes.clear()
            currentScreen = null
        }
        renderedSessionStart = sessionStart
        val desired = if (Settings.canDrawOverlays(this)) requestedScreen else Screen.PERMISSION
        if (currentScreen != desired) {
            rememberDraft()
            fields.clear()
            fieldNotes.clear()
            startButton = null
            captureStatus = null
            currentScreen = desired
            val content = createPage()
            when (desired) {
                Screen.PERMISSION -> buildPermissionScreen(content)
                Screen.HOME -> buildHomeScreen(content)
                Screen.RESULTS -> buildResultsScreen(content)
            }
        }
        renderSnapshot()
        renderCaptureState(CaptureConnection.state, CaptureConnection.detail)
    }

    private fun createPage(): LinearLayout {
        scroll = ScrollView(this).apply {
            setBackgroundColor(BACKGROUND)
            isFillViewport = true
            clipToPadding = false
            setPadding(dp(24), dp(36), dp(24), dp(28))
        }
        if (Build.VERSION.SDK_INT >= 30) {
            scroll.setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                view.setPadding(dp(24) + bars.left, dp(24) + bars.top, dp(24) + bars.right, dp(24) + bars.bottom)
                insets
            }
        } else scroll.fitsSystemWindows = true
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content, android.widget.FrameLayout.LayoutParams(-1, -2))
        setContentView(scroll)
        return content
    }

    private fun buildPermissionScreen(content: LinearLayout) {
        content.addView(android.widget.ImageView(this).apply {
            setImageResource(eu.komars.forgescanner.R.mipmap.ic_launcher)
            contentDescription = "Botysław Skaner"
        }, LinearLayout.LayoutParams(dp(76), dp(76)).apply { bottomMargin = dp(18) })
        content.addView(text("Botysław Skaner", 28, FOREGROUND, bold = true))
        content.addView(action(if (scanner.demo) "Wyłącz tryb demonstracyjny" else "Tryb demonstracyjny", 0x00f10015, quiet = true) { toggleDemo() }.withTop(10))
        addConnectionCard(content)
        content.addView(text("Najpierw zezwól na wyświetlanie małego przycisku nad grą.", 16, MUTED).withTop(16))
        content.addView(action("Zezwól na wyświetlanie nad aplikacjami", ID_ALLOW_OVERLAY, primary = true) {
            openOverlayPermission()
        }.withTop(28))
    }

    private fun addConnectionCard(content: LinearLayout) {
        if (scanner.demo) content.addView(text("DEMO — dane nie są wysyłane", 17, ACCENT, bold = true).withTop(12))
        content.addView(text(if (scanner.mobile.paired) "✓ Połączono: ${scanner.mobile.name}" else "Połącz z Discordem", 17, SUCCESS, bold = true).withTop(20))
        content.addView(text(if (scanner.mobile.paired) "Gotowe do wysyłania zatwierdzonych skanów." else "Połącz konto, aby wysyłać zasoby prosto do bota.", 13, MUTED).withTop(5))
        if (!scanner.mobile.paired) content.addView(action("Połącz z Discordem", 0x00f10011, primary = true) { pairPhone() }.withTop(10))
    }

    private fun buildHomeScreen(content: LinearLayout) {
        content.addView(android.widget.ImageView(this).apply {
            setImageResource(eu.komars.forgescanner.R.mipmap.ic_launcher)
            contentDescription = "Botysław Skaner"
        }, LinearLayout.LayoutParams(dp(76), dp(76)).apply { bottomMargin = dp(18) })
        content.addView(text("Botysław Skaner", 28, FOREGROUND, bold = true))
        content.addView(action(if (scanner.demo) "Wyłącz tryb demonstracyjny" else "Tryb demonstracyjny", 0x00f10015, quiet = true) { toggleDemo() }.withTop(10))
        addConnectionCard(content)
        content.addView(text("W grze naciśnij Skanuj. Nakładka podpowie, co pokazać.", 16, MUTED).withTop(14))
        startButton = action("Uruchom nakładkę", ID_START_OVERLAY, primary = true) { startPermissionFlow() }
        content.addView(startButton!!.withTop(28))
        content.addView(action("Wyniki", ID_RESULTS) { showReview() }.withTop(12))
        captureStatus = text("", 13, MUTED).withTop(14).also(content::addView)
        content.addView(action("Opcje", ID_OPTIONS, quiet = true) { showOptions() }.withTop(14))
        content.addView(text("Tworzone przez SirMaxeN", 12, MUTED).withTop(28))
        content.addView(text("Tworzę aplikacje z pasji i udostępniam je całkowicie za darmo. Niestety utrzymanie serwerów i narzędzi trochę kosztuje. Jeśli doceniasz moją pracę i chcesz wesprzeć tworzenie kolejnych przydatnych rzeczy, możesz postawić mi kawę. Dzięki za każde wsparcie!", 13, MUTED).withTop(8))
        content.addView(action("☕ Postaw mi kawę", 0x00f10010, quiet = true) {
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://buymeacoffee.com/sirmaxen"))) }
                .onFailure { toast("Otwórz buymeacoffee.com/sirmaxen w przeglądarce") }
        }.withTop(6))
    }

    private fun buildResultsScreen(content: LinearLayout) {
        scanner.overlay.minimize()
        content.addView(action("‹ Wróć", ID_BACK, quiet = true) { showHome() })
        content.addView(text("Sprawdź dane", 27, FOREGROUND, bold = true).withTop(18))
        if (scanner.demo) content.addView(text("DEMO — przykładowe dane, bez wysyłania do serwera", 14, ACCENT, bold = true).withTop(10))
        content.addView(text("9880 = 9,88k. Puste pole oznacza brak odczytu. Wpisz 0, jeśli nic nie masz.", 13, MUTED).withTop(10))
        ResourceField.entries.forEach { addResourceField(content, it) }
        content.addView(action("Domyślne koszty: 200 / 50", ID_DEFAULT_COSTS, quiet = true) { confirmDefaultCosts() }.withTop(16))
        content.addView(text(if (scanner.demo) "DEMO: wysyłka jest symulowana lokalnie. Kopiowany tekst nie uruchomi importu." else "Wyślij zapisze te zasoby i koszty w bocie dla konta: ${scanner.mobile.name ?: "najpierw połącz Discorda"}. Będą widoczne w raportach klanu. Skopiuj komendę pozwala zamiast tego uruchomić /import w Discordzie.", 13, MUTED).withTop(12))
        content.addView(action(if (scanner.demo) "Skopiuj przykład DEMO" else "Skopiuj komendę", ID_COPY, primary = true) { validatedValues()?.let { copyConfirmedValues(it) } }.withTop(14))
        content.addView(action("Wyślij dane do bota", ID_SEND, primary = true) { confirmSend() }.withTop(12))
        content.addView(action("Reset / nowe skanowanie", ID_RESET, quiet = true) { confirmNewScan() }.withTop(12))
    }

    private fun addResourceField(parent: LinearLayout, field: ResourceField) {
        parent.addView(text(field.label, 14, FOREGROUND, bold = true).withTop(20))
        val input = EditText(this).apply {
            id = FIELD_ID_BASE + field.ordinal
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            isSingleLine = true
            textSize = 18f
            setTextColor(FOREGROUND)
            setHintTextColor(MUTED)
            hint = if (field.isCost) "Wpisz koszt" else "np. 9880 lub 9,88k"
            setPadding(dp(14), dp(11), dp(14), dp(11))
            background = rounded(INPUT_BACKGROUND, 10, BORDER)
            filters = arrayOf(InputFilter.LengthFilter(32))
            setSelectAllOnFocus(true)
            contentDescription = field.label
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(value: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(value: CharSequence?, start: Int, before: Int, count: Int) {
                    if (!bindingValues) {
                        dirtyFields.add(field)
                        drafts[field] = value?.toString().orEmpty()
                        error = null
                        fieldNotes[field]?.apply {
                            text = "Do zatwierdzenia"
                            setTextColor(ACCENT)
                        }
                    }
                }
                override fun afterTextChanged(value: Editable?) = Unit
            })
        }
        fields[field] = input
        parent.addView(input.withTop(7))
        fieldNotes[field] = text("Nie odczytano", 12, MUTED).withTop(5).also(parent::addView)
    }

    private fun showOptions() {
        AlertDialog.Builder(this)
            .setTitle("Opcje")
            .setItems(arrayOf("Zatrzymaj nakładkę", "Nowe skanowanie", "Usuń zapisane ustawienia odczytu", "Połącz z Discordem", "Odłącz telefon lokalnie", "Usuń dane aplikacji", "Sprawdź aktualizacje", "Polityka prywatności")) { _, index ->
                when (index) {
                    0 -> {
                        openGameWhenReady = false
                        CaptureConnection.stop()
                        scanner.overlay.hide()
                        renderCaptureState(CaptureConnection.state, CaptureConnection.detail)
                    }
                    1 -> confirmNewScan()
                    2 -> confirmClearCalibration()
                    3 -> pairPhone()
                    4 -> { scanner.mobile.disconnect(); refreshConnectionScreen(); toast("Usunięto połączenie z telefonu. Dostęp możesz też cofnąć przez /mobile revoke.") }
                    5 -> confirmEraseApplicationData()
                    6 -> startActivity(Intent(this, eu.komars.forgescanner.update.UpdateActivity::class.java).putExtra("manual", true))
                    7 -> runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://komars.eu/botyslaw-skaner/privacy.html"))) }.onFailure { toast("Otwórz komars.eu/botyslaw-skaner/privacy.html") }
                }
            }
            .setNegativeButton("Zamknij", null)
            .show()
    }

    private fun toggleDemo() {
        AlertDialog.Builder(this).setTitle(if (scanner.demo) "Wyłączyć DEMO?" else "Tryb demonstracyjny")
            .setMessage(if (scanner.demo) "Przywrócimy poprzednie wyniki i prawdziwe połączenie z botem." else "Wczytamy przykładowe zasoby. Parowanie i wysyłka będą symulowane wyłącznie na telefonie. Żadne dane DEMO nie trafią do serwera ani Discorda. Twoje prawdziwe połączenie pozostanie zapisane.")
            .setPositiveButton(if (scanner.demo) "Wyłącz DEMO" else "Włącz DEMO") { _, _ ->
                scanner.setDemo(!scanner.demo)
                drafts.clear(); dirtyFields.clear(); fields.clear(); fieldNotes.clear()
                currentScreen = null; requestedScreen = if (scanner.demo) Screen.RESULTS else Screen.HOME
                renderScreen()
            }.setNegativeButton("Anuluj", null).show()
    }

    private fun refreshConnectionScreen() {
        rememberDraft()
        currentScreen = null
        renderScreen()
    }

    private fun confirmEraseApplicationData() {
        AlertDialog.Builder(this).setTitle("Usunąć wszystkie dane aplikacji?")
            .setMessage("Usuniemy połączenie z Discordem, ustawione obszary, wyniki, szkice i pozostałe lokalne dane. Aplikacja się zamknie; po ponownym uruchomieniu zaczniesz od początku. Dane zapisane w bocie na Discordzie pozostaną.")
            .setPositiveButton("Usuń wszystko") { _, _ ->
                scanner.closeScanner()
                scanner.mobile.disconnect()
                if (!getSystemService(android.app.ActivityManager::class.java).clearApplicationUserData()) {
                    refreshConnectionScreen()
                    showMessage("Nie usunięto danych", "Otwórz ustawienia Androida → Aplikacje → Botysław Skaner → Pamięć → Wyczyść dane.")
                }
            }.setNegativeButton("Anuluj", null).show()
    }

    private fun openOverlayPermission() {
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")), REQUEST_OVERLAY)
        } catch (_: Exception) {
            showMessage("Zezwól na nakładkę", "Otwórz ustawienia Androida i zezwól aplikacji Botysław Skaner na wyświetlanie nad innymi aplikacjami.")
        }
    }

    private fun startPermissionFlow() {
        if (!Settings.canDrawOverlays(this)) {
            requestedScreen = Screen.HOME
            renderScreen()
            return
        }
        if (CaptureConnection.state == CaptureState.READY) {
            scanner.prepareOverlaySession()
            scanner.overlay.show()
            openForgeMaster()
            return
        }
        if (CaptureConnection.state == CaptureState.STARTING) return
        permissionFlowPending = true
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        } else requestProjectionConsent()
    }

    private fun requestProjectionConsent() {
        if (!permissionFlowPending) return
        permissionFlowPending = false
        AlertDialog.Builder(this).setTitle("Odczyt ekranu gry")
            .setMessage("Po Twojej zgodzie skaner będzie odczytywał wybrany ekran, aby rozpoznawać zasoby w grze. Wybierz udostępnienie wyłącznie gry. Obrazy analizujemy na telefonie i nie wysyłamy ich do bota. Odczyt trwa do zamknięcia nakładki lub zatrzymania go w powiadomieniu Androida. Do Discorda wyślesz tylko sprawdzone liczby po osobnym zatwierdzeniu.")
            .setPositiveButton("Dalej") { _, _ -> launchProjectionConsent() }
            .setNegativeButton("Anuluj", null).show()
    }

    private fun launchProjectionConsent() {
        try {
            val manager = getSystemService(MediaProjectionManager::class.java)
            @Suppress("DEPRECATION")
            startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_PROJECTION)
        } catch (_: Exception) {
            showMessage("Nie można rozpocząć odczytu", "System nie udostępnił przechwytywania ekranu. Spróbuj ponownie.")
        }
    }

    @Deprecated("Native Activity result API is sufficient for this small application.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_OVERLAY -> renderScreen()
            REQUEST_PROJECTION -> {
                if (resultCode == RESULT_OK && data != null) {
                    try {
                        openGameWhenReady = true
                        FrameCaptureService.start(this, resultCode, data)
                    } catch (_: Exception) {
                        openGameWhenReady = false
                        showMessage("Nie można uruchomić skanera", "Spróbuj ponownie rozpocząć odczyt z tej aplikacji.")
                    }
                } else {
                    openGameWhenReady = false
                    toast("Nie rozpoczęto odczytu ekranu.")
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // Media projection may run even when the separate notification permission is denied.
        if (requestCode == REQUEST_NOTIFICATIONS) requestProjectionConsent()
    }

    private fun openForgeMaster() {
        val launch = packageManager.getLaunchIntentForPackage(FORGE_PACKAGE)
        if (launch == null) {
            scanner.overlay.minimize()
            showMessage("Otwórz Forge Master", "Otwórz grę ręcznie i naciśnij przycisk nakładki.")
            return
        }
        try {
            startActivity(launch)
        } catch (_: Exception) {
            scanner.overlay.minimize()
            showMessage("Otwórz grę ręcznie", "Uruchom Forge Master ze swojego ekranu głównego.")
        }
    }

    private fun renderSnapshot() {
        if (currentScreen != Screen.RESULTS) return
        val snapshot = scanner.session.snapshot()
        bindingValues = true
        try {
            fields.forEach { (field, input) ->
                val observation = snapshot.values[field]
                val desired = if (field in dirtyFields) drafts[field].orEmpty() else observation?.value?.toString().orEmpty()
                if (input.text.toString() != desired) input.setText(desired)
                fieldNotes[field]?.apply {
                    text = when {
                        field in dirtyFields -> "Do zatwierdzenia"
                        observation == null -> "Nie odczytano"
                        observation.approximate && (field == ResourceField.PETS || field == ResourceField.MOUNTS) -> "≈ ESTYMACJA listy — sprawdź przed wysłaniem."
                        observation.approximate -> "≈ Wartość ze skrótu gry; sprawdź końcowe cyfry."
                        observation.confirmed -> "Potwierdzone"
                        else -> "Odczyt do sprawdzenia"
                    }
                    setTextColor(if (observation?.confirmed == true && field !in dirtyFields) SUCCESS else MUTED)
                }
            }
        } finally {
            bindingValues = false
        }
    }

    private fun renderCaptureState(state: CaptureState, detail: String?) {
        captureStatus?.apply {
            text = when (state) {
                CaptureState.STOPPED -> "Nakładka jest wyłączona"
                CaptureState.STARTING -> "Uruchamianie…"
                CaptureState.READY -> "Nakładka działa"
                CaptureState.ERROR -> detail ?: "Odczyt zatrzymany. Uruchom ponownie."
            }
            setTextColor(if (state == CaptureState.READY) SUCCESS else MUTED)
        }
        startButton?.apply {
            text = if (state == CaptureState.READY) "Wróć do nakładki" else "Uruchom nakładkę"
            isEnabled = state != CaptureState.STARTING
        }
    }

    private fun consumeReviewIntent() {
        if (intent?.getBooleanExtra("review", false) == true) {
            intent.removeExtra("review")
            scanner.overlay.minimize()
            requestedScreen = Screen.RESULTS
        }
    }

    private fun showReview() {
        scanner.overlay.minimize()
        requestedScreen = Screen.RESULTS
        renderScreen()
    }

    private fun showHome() {
        rememberDraft()
        requestedScreen = Screen.HOME
        renderScreen()
    }

    private fun confirmDefaultCosts() {
        AlertDialog.Builder(this)
            .setTitle("Użyć domyślnych kosztów?")
            .setMessage("200 biletów za umiejętności x5 i 50 kluczy za wierzchowca. Jeśli masz obniżkę, wpisz własne koszty.")
            .setPositiveButton("Użyj 200 i 50") { _, _ ->
                fields.getValue(ResourceField.SKILL_COST).setText("200")
                fields.getValue(ResourceField.MOUNT_COST).setText("50")
            }
            .setNegativeButton("Wróć", null)
            .show()
    }

    private fun validatedValues(): Map<ResourceField, Long>? {
        val result = linkedMapOf<ResourceField, Long>()
        var firstInvalid: EditText? = null
        fields.forEach { (field, input) ->
            val raw = input.text.toString().trim()
            val value = GameNumberParser.parse(raw)?.wholeValueOrNull()
            val message = when {
                raw.isEmpty() -> "Wpisz wartość. 0 tylko wtedy, gdy nic nie masz."
                value == null -> "Wpisz całkowitą liczbę, np. 9880 lub 9,88k."
                field.isCost && value <= 0 -> "Koszt musi być większy od zera."
                else -> null
            }
            input.error = message
            if (message != null) {
                if (firstInvalid == null) firstInvalid = input
            } else result[field] = requireNotNull(value)
        }
        firstInvalid?.let {
            it.requestFocus()
            scroll.post { scroll.smoothScrollTo(0, (it.top - dp(40)).coerceAtLeast(0)) }
            toast("Sprawdź wszystkie 7 pól.")
            return null
        }
        return result
    }

    private fun confirmationSummary(values: Map<ResourceField, Long>): String {
        val snapshot = scanner.session.snapshot()
        return values.entries.joinToString("\n") { (field, value) ->
            val estimated = snapshot.values[field]?.let { it.value == value && it.approximate } == true
            "${field.label}: ${if (estimated) "≈ " else ""}$value${if (estimated) " (estymacja)" else ""}"
        }
    }

    private fun confirmCopy() {
        val values = validatedValues() ?: return
        val summary = confirmationSummary(values)
        AlertDialog.Builder(this)
            .setTitle("Potwierdź raport")
            .setMessage("Czy wartości są poprawne?\n\n$summary\n\nRaport trafi do schowka.")
            .setPositiveButton("Kopiuj i zakończ") { _, _ -> copyConfirmedValues(values) }
            .setNegativeButton("Popraw", null)
            .show()
    }

    private fun pairPhone() {
        if (scanner.demo) {
            scanner.mobile.pair("demo") { showMessage("Połączenie DEMO", it.getOrDefault("DEMO")) }; return
        }
        if (syncBusy) { toast("Poczekaj na zakończenie połączenia."); return }
        val input = EditText(this).apply { hint = "6 znaków z /mobile pair"; isSingleLine = true; filters = arrayOf(InputFilter.LengthFilter(64)) }
        AlertDialog.Builder(this).setTitle("Połącz z Discordem")
            .setMessage("Skopiowano /mobile pair i otwarto Discorda. Wklej komendę na swoim serwerze, potem wróć tutaj z prywatnym kodem. Dane będą wysyłane tylko po Twoim potwierdzeniu.")
            .setView(input).setPositiveButton("Połącz") { _, _ ->
                syncBusy = true
                toast("Łączenie…")
                scanner.mobile.pair(input.text.toString()) { result ->
                    syncBusy = false
                    if (!isFinishing && !isDestroyed) { refreshConnectionScreen(); showMessage("Połączenie", result.getOrElse { "Nie udało się połączyć: ${it.message}" }) }
                }
            }.setNegativeButton("Wróć", null).show()
        openDiscordForPairing()
    }

    private fun openDiscordForPairing() {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Komenda połączenia", "/mobile pair"))
        toast("Skopiowano /mobile pair. Wklej komendę na swoim serwerze i wróć z kodem.")
        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://discord.com/channels/@me"))
        runCatching { startActivity(Intent(intent).setPackage("com.discord")) }
            .onFailure { runCatching { startActivity(intent) }.onFailure { toast("Otwórz Discord i wklej /mobile pair") } }
    }

    private fun confirmSend() {
        if (syncBusy) { toast("Poczekaj na zakończenie wysyłania."); return }
        val values = validatedValues() ?: return
        val sessionStart = scanner.session.snapshot().startedAtMillis
        if (!scanner.mobile.paired) { pairPhone(); return }
        syncBusy = true
        toast(if (scanner.demo) "Symulacja wysyłania…" else "Wysyłanie…")
        scanner.mobile.send(values) { result ->
            syncBusy = false
            if (result.isSuccess && scanner.session.snapshot().startedAtMillis == sessionStart) {
                CaptureConnection.stop(); scanner.overlay.hide()
            }
            if (!isFinishing && !isDestroyed) {
                showMessage("Aktualizacja danych", result.getOrElse { "Nie wysłano: ${it.message}" })
            }
        }
    }

    private fun copyConfirmedValues(values: Map<ResourceField, Long>) {
        val previous = scanner.session.snapshot()
        values.forEach { (field, value) ->
            val observation = previous.values[field]
            val sameObservation = observation?.value == value
            scanner.session.setValue(field, value, confirmed = true,
                approximate = (sameObservation && observation?.approximate == true) ||
                    GameNumberParser.parse(fields.getValue(field).text.toString())?.abbreviated == true,
                sourceText = if (sameObservation) observation?.sourceText else null)
        }
        scanner.session.goTo(ScanStep.REVIEW)
        val snapshot = scanner.session.snapshot()
        if (!snapshot.readyToExport) {
            showMessage("Sprawdź dane", "Raport zawiera niepotwierdzone wartości. Sprawdź podsumowanie ponownie.")
            return
        }
        try {
            getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Forge Master · zasoby", if (scanner.demo) "DEMO — dane przykładowe, nie importuj do bota\n" + ClipboardReportFormatter.format(snapshot) else ClipboardReportFormatter.command(snapshot)))
        } catch (_: Exception) {
            showMessage("Nie skopiowano raportu", "Schowek jest obecnie niedostępny. Spróbuj ponownie.")
            return
        }
        drafts.clear()
        dirtyFields.clear()
        openGameWhenReady = false
        CaptureConnection.stop()
        scanner.overlay.hide()
        renderSnapshot()
        toast(if (scanner.demo) "Skopiowano przykład DEMO." else "Skopiowano /import. Wklej i uruchom komendę w Discordzie.")
    }

    private fun confirmNewScan() {
        AlertDialog.Builder(this)
            .setTitle("Nowe skanowanie?")
            .setMessage("Usuniemy bieżące wyniki. Zapisane obszary odczytu pozostaną.")
            .setPositiveButton("Nowe skanowanie") { _, _ -> resetScanAndHome() }
            .setNegativeButton("Anuluj", null)
            .show()
    }

    private fun resetScanAndHome() {
        rememberDraft()
        openGameWhenReady = false
        CaptureConnection.stop()
        scanner.startNewSession()
        drafts.clear()
        dirtyFields.clear()
        requestedScreen = Screen.HOME
        renderScreen()
    }

    private fun confirmClearCalibration() {
        AlertDialog.Builder(this)
            .setTitle("Usunąć ustawienia odczytu?")
            .setMessage("Przy kolejnym odczycie ponownie zaznaczysz miejsca liczników i list. Wyniki pozostaną.")
            .setPositiveButton("Usuń ustawienia") { _, _ ->
                scanner.calibrations.clear()
                scanner.overlay.resetSession()
                toast("Ustawienia odczytu usunięte.")
            }
            .setNegativeButton("Anuluj", null)
            .show()
    }

    private fun rememberDraft() {
        fields.forEach { (field, input) ->
            if (field in dirtyFields) drafts[field] = input.text.toString()
        }
    }

    private fun restoreDraft(saved: Bundle?) {
        saved ?: return
        if (saved.getBoolean("results")) requestedScreen = Screen.RESULTS
        permissionFlowPending = saved.getBoolean("permissionFlowPending")
        openGameWhenReady = saved.getBoolean("openGameWhenReady")
        if (saved.getLong("sessionStart") != scanner.session.snapshot().startedAtMillis) return
        ResourceField.entries.forEach { field ->
            saved.getString("draft_${field.name}")?.let {
                drafts[field] = it
                dirtyFields.add(field)
            }
        }
    }

    private fun text(value: String, size: Int, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size.toFloat()
        setTextColor(color)
        setLineSpacing(dp(3).toFloat(), 1f)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun action(label: String, stableId: Int, primary: Boolean = false, quiet: Boolean = false, onClick: () -> Unit) = Button(this).apply {
        id = stableId
        text = label
        contentDescription = label
        textSize = if (quiet) 13f else 15f
        isAllCaps = false
        gravity = Gravity.CENTER
        minHeight = dp(48)
        minimumHeight = dp(48)
        setPadding(dp(12), dp(12), dp(12), dp(12))
        setTextColor(if (primary) Color.WHITE else if (quiet) MUTED else FOREGROUND)
        background = rounded(if (primary) PRIMARY else if (quiet) BACKGROUND else INPUT_BACKGROUND, 12,
            if (primary) PRIMARY else if (quiet) BACKGROUND else BORDER)
        setOnClickListener { onClick() }
    }

    private fun <T : View> T.withTop(margin: Int): T = apply {
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(margin) }
    }

    private fun rounded(color: Int, radius: Int, stroke: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
        setStroke(dp(1), stroke)
    }

    private fun showMessage(title: String, message: String) {
        AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK", null).show()
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    companion object {
        const val ID_ALLOW_OVERLAY = 0x00f10001
        const val ID_START_OVERLAY = 0x00f10002
        const val ID_RESULTS = 0x00f10003
        const val ID_OPTIONS = 0x00f10004
        const val ID_COPY = 0x00f10005
        const val ID_SEND = 0x00f10008
        const val ID_RESET = 0x00f10009
        const val ID_BACK = 0x00f10006
        const val ID_DEFAULT_COSTS = 0x00f10007
        const val FIELD_ID_BASE = 0x00f10100
        private const val FORGE_PACKAGE = "com.hariwn.legendofcivilizations"
        private const val REQUEST_OVERLAY = 101
        private const val REQUEST_NOTIFICATIONS = 102
        private const val REQUEST_PROJECTION = 103
        private val BACKGROUND = Color.rgb(11, 17, 29)
        private val INPUT_BACKGROUND = Color.rgb(18, 28, 45)
        private val BORDER = Color.rgb(43, 58, 80)
        private val FOREGROUND = Color.rgb(237, 242, 251)
        private val MUTED = Color.rgb(158, 176, 200)
        private val ACCENT = Color.rgb(114, 216, 181)
        private val PRIMARY = Color.rgb(26, 107, 92)
        private val SUCCESS = Color.rgb(112, 221, 171)
    }
}
