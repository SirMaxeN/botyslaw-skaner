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
    private enum class Screen { PERMISSION, HOME, RESULTS, OPTIONS, HELP }

    private val scanner: ScannerApplication get() = application as ScannerApplication
    private val fields = linkedMapOf<ResourceField, EditText>()
    private val fieldNotes = linkedMapOf<ResourceField, TextView>()
    private val drafts = linkedMapOf<ResourceField, String>()
    private val dirtyFields = mutableSetOf<ResourceField>()
    private lateinit var scroll: ScrollView
    private var startButton: Button? = null
    private var manualStartButton: Button? = null
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
            scanner.showScannerOverlay()
            openForgeMaster()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = BACKGROUND
        window.navigationBarColor = BACKGROUND
        restoreDraft(savedInstanceState)
        if (intent.getBooleanExtra("home", false)) { requestedScreen = Screen.HOME; intent.removeExtra("home") }
        if(intent.getBooleanExtra("options",false)) { requestedScreen=Screen.OPTIONS;optionsFromHome=true;intent.removeExtra("options") }
        else if(savedInstanceState?.getBoolean("optionsFromHome")==true) optionsFromHome=true
        consumeReviewIntent()
        renderScreen()
    }

    /** Options opened from the main menu (HomeActivity): Back returns there, not to the resource screen. */
    private var optionsFromHome = false

    override fun onDestroy() {
        // An Options-only instance (opened from the main menu) must not stop a scan running elsewhere.
        if (isFinishing && !isChangingConfigurations && !optionsFromHome) scanner.closeScanner()
        super.onDestroy()
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
        if (intent.getBooleanExtra("home", false)) { requestedScreen = Screen.HOME; optionsFromHome = false; intent.removeExtra("home") }
        if (intent.getBooleanExtra("options", false)) { requestedScreen = Screen.OPTIONS; optionsFromHome = true; intent.removeExtra("options") }
        consumeReviewIntent()
        renderScreen()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        rememberDraft()
        outState.putLong("sessionStart", scanner.session.snapshot().startedAtMillis)
        outState.putBoolean("results", requestedScreen == Screen.RESULTS)
        outState.putString("screen", requestedScreen.name)
        outState.putBoolean("permissionFlowPending", permissionFlowPending)
        outState.putBoolean("openGameWhenReady", openGameWhenReady)
        outState.putBoolean("optionsFromHome", optionsFromHome)
        drafts.forEach { (field, value) -> outState.putString("draft_${field.name}", value) }
        super.onSaveInstanceState(outState)
    }

    @Deprecated("Native Activity back navigation is sufficient for this small application.")
    override fun onBackPressed() {
        when (currentScreen) {
            Screen.HELP -> showOptions()
            Screen.OPTIONS -> if (optionsFromHome) finish() else showHome()
            Screen.RESULTS -> showHome()
            else -> super.onBackPressed()
        }
    }

    private fun renderScreen() {
        // Review never needs screen capture. Preserve readings and pairing, but release
        // projection, gestures and both overlays even when review is opened/restored directly.
        if (requestedScreen == Screen.RESULTS) {
            openGameWhenReady = false
            scanner.closeScanner()
        }
        val sessionStart = scanner.session.snapshot().startedAtMillis
        if (renderedSessionStart != 0L && renderedSessionStart != sessionStart) {
            drafts.clear()
            dirtyFields.clear()
            fields.clear()
            fieldNotes.clear()
            currentScreen = null
        }
        renderedSessionStart = sessionStart
        val needsOverlay = requestedScreen == Screen.HOME || (requestedScreen == Screen.RESULTS && !scanner.demo)
        val desired = if (needsOverlay && !Settings.canDrawOverlays(this)) Screen.PERMISSION else requestedScreen
        if (currentScreen != desired) {
            rememberDraft()
            fields.clear()
            fieldNotes.clear()
            startButton = null
            manualStartButton = null
            captureStatus = null
            currentScreen = desired
            val content = createPage()
            when (desired) {
                Screen.PERMISSION -> buildPermissionScreen(content)
                Screen.HOME -> buildHomeScreen(content)
                Screen.RESULTS -> buildResultsScreen(content)
                Screen.OPTIONS -> buildOptionsScreen(content)
                Screen.HELP -> buildHelpScreen(content)
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
            setImageResource(eu.komars.forgescanner.R.drawable.menu_resources)
            contentDescription = "Botysław Skaner"
        }, LinearLayout.LayoutParams(dp(76), dp(76)).apply { bottomMargin = dp(18) })
        content.addView(text("Skaner zasobów", 28, FOREGROUND, bold = true))
        addConnectionCard(content)
        content.addView(text("Najpierw zezwól na wyświetlanie małego przycisku nad grą.", 16, MUTED).withTop(16))
        content.addView(action("Zezwól na wyświetlanie nad aplikacjami", ID_ALLOW_OVERLAY, primary = true) {
            openOverlayPermission()
        }.withTop(28))
        content.addView(action("Opcje", ID_OPTIONS, quiet = true) { showOptions() }.withTop(14))
    }

    private fun addConnectionCard(content: LinearLayout) {
        if (scanner.demo) content.addView(text("DEMO — dane nie są wysyłane", 17, ACCENT, bold = true).withTop(12))
        content.addView(text(if (scanner.mobile.paired) "✓ Połączono: ${scanner.mobile.name}" else "Połącz z Discordem", 17, SUCCESS, bold = true).withTop(20))
        content.addView(text(if (scanner.mobile.paired) "Gotowe do wysyłania zatwierdzonych skanów." else "Połącz konto, aby wysyłać zasoby prosto do bota.", 13, MUTED).withTop(5))
        if (!scanner.mobile.paired) content.addView(action("Połącz z Discordem", 0x00f10011, primary = true) { pairPhone() }.withTop(10))
    }

    private fun buildHomeScreen(content: LinearLayout) {
        content.addView(android.widget.ImageView(this).apply {
            setImageResource(eu.komars.forgescanner.R.drawable.menu_resources)
            contentDescription = "Botysław Skaner"
        }, LinearLayout.LayoutParams(dp(76), dp(76)).apply { bottomMargin = dp(18) })
        content.addView(text("Skaner zasobów", 28, FOREGROUND, bold = true))
        content.addView(text("Zatwierdź pola przy pierwszej konfiguracji. Kolejne skany korzystają z zapisanych ustawień.", 16, MUTED).withTop(14))
        content.addView(text("Odczyt ekranu gry", 15, FOREGROUND, bold = true).withTop(22))
        content.addView(text("Po uruchomieniu i zgodzie Androida skaner odczytuje wybrany ekran, aby policzyć zasoby. Wybierz wyłącznie grę. Obrazy są analizowane na telefonie i nie trafiają do serwera. Odczyt zakończysz krzyżykiem nakładki lub w powiadomieniu Androida. Do bota wysyłasz tylko sprawdzone liczby przyciskiem Wyślij.", 13, MUTED).apply { id = ID_CAPTURE_DISCLOSURE }.withTop(7))
        startButton = action("Automatyczny odczyt", ID_START_AUTOMATIC, primary = true) { startAutomaticMode() }
        content.addView(startButton!!.withTop(28))
        manualStartButton = action("Manualny odczyt", ID_START_OVERLAY) {
            scanner.automaticMode = false
            scanner.autoOverlay.hide()
            startPermissionFlow()
        }
        content.addView(manualStartButton!!.withTop(10))
        content.addView(action("Wyniki", ID_RESULTS) { showReview() }.withTop(12))
        captureStatus = text("", 13, MUTED).withTop(14).also(content::addView)
        content.addView(action("Menu główne", ID_OPTIONS) { startActivity(Intent(this,HomeActivity::class.java)) }.withTop(14))
        eu.komars.forgescanner.ui.SupportFooter.add(this,content)
    }

    private fun buildResultsScreen(content: LinearLayout) {
        if (scanner.automaticMode) scanner.overlay.hide() else scanner.overlay.minimize()
        scanner.autoOverlay.hide()
        content.addView(action("‹ Wróć", ID_BACK, quiet = true) { showHome() })
        content.addView(text("Sprawdź dane", 27, FOREGROUND, bold = true).withTop(18))
        if(scanner.session.snapshot().values.isNotEmpty()) content.addView(text("Ostatni zapis: "+java.text.DateFormat.getDateTimeInstance().format(java.util.Date(scanner.session.snapshot().updatedAtMillis)),12,MUTED).withTop(6))
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
        parent.addView(text(if (field.optional) "${field.label} (opcjonalnie, bez punktów)" else field.label, 14, FOREGROUND, bold = true).withTop(20))
        val input = EditText(this).apply {
            id = FIELD_ID_BASE + field.ordinal
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            isSingleLine = true
            textSize = 18f
            setTextColor(FOREGROUND)
            setHintTextColor(MUTED)
            hint = if (field.isCost) "Wpisz koszt" else if (field.optional) "puste = pomiń, np. 2,12m" else "np. 9880 lub 9,88k"
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
        rememberDraft()
        requestedScreen = Screen.OPTIONS
        renderScreen()
    }

    private fun buildOptionsScreen(content: LinearLayout) {
        content.addView(action("‹ Wróć", ID_BACK, quiet = true) { if (optionsFromHome) finish() else showHome() })
        content.addView(text("Opcje", 27, FOREGROUND, bold = true).withTop(18))
        content.addView(text("Wersja aplikacji: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", 13, MUTED).withTop(4))
        content.addView(action("Pomoc — jak używać skanera", ID_HELP) {
            requestedScreen = Screen.HELP; renderScreen()
        }.withTop(18))
        content.addView(action(if (scanner.demo) "Wyłącz tryb demonstracyjny" else "Tryb demonstracyjny", ID_DEMO) { toggleDemo() }.withTop(12))
        content.addView(text("Przykładowe wyniki i symulowane połączenie z Discordem. Dane demo pozostają na telefonie. Wyłączenie demo przywraca Twoje wcześniejsze wyniki i połączenie.", 13, MUTED).withTop(7))
        content.addView(action("Automatyczny odczyt", 0x00f10031) { startAutomaticMode() }.withTop(18))
        content.addView(action("Połącz z Discordem", 0x00f10011) { pairPhone() }.withTop(20))
        content.addView(action("Odłącz telefon lokalnie", 0x00f10018, quiet = true) {
            scanner.mobile.disconnect(); refreshConnectionScreen()
            toast("Usunięto połączenie z telefonu. Dostęp możesz też cofnąć przez /mobile revoke.")
        }.withTop(10))
        content.addView(action("Sprawdź aktualizacje", 0x00f10019) {
            startActivity(Intent(this, eu.komars.forgescanner.update.UpdateActivity::class.java).putExtra("manual", true))
        }.withTop(20))
        content.addView(action("Zatrzymaj nakładkę", 0x00f1001a, quiet = true) {
            openGameWhenReady = false; scanner.closeScanner(); showHome()
            toast("Nakładka zatrzymana.")
        }.withTop(10))
        content.addView(action("Nowe skanowanie", ID_RESET, quiet = true) { confirmNewScan() }.withTop(10))
        content.addView(action("Usuń zapisane ustawienia odczytu", 0x00f1001b, quiet = true) { confirmClearCalibration() }.withTop(10))
        content.addView(action("Usuń dane aplikacji", 0x00f1001c, quiet = true) { confirmEraseApplicationData() }.withTop(10))
        content.addView(action("Polityka prywatności", 0x00f1001d, quiet = true) {
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://komars.eu/botyslaw-skaner/privacy.html"))) }
                .onFailure { toast("Otwórz komars.eu/botyslaw-skaner/privacy.html") }
        }.withTop(20))
    }

    private fun buildHelpScreen(content: LinearLayout) {
        content.addView(action("‹ Opcje", ID_BACK, quiet = true) { showOptions() })
        content.addView(text("Pomoc", 27, FOREGROUND, bold = true).withTop(18))
        fun section(title: String, body: String) {
            content.addView(text(title, 17, FOREGROUND, bold = true).withTop(22))
            content.addView(text(body, 14, MUTED).withTop(7))
        }
        section("1. Automatyczny odczyt", "Zezwól na wyświetlanie nad grą. Na ekranie głównym wybierz Automatyczny odczyt. Za pierwszym razem zaakceptuj zgodę na sterowanie gestami i włącz usługę w ustawieniach Androida. W oknie udostępniania ekranu wybierz wyłącznie Forge Master. Zgoda systemowa jest potrzebna przy każdym nowym uruchomieniu odczytu.")
        section("2. Zatwierdź ustawienia", "Podczas konfiguracji skaner proponuje miejsca liczników i przycisków. Każde pole zobaczysz na ekranie: zatwierdź je lub popraw zaznaczenie. Listy wierzchowców oraz zwierzaków/jajek zaznaczasz ręcznie. Zapisane obszary i miejsca kliknięć służą do kolejnych skanów; poprawa jednego pola nie powtarza całej konfiguracji. Liczniki zaznaczaj razem z ikoną i całym paskiem, zostawiając zapas na większą liczbę; dla młotków wybierz dolny pasek kowadła.")
        section("3. Skanuj z zapisanych pól", "Zacznij na ekranie młotków i naciśnij Skanuj. Skaner korzysta z zapisanych obszarów, otwiera kolejne ekrany i przewija listy. Nie dotykaj gry w czasie działania. STOP zatrzymuje sekwencję, a Zakończ listę pozwala zakończyć liczenie ręcznie. Sprawdź wyniki przed wysłaniem.")
        section("Manualny odczyt", "To dotychczasowa nakładka z ręcznym przechodzeniem między ekranami. Wybierz Manualny odczyt, pokaż ekran wskazany w nakładce i naciśnij Ustaw, aby zaznaczyć licznik lub listę. Klucze i koszt oraz bilety i koszt x5 mają po dwa obszary. Po ustawieniu możesz używać Skanuj. Listę przewijaj od góry do dołu, obserwując licznik i podpowiedzi; na końcu przytrzymaj ją nieruchomo albo zakończ ręcznie. Ten tryb nie wymaga usługi sterowania gestami.")
        section("4. Sprawdź i wyślij", "W Wynikach sprawdź wszystkie 7 pól, w tym ostatni rząd listy i wartości oznaczone jako estymacja. Baza to 1 wierzchowiec lub 3 zwierzaki — odejmij je tylko wtedy, gdy są na zeskanowanej liście. Skopiuj komendę przygotowuje /import do Discorda. Wyślij dane do bota aktualizuje połączone konto. Aby połączyć konto, użyj Połącz z Discordem i kodu z /mobile pair. Skanowanie działa też bez parowania.")
        section("Sterowanie", "Przeciągnij nakładkę, aby ją przesunąć. Menu otwiera dodatkowe czynności, krzyżyk kończy odczyt. Wróć do aplikacji skanera pozwala wrócić do wyników. Opcje zawierają reset, zmianę ustawień i sprawdzanie aktualizacji.")
        section("Tryb demonstracyjny", "W Opcjach włączysz przykładowe wyniki. Parowanie i wysyłanie są symulowane lokalnie; dane demo nie trafiają do prawdziwego klanu. Tryb nie wymaga konta Discord ani zgody na nakładkę. Wyłączysz go również w Opcjach.")
        section("Prywatność", "Skaner analizuje ekran na telefonie. Nie wysyła nagrań ani obrazów. Synchronizacja przekazuje tylko sprawdzone liczby. Odczyt możesz zawsze zatrzymać krzyżykiem lub w powiadomieniu Androida.")
    }

    private fun toggleDemo() {
        scanner.setDemo(!scanner.demo)
        drafts.clear(); dirtyFields.clear(); fields.clear(); fieldNotes.clear()
        currentScreen = null; requestedScreen = if (scanner.demo) Screen.RESULTS else Screen.OPTIONS
        renderScreen()
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

    private fun startAutomaticMode() {
        if (scanner.demo) { toast("Wyłącz DEMO przed skanowaniem gry."); return }
        val preferences = getSharedPreferences("automation_consent_v1", MODE_PRIVATE)
        val gestures = eu.komars.forgescanner.automation.GestureConnection
        if (preferences.getBoolean("disclosed", false) && !gestures.isConnected && gestures.enabledInSettings(this)) {
            // Enabled but still binding (after an update or app start): wait briefly instead of asking again.
            toast("Łączę sterowanie gestami…")
            gestures.awaitConnection(this, connected = { if (!isFinishing) startAutomaticMode() }, missing = {
                if (!isFinishing) AlertDialog.Builder(this).setTitle("Sterowanie gestami nie odpowiada")
                    .setMessage("Usługa dostępności Botysława jest włączona, ale Android jej nie uruchomił. Wyłącz ją i włącz ponownie w ustawieniach dostępności, potem wróć tutaj.")
                    .setPositiveButton("Ustawienia") { _, _ -> startActivity(gestures.settingsIntent()) }.setNegativeButton("Anuluj", null).show()
            })
            return
        }
        if (!preferences.getBoolean("disclosed", false) || !gestures.isConnected) {
            AlertDialog.Builder(this).setTitle("Opcjonalne sterowanie gestami")
                .setMessage("Automatyczny skan używa usługi dostępności do naciskania wskazanych przez Ciebie przycisków nawigacji i przewijania list w Forge Master. Rozpoznaje aktywną aplikację, aby zatrzymać gesty po wyjściu z gry. Nie pobiera treści okien przez dostępność; obraz gry analizuje lokalnie po osobnej zgodzie na odczyt ekranu. Nie wysyła obrazów ani nie wykonuje przyzywania, łączenia lub zakupów. Każdy skan uruchamiasz przyciskiem; STOP zatrzymuje sekwencję po bieżącym krótkim geście. Po akceptacji włącz usługę Botysław: automatyczny skan w ustawieniach, wróć tutaj i uruchom ten tryb ponownie. Manualny odczyt działa bez tej zgody.")
                .setPositiveButton("Zgadzam się — ustawienia") { _, _ ->
                    preferences.edit().putBoolean("disclosed", true).apply()
                    val details = Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
                        .putExtra(Intent.EXTRA_COMPONENT_NAME, android.content.ComponentName(this, eu.komars.forgescanner.automation.GestureAccessService::class.java).flattenToString())
                    try { startActivity(details) }
                    catch (_: Exception) {
                        val component = android.content.ComponentName(this, eu.komars.forgescanner.automation.GestureAccessService::class.java).flattenToString()
                        startActivity(gestures.settingsIntent().putExtra(":settings:fragment_args_key", component)
                            .putExtra(":settings:show_fragment_args", Bundle().apply { putString(":settings:fragment_args_key", component) }))
                        toast("Wybierz Botysław: automatyczny skan w zainstalowanych aplikacjach.")
                    }
                }.setNegativeButton("Nie teraz", null).show()
            return
        }
        scanner.overlay.hide(); scanner.automaticMode = true
        startPermissionFlow()
    }

    private fun startPermissionFlow() {
        if (!Settings.canDrawOverlays(this)) {
            requestedScreen = Screen.HOME
            renderScreen()
            return
        }
        if (CaptureConnection.state == CaptureState.READY) {
            scanner.prepareOverlaySession()
            scanner.showScannerOverlay()
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
        // The home screen explains capture immediately before the explicit start action.
        // Android's own consent still precedes every new capture session.
        launchProjectionConsent()
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
            if (scanner.automaticMode) scanner.overlay.hide() else scanner.overlay.minimize()
        scanner.autoOverlay.hide()
            showMessage("Otwórz Forge Master", "Otwórz grę ręcznie i naciśnij przycisk nakładki.")
            return
        }
        try {
            startActivity(launch)
        } catch (_: Exception) {
            if (scanner.automaticMode) scanner.overlay.hide() else scanner.overlay.minimize()
        scanner.autoOverlay.hide()
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
        startButton?.isEnabled = state != CaptureState.STARTING
        manualStartButton?.isEnabled = state != CaptureState.STARTING
    }

    private fun consumeReviewIntent() {
        if (intent?.getBooleanExtra("review", false) == true) {
            intent.removeExtra("review")
            if (scanner.automaticMode) scanner.overlay.hide() else scanner.overlay.minimize()
        scanner.autoOverlay.hide()
            requestedScreen = Screen.RESULTS
        }
    }

    private fun showReview() {
        if (scanner.automaticMode) scanner.overlay.hide() else scanner.overlay.minimize()
        scanner.autoOverlay.hide()
        requestedScreen = Screen.RESULTS
        renderScreen()
    }

    private fun showHome() {
        optionsFromHome = false // the resource screen is shown here now: behave like a normal resource instance
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
            // Extras (coins, gems, shells, vials) may stay empty: they are optional and never scored.
            if (raw.isEmpty() && field.optional) { input.error = null; return@forEach }
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
            toast("Sprawdź wymagane pola (7) — dodatkowe zasoby mogą zostać puste.")
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
                if(!scanner.demo) eu.komars.forgescanner.model.ConfirmedCostMemory(this).remember(scanner.mobile.name.orEmpty(),values)
                scanner.closeScanner(); showHome()
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
            .setMessage("Przy kolejnym odczycie ponownie zatwierdzisz pola i miejsca kliknięć oraz zaznaczysz listy. Wyniki i połączenie z Discordem pozostaną.")
            .setPositiveButton("Usuń ustawienia") { _, _ ->
                scanner.calibrations.clear()
                scanner.autoOverlay.clearSettings()
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
        saved.getString("screen")?.let { name -> Screen.entries.firstOrNull { it.name == name }?.let { requestedScreen = it } }
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
        const val ID_START_AUTOMATIC = 0x00f10030
        const val ID_RESULTS = 0x00f10003
        const val ID_OPTIONS = 0x00f10004
        const val ID_COPY = 0x00f10005
        const val ID_SEND = 0x00f10008
        const val ID_RESET = 0x00f10009
        const val ID_BACK = 0x00f10006
        const val ID_DEFAULT_COSTS = 0x00f10007
        const val ID_DEMO = 0x00f10015
        const val ID_HELP = 0x00f10016
        const val ID_CAPTURE_DISCLOSURE = 0x00f10017
        const val FIELD_ID_BASE = 0x00f10100
        private const val FORGE_PACKAGE = "com.hariwn.legendofcivilizations"
        private const val REQUEST_OVERLAY = 101
        private const val REQUEST_NOTIFICATIONS = 102
        private const val REQUEST_PROJECTION = 103
        private val BACKGROUND = eu.komars.forgescanner.ui.ScannerAppearance.BACKGROUND
        private val INPUT_BACKGROUND = eu.komars.forgescanner.ui.ScannerAppearance.INPUT_BACKGROUND
        private val BORDER = eu.komars.forgescanner.ui.ScannerAppearance.BORDER
        private val FOREGROUND = eu.komars.forgescanner.ui.ScannerAppearance.FOREGROUND
        private val MUTED = eu.komars.forgescanner.ui.ScannerAppearance.MUTED
        private val ACCENT = eu.komars.forgescanner.ui.ScannerAppearance.ACCENT
        private val PRIMARY = eu.komars.forgescanner.ui.ScannerAppearance.PRIMARY
        private val SUCCESS = eu.komars.forgescanner.ui.ScannerAppearance.SUCCESS
    }
}
