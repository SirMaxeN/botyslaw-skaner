# Battle simulator — first device-test build

The separate `.autotest` package is labeled **Botysław WALKA Test**, with a purple crossed-swords icon. Only AUTO Test starts the battle screen; the production launcher and icon are unchanged. Nothing is published.

## Device flow

1. Allow overlay and explicitly enable gesture control for this test package.
2. Choose own-character scan and share Forge Master only. Begin on the forge screen.
3. Set fields: info i, player name, damage number including unit, health number including unit, bonus-list viewport, equipped weapon, complete weapon title, weapon-close button, profile-close button.
4. Each navigation selection performs one confirmed tap. After completing setup, the first scan starts automatically.
5. Review every field and the complete raw bonus list. Missing bonuses stay empty. Only explicit user confirmation can fill missing bonuses with zeros. Save the reviewed profile.
6. Choose opponent scan, manually open the opponent profile and return its bonus list to the top. Configure its separate fields. Its setup also starts a scan. Review and save.
7. Reuse saved profiles or rescan either side. Last scan time is visible. A failed/cancelled scan never replaces accepted data.

Bonus list reads use bounded overlapping scrolls, a maximum of 25 observations and two identical successive OCR comparisons to propose the bottom. This is a heuristic: repeated unreadable/clipped rows can produce an early stop. Mandatory review is required. Setup geometry must match the source capture dimensions. Weapon title `(dystans)` means ranged; unreadable/partial parentheses require manual type selection.

STOP revokes gestures and callbacks. The system capture notification can stop sharing. Touch shielding blocks unintended interaction outside the panel and the injected gesture corridor. Physical touches in that corridor cannot be distinguished from injected input. No fight button, purchases or equipment changes are automated.

## Experimental calculations and research (2026-11-01)

Sources inspected:
- https://forgeoptimizer.netlify.app/optimizer.js
- https://forgemaster.pro/stats/base-stats.html
- https://github.com/1vcian/fm/blob/main/src/components/Profile/StatsSummaryPanel.tsx
- https://github.com/1vcian/fm/blob/main/src/utils/statEngine.ts
- https://forge-master.fandom.com/wiki/Stats_and_Power (search index; older conflicting power formula)

The optimizer explicitly calculates `rawHit = baseDmg * (1 + totalDamage) * (1 + weaponTypeBonus)` and `effHp = baseHp * (1 + totalHealth)`. It does not establish that the in-game profile number is its `baseDmg` input. The current helper explicitly states in src/utils/statEngine.ts that excluding substats affects POWER ONLY, while Damage/Health readouts ALWAYS include them. This is stronger evidence against automatically multiplying the scanned sums again, although it is a community calculator rather than a direct game test. The default uses scanned sums as final static damage/health; a clearly labeled comparison option applies static percentages to test the alternative interpretation. Do not describe the prototype as having established actual game accuracy.

The independently written model uses attack-speed breakpoints, critical and double-hit rolls, continuous regeneration, lifesteal from damage after block, proportional block reduction and a 4-second melee approach delay. It runs 1000 simulations, with a 600-second stalemate guard, like the inspected optimizer. Actual bonus percentages are used directly, instead of converting them to ideal modifier-slot counts. Visible OCR caps override bonus totals and can be corrected during review. Regeneration uses the entered health basis, as in the inspected optimizer. No extra universal melee ×1.6 is added: the inspected raw-hit pipeline does not add one.

Known differences/limits: actual skill damage, cooldowns, reflect and extra effects are not modeled. Block is a damage reduction in this optimizer model, not a simulated chance event. Visible in-game caps are preferred over the optimizer's outdated slot-based cap constants. Weapon-specific base inclusion, health semantics, regeneration and real PvP time limits need in-game validation. War mode currently takes manual extra damage/health multipliers for each side; other war rules/bonuses are not established. It is a configurable prototype, not a finished war-rules engine.

To validate displayed sums, record before/after profile sums and bonus lists while changing one bonus without changing underlying flat stats, mastery, skin, pets or skills. If switching equipment changes base stats too, account for those differences or use matched-base items. Test general damage, weapon-type damage and health independently. A real-fight recording against a known opponent can then validate hit size, criticals, healing and approach time.

All profiles, unreviewed OCR drafts and geometry remain local. No new server synchronization is added. No screenshot is written to disk. Existing resource scanner remains available through a button.

## Validation

Run `tools/build-and-install-auto-test.ps1 -SkipInstall -NoWait` for unit tests, lint and APK generation; run `tools/install-auto-test.ps1` for an authorized device. The installer clears only its established calibration preference files, preserves pairing and unrelated data, and never updates the Google Play package. Manual gameplay tests are left to the user under AGENTS.md.


Build validation (2026-11-01): 305 unit tests, 280 passed, 25 optional tests skipped, 0 failures/errors. AUTO Test lint and APK assembly passed. Repository audit: 192 Git candidates, 0 findings. Installed through tools/install-auto-test.ps1 on the authorized USB device with calibration-only reset; launch command succeeded. No manual UI walkthrough or gameplay scan was performed. APK: releases/Botyslaw-WALKA-Test-1.apk.
## Update: automatic setup proposals and original appearance

The battle setup now proposes editable regions before confirmation, using the same ScreenSelectionWindow contract as resource scanning. Equipment grid outlines and spacing anchor weapon/profile regions; a unique black i with a light glyph on the equipment panel anchors opening the own profile. Header OCR refines name and numeric crops; the full bonus viewport excludes action-button rows. The existing CloseButtonDetector proposes red white-X controls. Item title OCR proposes a complete title when sufficiently distinct; ambiguous titles/layouts fall back to manual marking.

Every suggested region still requires confirmation or redrawing. A confirmed navigation region performs one bounded tap and advances setup to the next screen; proposing a region itself never taps. Setup navigation receives a fresh gesture grant at each user confirmation, so time spent reviewing fields does not consume the runtime scan grant. The first scan remains automatic after setup completes. Opponent setup begins on the profile manually opened by the user.

The battle activity uses the original scanner palette, rounded buttons, spacing, input styles and system-bar insets. Original MainActivity and battle screens share ScannerAppearance palette values. The floating panel follows the original compact automatic-scan design. Only the test launcher icon remains purple with crossed swords.

Battle calibration is now stored under battle_self/battle_opponent in scan_calibrations_v1. The established test installer therefore resets battle marking fields along with resource calibration, while preserving reviewed battle profiles, pairing and credentials. The obsolete battle_calibration_v1 file is not read or restored.

Optional private geometry regressions use BATTLE_SETUP_PROFILES (semicolon-separated files) and BATTLE_SETUP_HOME. Files remain in ignored .local/battle-setup-fixtures. Fixture encoding: big-endian 32-bit width and height followed by width*height ARGB integers. No user screenshots are committed. Tests cover modal/equipment anchors, viewport exclusion of action buttons, unique info-disk recognition, ambiguous/missing info rejection, and three provided profile screens plus the provided home crop. Their results establish stored-image geometry only, not live phone OCR/navigation accuracy.

Update validation (2026-11-01): 310 unit tests, 285 passed, 25 optional tests skipped, 0 failures/errors. All five battle setup geometry tests passed, including all four provided screens. AUTO Test lint and assembly passed. Repository audit: 196 candidates, 0 findings. Installed through the established calibration-only reset script; launch succeeded. Reviewed profiles and pairing preserved. No manual gameplay or UI walkthrough performed. APK: releases/Botyslaw-WALKA-Test-2.apk.
## Update: weapon panel semantics, clipped list and variable player names

Weapon recognition now uses the whole item-panel area above the damage row, including wrapped names and the ranged marker. Dismissing weapon details is a confirmed tap OUTSIDE that item panel. Only the profile itself uses the red X. Automatic item proposals require an unambiguous panel/damage row; otherwise the selector asks for manual confirmation of the upper area/outside point. No weapon-detail screenshot has been supplied, so only pure geometry regressions establish this proposal contract; live item-panel recognition still requires user testing.

The profile bonus viewport is capped to the visible clipped list strip, not the empty lower half of the profile. Gesture start points therefore remain inside the scrolling list. Repeated OCR with no newly observed bonus type no longer silently establishes the list end: the overlay asks the user to confirm that the whole list is visible or repair the field. Existing accepted profiles remain unchanged on cancellation.

Player-name OCR crops at the cyan marker after white nickname text, and strips trailing gender symbols from recognized text. Neither the nickname nor its length is hard-coded. Tests cover varied lengths, no marker, spaces, digits and Polish letters. OCR may still need review for unusual colored nickname characters or misread glyphs.

Correction validation (2026-11-01): 316 tests, 291 passed, 25 optional tests skipped, 0 errors/failures. Lint and APK assembly passed. Repository audit: 200 candidates, 0 findings. Test 3 installed using calibration-only reset; reviewed profiles and Discord pairing preserved. No manual UI walkthrough or gameplay scan performed. APK: releases/Botyslaw-WALKA-Test-3.apk.
## Update: compact weapon tooltip and screen-sharing disclosure

The supplied weapon screenshot shows a short wide tooltip, not a tall profile modal. The detector now recognizes a unique wide gray tooltip from its component bounds/fill, independently of the equipment grid behind it. Profile detection remains unchanged when no such tooltip is present. A regression uses the user's exact privately stored tooltip crop, checks its detected boundary, the full area above the known base-damage row, and an outside dismissal point. The screenshot remains in ignored .local/battle-setup-fixtures/weapon.argb and is supplied via BATTLE_SETUP_WEAPON. This is geometry verification, not live phone OCR/navigation verification.

The original resource home already explained screen capture, on-device image processing, explicit Android consent and stopping capture. The battle home now includes an equally visible disclosure before the scan actions, and its start dialog repeats the image-processing and stop information. STOP cancels scanning/gestures; Wróć or Zatrzymaj in the Android capture notification ends the capture session. Existing system consent remains required for a new capture session.

Test 4 validation (2026-11-01): all unit tests, AUTO Test lint and APK assembly passed, including the supplied compact weapon screenshot. Installed with tools/install-auto-test.ps1 and calibration-only reset. Profiles and pairing preserved. No manual gameplay walkthrough performed. APK: releases/Botyslaw-WALKA-Test-4.apk.
## Update: one overlay for normal battles, scan diagnostics and save fixes

The launcher now has one **Otwórz nakładkę walki** action. The overlay contains own scan, opponent scan, normal fight calculation, setup, saved-data editing, STOP and close. Scan review/save and calculation results are overlay dialogs; completing a scan no longer stops screen capture or opens the Activity. The war button is explicitly deferred/disabled until war-bonus scanning is implemented.

Own scans can close a recognized profile or weapon tooltip before returning to the forge route. Opponent scans require the player to open the desired opponent profile. Weapon dismissal is detected and tapped automatically, both in setup and runtime; it is no longer a user-confirmed setup field. Missing tooltip bounds fail without a blind tap. Weapon-title reads retry when unreadable, retain the OCR header in saved data, accept a clear ranged word even if punctuation is missing, and do not treat unrelated OCR parentheses from artwork as an unknown weapon type.

The user's saved own profile was inspected with explicit authorization: damage 37.1m, health 111m, existing bonuses and manual melee selection. No pairing files or credentials were read. The raw list contained -3.08% Umiejętność Czas / odnowienia, which the old parser missed. Cooldown is now recognized by its Polish label and signed percent; its stored magnitude is the reduction percentage. OCR fragments are reassembled by row coordinates so separate value/label blocks do not change the intended order.

Absent bonus types default to zero, and blank bonus inputs save as zero. The data-review checkbox and simulation confirmation checkbox are removed. User-entered zero corrections are preserved in schema 2 profiles; legacy cooldown zeros can be recovered from the saved raw list on review. HP and damage must still be positive, with errors attached to those fields. The full weapon OCR text is now retained when saving.

Manual cap entry is removed. Applicable caps are read automatically from the game list. Wiki documents Mira 80% and Tira 30%; these are fairy conversion limits, not evidence of unconditional caps for all builds. Known equipment substats total at most 60% block across 12 pieces. 100% block has not been verified as an attainable current build. Sources: https://forgemaster.pro/stats/substats.html and https://forgemaster.pro/live/now.html; the current helper's src/utils/fairies.ts confirms conditional fairy total-pool clamping.

A full-phone home regression exposed the bottom navigation strip being misclassified as a compact weapon tooltip. Tooltip proposals now require side margins; the edge-to-edge navigation strip is rejected. A unique black info disk can be proposed independently of equipment artwork, with white glyph/light-surround checks. Tests include the original full home screenshot reduced to a private geometry fixture, as well as the previous user screen crops. No new manual gameplay or UI walkthrough was performed.

Test 5 validation (2026-11-01): full unit tests, lint and APK assembly passed, including full-home info, compact tooltip, clipped-list, row-ordered OCR, signed cooldown, ranged-marker and default-zero regressions. Installed through the calibration-only reset script; saved profiles and pairing preserved. Full overlay UI/gameplay flow is left to user testing under AGENTS.md. APK: releases/Botyslaw-WALKA-Test-5.apk.
## Update: original overlay conventions, skins, shared profile setup and capture shutdown

Weapon taps now use the lower body of the configured slot (40% across, 72% down), away from the skin badge at the upper right. The supplied skin profile is included in the private profile regressions. Profile setup can be reused for opponents: after own setup, opponent scan fields are detected from the currently opened profile and its own position, with no repeated wizard. An explicit opponent setup remains in Menu as a repair option for unrecognized layouts. Shared weapon headers are detected again from the actual tooltip before reading; unknown shared tooltip headers stop instead of using wrong own-character coordinates.

Scan controls remain unavailable without a complete saved configuration. The initial state offers Ustaw pola, the idle state offers scans/calculation/Menu, and the running state displays a colored step header, the original hands-off warning and STOP. Data and closing actions are inside Menu. Normal field reads use the existing OverlayPlacement to keep the panel visible away from the crop.

STOP was audited in both battle and original automatic resource modes. Previously the resource STOP only cancelled the runner; it now closes the whole scanner/capture session. ScannerApplication.closeScanner cancels both overlays before stopping capture, and capture STOPPED/ERROR hides the battle overlay too. Finishing either scanner Activity and removing the task from Recents closes the scanner. On supported Android versions, hiding previously visible shared game content also ends capture; initial waiting for the selected game remains allowed. MediaProjection callback, ImageReader, cached source image, VirtualDisplay and notification cleanup follow the existing releaseCapture/finishSession path. Simply switching from the launcher to the game does not trigger Activity-finish cleanup.

These resource-mode changes are included in the separate test package's resource scanner; no Google Play update has been published. The test installer still resets exactly the established calibration preference files, now including the battle fields stored in scan_calibrations_v1, and verifies removal before launch. Saved profiles and Discord pairing remain intact. Runtime lifecycle/UI verification on a physical phone is left to the user; no manual gameplay walkthrough was performed.

Test 6 validation (2026-11-01): unit tests, lint and APK assembly passed, including the new skin profile fixture. Installed through tools/install-auto-test.ps1; calibration removal was verified and profiles/pairing preserved. Resource and battle STOP/close cleanup audited in shared capture code. Manual lifecycle/UI behavior remains for user verification. APK: releases/Botyslaw-WALKA-Test-6.apk.
## Update: scroll corridor/STOP placement and post-opponent fight choice

Gesture placement now accounts for the larger of the measured/current panel heights and a physical-pixel minimum gap. Before each tap/scroll, the panel is moved outside the padded gesture target, a bounded layout-settling delay is observed, and actual screen bounds are checked; one bounded re-placement is allowed if a collision remains. The scrolling corridor matches GestureConnection's 20px padding and is itself protected by an extra margin. Existing TouchShieldGeometry rejection stays intact. A regression demonstrates rejection with stale height/small gap and acceptance with conservative placement.

The main overlay no longer has a calculate button. A complete opponent scan is saved locally and opens a choice of Normal battle / War (in preparation). Selecting Normal starts the simulation immediately. Incomplete HP/damage/weapon type opens the overlay editor first, then the same choice. War remains deferred pending extra-bonus scanning. A recalculation action stays inside the result dialog.

Test 7 validation (2026-11-01): unit tests, gesture-space collision regression, lint and assembly passed. Installed using calibration-only reset; profiles and pairing preserved. No manual gameplay walkthrough performed. APK: releases/Botyslaw-WALKA-Test-7.apk.
## Update: list visibility, readable dialogs and progress text

While reading/scrolling bonuses, the protected area is now the union of the entire configured viewport and the gesture corridor, rather than only the narrow finger path. This prevents panel relocation from avoiding the gesture while still covering the rest of the list. Added regression contrasts narrow-corridor placement with whole-viewport placement.

Overlay data/choice/result dialogs now use an explicit dark ScannerDialogTheme and dark content/scroll/window backgrounds with readable foreground/accent colors. Runtime progress uses eight own-character stages and six opponent stages; internal return taps keep the weapon-header stage instead of resetting the counter. Preparation is shown without a step number. The internal 25-observation bound remains a loop/capture limit, but the UI now shows a plain automatic bonus-reading message instead of x/25. Setup completion uses a completion label rather than a step beyond the total.

### Test 8 — ponowne skany i czytelność nakładki
- Po skanie można przesuwać nakładkę za tytuł lub tekst stanu; podczas automatyzacji pozycję ustala skaner.
- Przed każdym odczytem bonusów skaner wraca na górę i dopiero zbiera listę w dół, z ograniczeniem liczby gestów.
- Nakładka omija cały widoczny obszar bonusów, także podczas przewijania. Ukryto techniczny licznik odczytów; numeracja kroków zależy od skanowanej strony.
- Wszystkie okna danych i symulacji mają jawny ciemny motyw oraz ciemne tło zawartości.
- Typ broni odczytywany z całego dymku, z rozpoznaniem „Obrażenia (dystansowy)” i wyłączeniem procentowych bonusów z tej decyzji.
- Tylko moja postać jest zapisywana. Przeciwnik i jego ewentualny szkic pozostają w pamięci do zamknięcia nakładki lub następnego skanu przeciwnika. Usunięto stare zapisane dane przeciwnika.
- Zapis własny zawierał 37m przy wcześniej widocznych 37.1m. Ramki liczb są proponowane ponownie dla aktualnego profilu, z większym marginesem poziomym. Surowy odczyt obrażeń i zdrowia zachowany w moim szkicu do diagnozy. Brak odczytu nadal wymaga poprawy zamiast zera.

Walidacja Test 8: 326 testów, 25 pominiętych testów opcjonalnych, 0 błędów; lintAutoTest i assembleAutoTest zakończone sukcesem. Kontrola repozytorium: 204 kandydatów, 0 uwag. Zainstalowano osobny pakiet AUTO Test przez instalator z resetem wyłącznie kalibracji; zapis własnej postaci zachowany. Zapisano releases/Botyslaw-WALKA-Test-8.apk. Testy ręczne gry pozostawione użytkownikowi.

### Test 9 — krótsze skanowanie bonusów
Powrót na górę wykonuje dokładnie cztery szybkie gesty, bez OCR i bez pętli szukającej identycznego odczytu. Przed pierwszym odczytem zachowana krótka pauza na zatrzymanie listy. Przewijanie w dół nadal zachowuje nakładanie się widocznych fragmentów (40%), ale gest skrócono z 450 do 320 ms, a pauzę z 650 do 250 ms. Wystarcza jeden powtórzony odczyt końca zamiast dwóch; limit awaryjny skrócono z 25 do 12 odczytów, po czym skan zgłasza problem bez nadpisania zapisanej postaci. STOP i ochrona obszaru gestu zachowane.

Test 9 obejmuje także wspólną ramkę prawego górnego bloku statystyk zamiast dwóch ciasnych ramek liczb. Parser przypisuje wartości do etykiet obrażeń i zdrowia, ignorując poziom kuźni. OCR skaluje blok 3×, przy niepełnym odczycie ponawia maksymalnie dwa razy w skali 4×. Niepodpisane lub niejednoznaczne wartości pozostają puste do poprawy. Odczyt źródłowy bloku jest widoczny w danych i zachowany przy zapisie mojej postaci. W oknie danych obu stron dostępna minimalizacja do przesuwanego przycisku; ten sam formularz i wpisane wartości są przywracane bez ponownego skanu. W trakcie podglądania gry główna nakładka jest schowana.

Walidacja końcowa Test 9: 331 testów (25 opcjonalnych pominiętych), 0 błędów; parser bloku statystyk 5/5; lintAutoTest i assembleAutoTest zakończone sukcesem. Kontrola repozytorium 206 kandydatów, 0 uwag. Instalacja osobnego AUTO Test przez tools/install-auto-test.ps1 zakończona sukcesem, reset kalibracji potwierdzony przez instalator. Własny profil porównany przed i po instalacji, identyczny. APK releases/Botyslaw-WALKA-Test-9.apk. Testy ręczne gry i minimalizacji pozostawiono użytkownikowi.

Widoczny przycisk Zakończ obok Menu po skanie wywołuje wspólne closeScanner: zamyka okna, anuluje gesty i kończy MediaProjection wraz z usunięciem powiadomienia usługi. Menu używa tej samej ścieżki. Zminimalizowane dane: przytrzymanie przycisku powrotu co najmniej 600 ms i puszczenie również kończy sesję; przeciąganie nadal przesuwa przycisk.

Zainstalowano poprawkę widocznego Zakończ po testach i lint (sukces), kalibracja zresetowana zgodnie z polityką. Analiza filmu użytkownika zapisana lokalnie w .local/battle-diagnostics/opponent-20261101; statystyki przeciwnika i analiza wrażliwości czasu podejścia w analysis.json.

### All-skills test build (2026-11-01)
All 18 active skills have exact level 1–100 active base-value tables fetched from https://forgemaster.pro/skills/index.html and each linked skill page. Source data retained in ignored .local/battle-diagnostics/skills-wiki.json. Passive ownership stats are not re-added to scanned profile sums. Active scale: general damage × skill damage × separately entered research mastery; additional research must not duplicate a bonus already included.
Scanner attempts each of the three skill detail tooltips after weapon inspection, deriving slot geometry from equipment anchors. Unknown name/level remains unresolved for manual correction from all 18 choices; review requires explicit selection or empty slot, valid levels and no duplicate skills. Own profile persists, opponent stays in memory. Old own profiles require loadout review before simulation; no invented empty skills.
PvP simulation uses 60 s and remaining-HP percentage, common maximum loadout health multiplier (1 + .5 pets + .5 skills + 2 mount), regeneration ticks each second using pre-PvP health. Sources: https://forgemaster.pro/pvp/battles.html and https://forgemaster.pro/stats/base-stats.html. Skills resolve independently of weapon approach; temporary flat buffs expire; drone fires during its active window. Cooldown reduction applies to cooldown, not active duration.
EXPLICIT UNVERIFIED ASSUMPTIONS: first cast .25 s, damage per multi-hit skill treated as total cast damage, drone shot interval defaults to 1 s (editable), melee approach defaults 4 s (editable), buff health boosts are multiplied by PvP factor and heal on cast, skills do not inherit weapon crit/double/block/lifesteal. Timed effects start cooldown after expiration per wiki interpretation. Fixed-step resolution .05 s. Wiki rules are community interpretations and PvP applicability to clan-profile duels still needs user gameplay validation. No war-bonus scan or war accuracy claim.

All-skills build validation: 337 tests, 25 optional skips, zero failures; lintAutoTest and assembleAutoTest successful; repository check 209 candidates, zero findings. Installed separate AUTO Test with calibration-only reset through approved installer; own profile compared before/after and identical. Artifact releases/Botyslaw-WALKA-Test-10.apk. User performs manual scan/UI/gameplay verification. Health skill scaling uses general HEALTH rather than general DAMAGE; time-limit comparison tolerance 1e-12.

Test 11: saved draft exposed a pet tooltip (Treant) in skill scan. Corrected small-icon pitch independently of equipment slots; own fixture targets checked against actual three skill icon centers. Three proposed editable skill rectangles added to calibration; runtime respects confirmed fields rather than overwriting them. Expanded recognition to displayed English and Polish catalog names. No manual gameplay scan conducted.

Final correction: profile row is SKILLS first, then pets, then fairy. Targets now use positions 0–2, not 3–5. Own screenshot regression explicitly checks centers 110/150/185 and that all skill rectangles end before the pet group. Calibration labels explain the left group.

Test 11 installed after passing unit/regression tests, lint and assembly; own fixture skill click regression executed, not skipped. Own saved profile compared before/after and unchanged. Calibration-only reset verified by tools/install-auto-test.ps1. APK releases/Botyslaw-WALKA-Test-11.apk. No manual gameplay scan run.

Test 12: learned user-confirmed Polish tooltip titles plus icon shape hash and average RGB; ambiguous artwork matches rejected. No screenshots persisted; first unseen translation/artwork may need manual selection. Learning occurs only after valid save. Tooltip handling remains click-outside like weapon details. Header missing values retry twice with larger scale and expanded crop on final attempt. Bonus completion compares recognized stat/value pairs to tolerate OCR noise in other text; bounded retry clears previous observations and rewinds to top, never retries by continuing at bottom. Flat attack range parsed in both orders and stored without percent; no unverified automatic conversion into approach speed. Opponent prep sets status immediately and repositions overlay away from fields rather than hiding it for serial OCR.

Test 12 validation: 339 tests, zero failures/errors, 25 optional skips; lint and APK build successful. Repository check: 210 candidates, zero findings. Installed through calibration-only reset installer; saved self profile compared before/after and unchanged. Artifact releases/Botyslaw-WALKA-Test-12.apk. UI and gameplay testing remains with the user.

Test 13: bundled all 18 original skill icon PNGs downloaded from https://forgemaster.pro/assets/icons/skills/ (linked from each skill wiki page). Alpha-masked multiscale/offset RGB artwork matching independent of rarity background and translated text. Conservative absolute-error and runner-up margin gates; OCR/manual correction remains for uncertain matches. Stampede guessed Polish display name removed. Recognized open opponent profile can reuse in-bounds shared calibration if a fresh field proposal fails. Unit regression uses actual saved own Stampede slot and downloaded reference icons.

Test 13 final validation: 342 tests, zero failures/errors, 25 optional skips. All 18 downloaded icon references and actual own Stampede regression executed successfully. Empty field rejected. Additional saved EvilMosquito screenshot recognized as same profile layout; Stampede visual match verified independently. Some real screenshot icon matches remain uncertain and intentionally fall back to OCR/manual correction. Conflicts between confident artwork and text require correction. Lint and APK build successful, repository check 230 candidates zero findings. Installed through calibration-only reset script; saved own profile unchanged before/after. Artifact releases/Botyslaw-WALKA-Test-13.apk. Manual gameplay scans left to user.

Test 14: transient OCR/frame/skill-window failures trigger up to two generation-cancelled whole-scan recoveries through normal screen preparation, clearing collected data when scan restarts; STOP/touch shield/calibration failures do not retry. Missing skill names/levels retry twice in the same tooltip at larger scale; unopened slots retry clicks twice. Level parser accepts comma/dot OCR punctuation and rejects ambiguous/out-of-range levels. Complete enemy scans proceed to battle choice via normal validation; incomplete scans show only missing fields. Result view hides profile stats/skill composition/model assumptions and uses default model rules.

Test 14 final validation: 344 tests, zero failures/errors, 25 optional skips; lintAutoTest and APK assembly successful. Repository check 232 candidates zero findings. Calibration-only installer successful; own profile compared before/after identical. Artifact releases/Botyslaw-WALKA-Test-14.apk. Whole-scan retries bounded at two and do not follow deliberate STOP, shield touch, gesture safety or invalid calibration. Manual gameplay/UI checks remain with user.

Test 15: main overlay collapse button shows a draggable 228dp icon bar for own scan, opponent scan, expand and end capture. Compact scanning retains stage/hands-off indicator and STOP; layout changes disabled while scanning to preserve gesture shield geometry. Compact buttons share setup gating. Overlay placement measures actual compact width. Opponent calibration is reused at matching capture dimensions; forced setup and changed dimensions still repropose. Installer reset clears both sets of fields as before.

Test 15 final scope: user explicitly authorized preserving only the last accepted opponent for repeat fights, superseding earlier ephemeral opponent policy. Failed/unfinished scans preserve the last accepted record; drafts remain memory-only. Repeat button in full overlay and ↻ icon in compact overlay reopen normal/war choice; accepted opponent survives overlay/app restart. Shared enemy fields reused unless dimensions differ or a recoverable read failure marks them for one fresh proposal pass; cached header no longer re-detected on each scan. Compact icon strip fits five controls plus drag handle in 228dp.

Test 15 final validation: unit/regression suite and lint passed, APK built; repository check 232 candidates zero findings. Calibration-only installer succeeded; own and any existing accepted opponent profile compared before/after unchanged. Manual opponent calibration also refreshes after a detected failure; failures expand the compact overlay to show the message. Artifact releases/Botyslaw-WALKA-Test-15.apk. User performs manual overlay/gameplay verification.

Test 16: compact icon buttons use explicit background and zero native button insets/minimums so 36dp controls do not clip glyphs. Repeat uses a tinted system drawable rather than a font glyph; long-press tooltip labels every icon. Enemy save callback refreshes button availability before opening battle choice, avoiding stale disabled repeat controls after first scan. Existing opponent preference confirmed present before change.

Test 16 validation: unit/regression suite, lint and APK assembly passed. Calibration-only installer succeeded; self and opponent profile compared before/after identical. Artifact releases/Botyslaw-WALKA-Test-16.apk. Manual compact-overlay UI verification left to user per repository policy.

### Test 17 — przygotowanie wojenne (local development; phone disconnected)
Root menu now owns Discord pairing, overlay/accessibility links, app settings and coffee support; resources retain automatic/manual choices, fights retain setup/simulator plus war list. All icon buttons use 44dp square centered controls. Resource scanners have compact icon bars, full STOP panels retained during automatic runs.
War workflow marks and stores navigation/research/flags/list viewport/close/OK in calibration-only preferences. Saved technology values read current k% before green next-level increment; rival-info field is requested only in the battle window. Sunday window is explicitly 01:00 Europe/Warsaw through Monday 01:00, matching user requirement even when the resource calendar resets at another summer hour. Screen must also show Day 6 before bonus scan. Stale own scan warning shown when opening Sunday simulator. Normal battle uses no war multipliers; war requires this day's research/rival data.
Roster scan rewinds until stable, collects names/power with overlap and validates against header member count; partial rosters never upload. Server authenticates existing paired device, validates date/freshness/rosters/bonuses, computes approximate power-based matching, locks one immutable report per Discord guild/day transactionally and allows exact request retry. Discord report button is time-gated; report paginates and suppresses mentions. Core matching maximizes unique assignments; excess eligible allies get explicitly shared main target plus at most three alternatives. Power heuristic is not presented as combat probability.
Original recording saved .local/war-preparation/war.mp4 and reviewed as frame overview. Manual gameplay and first-time marking remain unverified without the user's phone. Server changes prepared locally; no report submitted or live deployment performed in this iteration.


Test 17 local iterations: corrected a green next-level research increment being mistaken for the current bonus, capped scroll strokes to the established gesture safety budget, added a manual research-location fallback, guarded delayed HTTP callbacks by session generation, and deferred recycling the selection image until its last queued draw has finished. Preparation waits for the explicit “Start — jestem w kuźni” action before navigating, so sharing-screen permission or opening the app cannot accidentally start a tap on the wrong screen. Resource compact icon windows use the exact rounded pixel sum of their square children; the icon artwork is centered with equal insets.

Server artifact is prepared locally in the sibling bot repository (`deployment/release-rHTnrB`), without a live deployment. Before phone end-to-end report testing, deploy that backend: current APK uses the production API origin. The existing paired credentials/profile data should be preserved; use tools/install-auto-test.ps1 when the phone returns to reset only established calibration files. War marks live in scan_calibrations_v1 and are included in that reset.

Phone checklist for the user: root menu and original color palette; automatic/manual resource expand/minimize; first own setup; research damage/health current values ignoring green increments; both flag positions and common roster close; rival-info/OK; normal-vs-war choice only in the fixed Sunday window; complete 49/50 or 50/50 member scans across scrolling; one immutable Discord report with alternatives and clear shared targets; visible STOP and disappearance of Android screen-sharing notification after termination. Gameplay navigation and OCR on the physical phone remain unverified locally.

Mechanics reference: https://forgemaster.pro/clans/tech-tree.html describes current war research as war-only multiplicative bonuses (`+10000%` means ×101). The roster heuristic and experimental PvP timing model should not be described as verified game win rates.


Final local validation, Test 17: 350 Android test cases (325 passed, 25 optional image-fixture cases skipped, zero failures/errors), including all 18 skills under equal war research multipliers and a normal/war separation regression. The skipped cases are older optional resource-image fixtures, not the newly added war tests. Existing private battle geometry/icon fixtures were supplied. Bot validation: 77 tests passed, including actual private Discord command/component dispatch for ordinary members and 729 exhaustive small-roster comparisons. Both repository audits pass. War-list availability in the app is refreshed from the paired server rather than a device-wide sent-day flag, avoiding incorrect blocking when pairing to another guild. Exiting the new root activity closes the shared scanner as well.


### Test 18 — original home appearance and setup gate

Replaced the temporary flat launcher with the original scanner's 76dp icon, 28sp heading, shared palette and button variants, 24dp page spacing, proper system-bar insets and the complete original support copy with the quiet coffee action. Home shows Discord connection and missing overlay/accessibility/notification permissions before exposing either scanner. Completed setup exposes only the resource/battle choices plus Options and the support footer. Permission status refreshes on return from Android settings; pairing reuses the existing encrypted connection, private code flow and Discord launch. Screen-sharing consent still occurs when starting a scanner. No gameplay walkthrough was conducted.


### Test 19 — explicit configuration start and completed-fight repeat

The unconfigured battle launcher is named Konfiguracja. It opens the game and idle overlay without starting field selection; only Ustaw pole explicitly begins the wizard. Before calibration, scan controls and repeat are hidden; full controls are Ustaw pole, Zwiń, Zakończ, Menu. Compact mode offers field setup, expansion and termination, with width matching visible icon count. Repeat is hidden in full and compact layouts until a simulation successfully completes and its result is displayed. Saving a newly scanned opponent invalidates that repeat eligibility. Older stored opponents without proof of a completed simulation do not expose repeat. Existing first mandatory own scan after confirmed calibration is preserved.


Test 19 follow-up: configuration checkpoints persist after every confirmed field in the calibration-only store. Cancellation exposes Kontynuuj and replays only the necessary profile/weapon navigation from the detected start screen to the first missing field. A complete setup or explicit Ustaw od nowa starts fresh. War preparation now shares battle step colors, headings, hands-off warning and selection accent. The research opener uses AutoScanStore.OPEN_RESEARCH, falling back to the existing GameUiDetector proposal rather than an independently guessed coordinate. Bounded main-screen return detects red close controls and waits through unrecognized transitions; it no longer asks the user to return to the incorrectly named forge screen. Preparation has six stages; a full roster report has eight. Gameplay remains for user verification.


### Test 20 — technology categories, recovery and setup before scanning

Reviewed the user's latest phone screenshot (Screenshot_20261101_204446_Forge Master.jpg), kept privately under .local/war-preparation/intermediate-screen.jpg. Technology opens a category grid, not the clan tree itself: select the lower-right red-flag clan tile (whose caption is the clan name) before seeking the war nodes. Technology tab now derives from the same detected three-tab strip as skills/pets; an ambiguous strip requests manual selection rather than trusting the old independent saved coordinate. Research preparation displays a warning before analysis begins. Failed preparation offers Ponów and Wskaż badania ręcznie; manual scrolling precedes a separate Dalej action for marking.

Initial battle setup now proceeds through profile fields and all available research/war geometry before the first profile scan. Configuration mode marks the shared roster viewport/close without scanning both full rosters or submitting a report. Rival-info/OK fields are required only during the requested Day-6 time window. The public setup gate includes these war fields, so a completed profile wizard alone does not expose scan/repeat actions. Completed full setup returns to the main game screen before the first own scan. Existing reviewed profiles, pairing and live server reports are unchanged.


### Test 21 — supplied research screenshots and shared research entry

Downloaded the two explicitly requested screenshots, 20261101_205524 and 205508, into private .local/war-preparation files. They show recovery actions accumulating and a fixed damage-node rectangle to the left of the actual icon. Recovery controls are now replaced, never appended repeatedly; manual selection immediately asks for one horizontal area enclosing the sword/heart pair and derives each half, with no extra Dalej buttons. Automatic proposals use adjacent red flags on blue research discs and refresh stale centers when the observed nodes move. A regression runs against a private pixel fixture extracted from the user's screenshot, plus a synthetic green-node decoy case.

Missing shared OPEN_RESEARCH navigation is configured from the battle workflow itself. It proposes the original detector's research button, asks for confirmation, saves the source checkpoint in AutoScanStore, and records the destination only after recognizing the resulting research screen. Existing verified resource navigation is reused. Root resource and battle buttons now both use the primary variant and equal dimensions. No manual gameplay run was performed.


### Test 22 — dialog-relative fields and repairable clan/war navigation

Reviewed three requested screenshots (210526, 210410, 210355), kept only under private .local/war-preparation. The description rectangle was above the actual dialog; its close proposal pointed at the bottom main menu. Added a white-dialog geometry proposal: shared description is relative to the detected panel and limited to its upper content, while close detection is constrained around that dialog's lower center. Both research nodes reuse these shared fields unless their detected centers move outside the proposed layout. A private screenshot regression verifies the popup X rather than the separate main-menu X; a translated-dialog regression checks description placement and donation exclusion.

Research failures offer correction of description/close without redoing nodes. Clan/war failures offer Popraw przyciski klanu i wojny, invalidate only the relevant navigation/flag coordinates, re-arm gestures, return to the main screen and resume marking. War overlay now uses the same physical full-screen window origin as the battle overlay/touch shield and verifies actual rendered bounds against the protected region before opening gestures. Existing touch-shield rejection remains enabled; no unsafe bypass was added. Manual gameplay remains user-tested.


### Test 23 — verified proposals instead of preset rectangles

Reviewed latest requested screenshots 211407 (war tab), 211546 and 211537 (war flags), retained under private .local/war-preparation. The former fallback used approximate normalized coordinates and placed frames below the actual tab and above banner bodies. Removed that fallback from field selection: initial rectangles now come only from observed proposals; an uncertain field starts blank with explicit manual-marking instructions. Technology always passes through confirmation on first configuration, including when its tab is recognized, and is part of full-configuration readiness.

Added pure component geometry for red/orange banner bodies, excluding header tint, notification dots and yellow poles; the same primitives propose the main clan entry and the clan technology category. War tab uses exact recognized OCR elements (Wojna/War) in the lower-right footer, rejecting ambiguity. Existing saved fields are reused only when their centers remain compatible with a fresh proposal. The runtime clock's existing Polish Sunday window remains authoritative for Day-6-only controls; failure to OCR the caption no longer blocks valid Sunday setup. Two visual flags or explicit user marking establish the war-screen fields, rather than relying on an OCR numeral.

Regression coverage includes the actual supplied flag screenshot, synthetic header/notification decoys, clan entry and recognized-footer text geometry/ambiguity. Existing profile/node/popup regressions are included. No manual gameplay run was performed.


### Test 24 — Astra audit and verified transitions

User explicitly requested an independent Astra audit and authorized physical phone navigation. Astra reproduced the installed detector returning HOME on the real war screenshot while the unrestricted X detector simultaneously found the final clan close. Also confirmed missing bonus/list detectors and absence of post-tap checks. The original resource classifier shared the HOME false-positive risk.

Introduced a source-checked, bounded VerifiedWarTransition runner (Astra implementation, 16 deterministic tests), one tap maximum, two destination observations, guarded cancellation/duplicate callbacks. War integration uses modal-first observed states and positive source/target checks; configuration readiness now validates geometry and successful transition records. Both clan modal views are verified before setup completion using the shared viewport/X. Added actual rival-info disk, white bonus/blue OK, light roster viewport and constrained modal-close proposals. Removed the disguised expected-X fallback. Recovery re-arms gestures and invalidates failed shared research navigation.

HOME now vetoes unrestricted visible X, and battle navigation requires an independent equipment-row/info proof. First own scan has its own two-observation gate; final war close is verified before scanning. Strict equipment detection preserves original proposal behavior while fixing gold fills and overlapping artwork only for the strict home proof. Shared resource action guards remain used where applicable; technology's blanket resource-only content ban is replaced by typed war node/modal checks and OCR action exclusions for legitimate research navigation.

Private real-frame regressions cover war neverHOME, actual equipment HOME, own/enemy roster viewport+X, bonus text+OK, info disk and clan-node grid. Full original resource/profile regressions and all transition tests pass after two image-driven corrections (small-letter decoys beside info disk; strict gold-card/overlap handling). No sample Discord war report was sent.

### Additional recording regression (2026-11-02)
Downloaded the user-selected 2026-11-01 22:43 recording to private `.local/war-preparation/war-latest.mp4` (71.38 seconds). Contact sheet confirms research and both clan lists, including multiple scroll positions. `WarSceneRegressionTest.latestRecordingScrolledRostersKeepViewportAndClose` checks nine frames at 20–65 seconds for the roster viewport, actual close control, and rejection of HOME. These are image regression checks; they do not replace a live Day 6 end-to-end test or validate OCR of every member. No weekly Discord report was submitted during validation.

## Test 25 — responsive war configuration
- Move scene analysis off the UI thread; restore the status/STOP overlay as soon as a clean frame is captured, before analysis begins. Clean captures still briefly hide the overlay.
- Keep full OCR action exclusions before navigation taps; use geometry for post-tap confirmations except the clan page requiring its footer label. Two independent destination observations remain required.
- Reuse geometry instead of repeatedly reducing the image; remove per-pixel temporary arrays in connected-component analysis.
- Generalize saturated banner colours with ambiguity rejection, preserve a text-based war-page fallback for manually selected banners. No member name or ordering is a scene-recognition template.
- Broaden the roster viewport vertical bounds, regression-test the actual failed 2026-11-02 phone screen and nine scrolled recording frames. Add eight scaled/aspect-ratio image cases; these are not physical-device compatibility certification.
- Reuse compact configuration actions and selection appearance across battle/research/war. Reduce repeated top-of-roster checks (two stable comparisons to one; bounded 12 instead of 20 attempts). Finger down returns toward the beginning; finger up reveals following players.
- Calibration continues to be per captured-image dimensions. A size change requires recalibration. No weekly Discord report was generated during these checks.


## Test 26 — reading feedback and UI continuity
- Skill tooltip OCR retries the original image and three inverted threshold contrasts on fresh frames; unreadable levels remain manual fields, never invented levels.
- Compact battle row disables baseline alignment and removes the left-aligned repeat drawable. Row/window sizing uses matching pixel rounding. Profile editor constrains its scrolling body and stacks its action buttons.
- Battle menu refreshes on resume. Return to app stops capture; resource upload success returns to resource mode selection. Resources expose compact mode directly and include a scan/configure action in the compact bar.
- A small non-touchable indicator stays in the upper-right corner while capture is READY, including brief clean-frame captures. That reserved area is black-masked before full-frame recognition. The indicator disappears on stop/error; it is capture-session feedback, not a network/upload indicator.
- Existing clear panel positions are retained for resource runtime, battle reads and war gestures.
- Battle screen shows capture disclosure inline, then opens Android consent directly. Home/resources/battle share SupportFooter.
- Confirmed resource costs are remembered locally only after a successful non-demo upload, for the paired display name, for up to seven days. History can resolve a current OCR candidate at confidence >= .75, but cannot supply a missing candidate or override a different candidate >= .95. Regression tests cover these restrictions. This is not persistence of every unfinished resource scan.
- Research search checks the current view, returns toward the top until the sampled viewport stops changing (bounded), then finds the sword/heart nodes downward. Previously configured node coordinates are refreshed from current recognition and still checked before tapping.
- No real Discord report is submitted by this validation. Full OCR on difficult live tooltips and the final phone UI remain physical-device checks.

## Test 28
- Konfiguracja wszystkich pól pomija odczyt procentów badań i premii rywala ; po zakończeniu konfiguracji rozpoczyna pierwszy skan postaci zgodnie z doprecyzowaniem użytkownika.
- Wejście do badań zawsze najpierw wraca na górę; dopiero potwierdzony brak dalszego ruchu rozpoczyna wyszukiwanie ikon w dół. Limit powrotu kończy się błędem zamiast uznawać dowolne miejsce za początek.
- Wskaźnik przechwytywania to okienko Bot skanuje; maskowany jest jego prostokątny obszar.
- Rozpoznane okna modalne nie wymagają dodatkowego pełnego OCR tekstu do potwierdzania nawigacji. Nadal stosowane są kontrole ekranów i przejść; nie wprowadzono bezwarunkowej sekwencji kliknięć.

## Test 29 — wskaźnik i rozpoczęcie konfiguracji
- Napis jest sterowany stanem automatycznej pracy, a nie samym udostępnianiem ekranu. Znika w stanie oczekiwania, przy ręcznym zaznaczaniu oraz po błędzie/STOP.
- Podczas pobierania klatek napis pozostaje widoczny. Ignorowany prostokąt pochodzi z rzeczywistego położenia i wymiarów okienka po ułożeniu przez Androida, w fizycznych wymiarach ekranu.
- Potwierdzony główny ekran ma pierwszeństwo przed heurystyczną propozycją dymku szczegółów, aby przygotowanie nie próbowało zamykać nieistniejącego okna kliknięciem w wyposażenie.
- Komunikat błędu rozpoczęcia konfiguracji nie jest nadpisywany tekstem stanu oczekiwania.

## Test 31 — potwierdzona przyczyna odrzucania ekranu głównego
Rzeczywista odrzucona klatka z cache wersji testowej została przeanalizowana natywnie na telefonie. Przy 360x780 Android zwracał `close=PixelRegion(23,668,38,683)`: był to czerwony licznik 99 przy czacie, nie zamknięcie X. Układ wyposażenia oraz i były już poprawnie wykrywane. Lokalny resampler nie odtwarzał tego aliasingu, dlatego poprzednie testy screenshotów nie wykrywały regresji.

CloseButtonPixelDetector wymaga teraz, aby biały X był jednym połączonym glifem. Dwie osobne cyfry nie spełniają warunku. Natywne przeliczenie identycznej odrzuconej klatki po poprawce daje HOME=true i close=null przy szerokości 360 oraz 480, z filtrowaniem i bez. Dokładne piksele natywnego wariantu 360 zapisano prywatnie jako scene-99.argb; test nativeAndroidChat99IsNotAModalClose sprawdza brak fałszywego X oraz pozytywne rozpoznanie HOME. DEBUG zapisuje jedną klatkę niepowodzenia w cache, bez wysyłania jej do serwera.

## Test 32 — research scrolling and banner identity

Research search now starts with exactly three long downward finger gestures (content returns to the top), then inspects each page and scrolls downward with overlap. Rewind no longer captures and compares up to 20 frames. Gestures stay in the research content, above the fixed navigation tabs.

Reference: private user screenshots 2026-11-02 19:50:05 and 19:50:37. Target nodes are blue discs with the hanging red banner and horizontal gold-capped pole: sword on the left, heart on the right (92/100 and 77/100 in this example; levels are not identity). Pennants on vertical poles and potion banners must not match. Detector now requires the horizontal gold caps as well as paired red/blue geometry. The screenshot regression checks both acceptance and rejection. These screenshots remain local, outside released APKs.

## Test 33 — central short research gestures

User screenshot 2026-11-02 20:02:10 showed GestureSafety rejection. Test 32 requested a 38.5%-of-screen stroke, exceeding the existing 35% limit. Replaced research gestures with six 260 ms strokes between 40% and 60% screen height, at horizontal center. Search uses the reverse stroke and up to 12 advances, inspecting each viewport. Only the gesture corridor is cleared of overlays. Clan roster scrolling is unchanged. Added a regression running research strokes through the actual GestureSafety validator at four phone sizes, checking direction and center alignment.

## Test 34 — stop research motion before selecting

The 20:08:50 screenshot showed the proposal below the actual sword node: detection and selection used different frames while the list was still moving. Research swipes now retain the same touch and finish with GestureConnection.endHold (180 ms stationary release), without injecting any separate braking tap. A bounded motion gate requires four stable content samples before searching. Research selection proposals are also recomputed from the exact bitmap displayed in ScreenSelectionWindow, so an earlier coordinate cannot be drawn over later content. The motion-gate regression checks that movement resets the stable evidence.

## Test 35 — research pacing, icon states, menu and resource isolation

- Rewind uses six central strokes spanning 32% of height, with stationary release only on the last stroke; search still uses overlapping 20% strokes. Motion sampling stops after two stable comparisons or a real 2-second deadline.
- War research supports blue and maxed green discs. Horizontal gold banner caps plus sword blade/pommel and heart evidence distinguish target nodes from resource bag banners. Real-image tests also recolor blue discs to green and assert matching/rejection.
- Original vector illustrations (resource crate/crystal and shield/swords) share a cyan/gold frame, used on main menu buttons and section headers.
- Saved war routes skip redundant text recognition and require one destination observation instead of two. First configuration retains full verification. Wrong-source regression remains active.
- Resources now request fresh frames after removing the panel, report busy state while proposing fields, and use a resource-only second-resolution proposal fallback. The geometry default for other modules remains unchanged.
- Resource failure was not reproduced from the user's selector screenshot or the earlier native Android home frame: both yield hammers/mount/research fields. Do NOT claim the live regression resolved until retested. Debug-only private cache resource-rejected.png/argb preserves the latest rejected input if it recurs.
- check_repository.py now enforces that shared capture/geometry and resource proposal modules cannot import battle policies. Full tests cover both modules; this reduces regression risk, it cannot guarantee zero regressions.

## Test 39 — saved-route speed and seasonal stats

Previously verified war/research navigation follows saved taps directly; subsequent field recognition/read provides the checkpoint and existing failure controls provide recovery. Damage/health nodes and rival info retain source checking. First configuration is still verified. Research motion polling runs every 120 ms without a 1-second slowdown, and top detection needs one unchanged comparison across gestures (after the initial three strokes).

Game update research: https://forgemaster.pro/start/game-updates.html (2.11.0, 27 Oct) announces next-season fairies, Incoming Critical Damage Reduction and Damage on every 5th Attack. https://forgemaster.pro/cosmetics/fairies.html states conversion rules vary per season but does not currently publish new coefficients. Added independent parsed/persisted seasonal bonus keys (including reflect), preventing critical reduction from overwriting critical damage. Simulations with these nonzero bonuses explicitly report unsupported mechanics instead of silently ignoring them. Full new-fairy simulation is PENDING verified current descriptions/rules; do not claim it complete.

## Test 41 — current-season fairy profile

User screenshots 2026-11-03 14:56 and 14:57 confirm season-specific Tira/Mira/Lora artwork and profile fairy slot with level. Current UI read-only capture confirms Tira level 3 has 6.5% damage per 10% equipment health (+0.5 percentage points per level); user confirms Lora level 3 has +50% every fifth attack (base 30%, +10 points/level). Profile scan now samples the separate fairy slot, uses color evidence for type and retries level OCR in contrast variants, saving the metadata with reviewed profiles. Unknown OCR remains absent. Type/color recognition needs physical-device testing across other profiles.

Pvp engine supports current Lora bonus on every fifth weapon attack event (both hits of a double share the event; interaction is an explicit assumption pending combat verification) and uses scanned incoming critical reduction, capped at 70%, on critical hits. Tira's static effect is already in displayed total damage and is not added again. Explicit fifth-hit stat takes precedence over fairy-derived amount to avoid duplication. Reflect remains unsupported when nonzero. Mira conversion is not reconstructed from total cooldown (equipment-only source differs); scanned final reduction is used. These limitations supersede the earlier blanket seasonal-block note.

Test 38 was built but not installed; Test 39/40 were intermediate builds. Final intended install: Test 41.

## Test 44 — review visibility, fairy calibration and test war list

Resource ambiguity dialogs now offer Peek game and a small Return to choice overlay, preserving the same dialog/checked option. Both the dialog and large scanner panel hide during the peek, and touch shielding is removed so the game can be inspected. New fairy+level calibration step uses the existing profile-relative proposal and stores the marked area; scanning uses this calibration.

Research braking finds an entirely white patch at the outer margin (40–65% vertical region), never a fixed assumed button coordinate. After the first three fast rewind strokes, the bot taps that blank point, then verifies motion; absence of a verified blank patch keeps passive motion waiting. The pure regression rejects colored controls and text inside the candidate patch.

AUTO Test-only Test war list can run any day, follows research/clan/roster navigation, skips rival technology modal, assumes enemy bonuses equal our scanned bonuses, and sends to the separate authenticated war-test endpoint. It never writes uploadedDay or calls production weekly-lock status. Server stores test under TEST key, permits replacement, force-copies own bonus, retains complete-roster validation and existing power matching. Discord /report war-test displays clearly marked test report with independent pagination. Real war report/lock stays separate.

Server tests: 78 passed, repository audit clean. External deployment was denied by automatic approval review pending explicit user approval to update server code and register Discord commands. Do not claim report is live until deployment succeeds. Test 42/43 were build iterations; final intended phone install is Test 44.

## Test 46 — stage-local recovery and saved-route speed

Continue no longer calls start(): each current operation stores a retry closure. Repair current field clears only that key and invokes the current stage; research read failure stays in the same modal and keeps unrelated geometry, scanned damage/health, and accumulated rosters. Research OCR retries original plus two thresholds at 3x scale, retaining crop on failure. Saved science destination, technology tab, clan category, clan entry, war tab, flags and main close bypass repeated screen/text discovery. Freshly detected moving research nodes preserve the verified mark instead of forcing full transition verification again. Search down uses overlapping 28%-height strokes without duplicate stationary hold plus braking tap. Read reliability on the physical device remains to be tested; no new rejected research screenshot was available.

Test 45 passed as an intermediate build; final intended install is Test 46. Discord test report deployment remains pending the explicit approval requested after auto-review rejection; no server deployment attempted in this turn.

## Test 48 — explicit start, research parser and fairy detail read

App entry points for war preparation/list/test now show an idle overlay with a Start button; no navigation, status check or scan begins before it. Resources no longer auto-configure the next field simply on show(). Battle app already opened an idle overlay. Internally continuing user-started configuration remains automatic.

Research percentage parsing now allows OCR whitespace after +, a missing +, decimal comma and unit k. It requires the correct war-node identity and one unambiguous percentage outside parentheses, rejecting an upgrade-only or conflicting read. New regression covers these cases. Debug builds keep last rejected research OCR text privately in cache/research-rejected.txt for actual-device diagnosis. The provided failure screenshot confirmed the correct modal was open but did not expose OCR text; the live failure is not yet proven resolved.

Fairy scanning now opens the marked icon after reading its level, reads actual detail text/name/effect, and dismisses a compact tooltip outside it or uses the detected X for a full dialog. The underlying profile X is not used for compact tooltips. Review displays type/level/effect and permits level correction. Profile final stat rows take priority over detail effect values; absent fifth-hit/critical-reduction values can be filled from actual detail text. Tira is already accounted for in displayed damage; no additional conversion is applied. Engine has current Lora and scanned Mira effect support, with existing documented double-hit assumption.

Test 47 was an intermediate build; intended phone install is Test 48. Test cases for actual bonus versus green upgrade increment and actual Mira bonus versus cap were added. Physical fairy modal/tooltip interaction still needs user validation.

## Test 50 finalization

Supersedes intended Test 48 install: fairy detail read now explicitly detects the white modal text zone; a full-screen fallback temporarily hides the main panel instead of attempting impossible off-screen placement. The activity indicator remains present/masked. Resource configuration keeps a user-started setupRequested gate so starting from the app stays idle, but after the user starts setup in the overlay the next calibration field can still be proposed automatically. Hiding the overlay clears that gate. Idle war offer also responds to system capture stop.

Builds 47–49 were local verification iterations only. Final intended phone installation is Test 50. Newly changed parsers have regression tests; actual research failure and fairy popup behavior require physical retest by the user. Do not claim the screenshot's OCR failure proven fixed without retest or inspecting private research-rejected.txt after reproduction.

## Test 51 — uncertain fairy level confirmation

Fairy level no longer accepts the first non-null OCR result or lets a detail read silently replace profile evidence. At least two valid reads from each view must agree before other levels can be accepted automatically. Known-confusable 3/8 always prompts, even if OCR repeats the same digit across views. Conflicting/missing evidence prompts as well. The confirmation starts with an empty numeric input, validates 1–20, allows Peek game with the existing minimized form/restore mechanism, pauses the busy indicator while waiting, and stores levelConfirmed=true after an explicit answer. Confirmation callbacks check scan generation so STOP invalidates them. Tests cover repeated 8, 3/8 conflicts, missing detail observations and agreement across both views. Previously saved data is preserved; the new policy applies when scanning again.

## Test 52 — wspólna konfiguracja ekranów i sesje

- Wspólne okno zaznaczania dla zasobów, profilu, badań i wojny: kolorowe ramki, wybór pola po prawej, wspólne zatwierdzenie. Przyciski mieszczą się poza paskami systemowymi.
- Profil: nazwa, statystyki, bonusy, broń, trzy umiejętności, wróżka i zamknięcie na jednym podglądzie; osobno tylko wejście i dymek broni. Ramka wróżki obejmuje pełny poziom.
- Zasoby: wspólna konfiguracja kluczy/kosztu/listy i biletów/kosztu. Konfiguracja zapisuje obszary bez odczytu wartości. Podczas skanu pary licznik/koszt nadal korzystają ze wspólnych klatek.
- Badania: opis i zamknięcie razem; wojna: dwie flagi razem; klan: lista i zamknięcie razem. Wejście do badań współdzielone z zasobami. Zamknięcie sekcji badań/klanu używa pozycji wejścia z dolnego paska; rozpoznawanie pozostaje dla nieznanego stanu początkowego i odzyskiwania po błędzie.
- Skan profilu odczytuje nazwę i blok statystyk z jednego obrazu; brak statystyk uruchamia dotychczasowe ponowienia.
- Klawiatura: log telefonu potwierdził błędne OTHER_APP dla Gboard LinearLayout i SoftInputWindow. Klasy okien aktywnej klawiatury nie unieważniają gry; jej ekran ustawień oraz inne aplikacje nadal zatrzymują gesty. Potwierdzenie poziomu chowa klawiaturę i odnawia sesję gestów.
- Przeciwnik usuwany z pamięci i dawnych preferencji po zakończeniu udostępniania/STOP oraz przy uruchomieniu procesu. Powtórka wymaga nowego skanu przeciwnika i ukończonej symulacji w tej sesji.
- Ruch w dół listy badań skrócony z 28% do 19% wysokości ekranu, aby zachować większą zakładkę.

Weryfikacja urządzenia pozostaje po stronie użytkownika; wcześniejszy audyt fizyczny zatrzymał się na konfiguracji wróżki, przed przejściem do badań i listy klanu. Testy i lint nie zastępują tego przebiegu.

## Test 53 — konfiguracja całych ekranów i mapa przejść

- Ekran główny zasobów: wspólne zaznaczenie młotków, wejścia do wierzchowców i wejścia do badań. Wierzchowce: klucze, koszt, lista i zamknięcie. Umiejętności/zwierzaki: pola zawartości razem z widocznymi zakładkami. Przejścia używają zatwierdzonych pól bez ponownego otwierania selektora dla każdego przycisku.
- Lekkie wypełnienia wszystkich ramek w kolorach pól, także podczas przeciągania. Wspólna poprawka dla zasobów, walki, badań i wojny.
- Nagłówki i komunikaty konfiguracji wskazują ekran i trwające przejście; usunięto stary interfejs ustawiania pojedynczych pól zasobów i martwe potwierdzenia ekranów.
- `GameNavigationGraph` przechowuje wejścia/wyjścia akcji. Zasoby i badania/wojna korzystają z grafu; po udanym kliknięciu zmienia się oczekiwany ekran. Przebieg walki wykorzystuje już potwierdzony początkowy ekran i wewnętrzny powrót po konfiguracji bez kolejnych identycznych potwierdzeń.
- Zapis trasy badań od góry (wersja gestu, rozmiar obrazu, liczba ruchów w dół). Odtwarzanie bez analizy pośrednich ekranów; jeden odczyt docelowych ikon i ponowne wyszukiwanie, jeśli trasa nie pasuje. Ruchy w dół kończą się zatrzymaniem tego samego dotyku zamiast rozpędzania listy.
- Zapisane opisy badań, zamknięcia i lista członków używane bez ponownego wykrywania całego okna przy każdej zmianie badania/klanu.
- Nagranie z 4 listopada oraz sprostowanie użytkownika potwierdzają: flaga zawsze otwiera członków z mocą, a pole MVP poniżej — ranking punktów. Test listy wojennej w tygodniu zachowuje wcześniejsze założenie równych bonusów. Nie ma nowej blokady testu w tygodniu.
- Mapa i reguły: [GAME-FLOW.md](GAME-FLOW.md). Po instalacji standardowy reset wyłącznie pól. Przegląd nagrania i testy nie zastępują próby nowej wersji na telefonie.

## Test 54 — zakładki badań przed ich zawartością

Screen Screenshot_20261104_130259_Forge Master.jpg pokazał błędne proponowanie biletów/kosztu na drzewie technologii. Etap wejścia do badań zawsze konfiguruje teraz osobno dolne zakładki Umiejętności, Zwierzaki i Drzewo technologii. Zatwierdzenie wykonuje kliknięcie Umiejętności, dopiero następny podgląd proponuje bilety i koszt; lista zwierzaków jest po wejściu na jej zakładkę. Pierwszy wybór pól nie zależy od odgadnięcia, którą zakładkę gra zapamiętała. Współdzielona pozycja technologii jest zapisywana w AutoScanStore i używana przez konfigurację walki. Testy trasy wymagają pięciu podglądów niezależnie od początkowej zakładki.

## Test 55 — obrazy i analiza pracują równolegle

- Jeden obraz profilu służy do nazwy, łącznych obrażeń i zdrowia oraz przygotowania ikon trzech umiejętności i wróżki. Nazwa i statystyki są analizowane podczas dalszego odczytu bonusów; przed wejściem w broń wymagana jest zakończona analiza nagłówka i ewentualne ponowienie samego bloku statystyk.
- Broń i trzy okna umiejętności: przechwycenie wycinka, zamknięcie na podstawie zapamiętanych granic, dalsza nawigacja. Kolejka OCR analizuje kopie obrazów niezależnie od widocznego już ekranu. Kontrasty są liczone z tego samego obrazu.
- Po zebraniu okien bariera czeka na wszystkie odczyty. Nieczytelna broń/umiejętność jest otwierana ponownie pojedynczo, najwyżej drugi raz; dalsze braki obsługuje istniejąca ręczna weryfikacja danych.
- Wróżka korzysta z ikony przechwyconej na pierwszym obrazie profilu; opis efektu nadal otwierany i sprawdzany w jej szczegółach. Polityka potwierdzania niepewnego poziomu, w tym 3/8, zachowana.
- Obrazy to małe wycinki przechowywane w pamięci. OCR ma ograniczony rozmiar wejściowy. STOP/ukrycie/odzyskiwanie po błędzie unieważnia kolejkę i usuwa oczekujące wycinki; spóźnione wyniki nie przechodzą do kolejnego etapu.
- Lista bonusów zachowuje sprawdzanie końca podczas przewijania. Wyniki symulacji wymagają zakończenia pozostałych odczytów.
- Testy bariery obejmują zakończenie OCR przy nadal otwartym oknie, oczekiwanie na wszystkie wyniki po zamknięciu okien, pojedyncze ponowienie oraz STOP przed spóźnioną odpowiedzią. Realnego czasu skanu nie zmierzono — potrzebna próba na telefonie.

## Test 56 — powrót do sekwencyjnych umiejętności i brak wyposażenia

- Na prośbę użytkownika wycofano zbieranie okien umiejętności przed OCR. Przywrócony przebieg sprzed Test 55: profil/ikona, otwarcie dymka, odczyt nazwy i poziomu, do trzech ponowień kontrastu, zamknięcie, następna umiejętność. Odczyt wzorca ikony pomaga w rozpoznawaniu nazwy; samodzielny tryb ikona+poziom nie jest aktywowany w tej wersji.
- Nieudany OCR pozostaje nieznanym wynikiem. Pusty slot wymaga prawie jednolitego koloru rzeczywistego tła profilu, właściwego położenia względem geometrii oraz braku ikony. Potwierdzone puste umiejętności są zapisywane jako `empty`, a lista wyposażenia dla symulatora może mieć 0–3 elementów.
- Brak wróżki ma osobny zapis `present=false`, bez wymuszania poziomu. W edycji danych dodano jawny wybór Brak wyposażonej wróżki. Gdy kliknięcie nie otworzy jej okna, skaner pyta o obecność zamiast rozpoczynać całą procedurę od nowa. Niepewny poziom obecnej wróżki nadal wymaga potwierdzenia.
- Ikony obrażeń i zdrowia wojennego są rozpoznawane oraz zaznaczane wspólnie na jednym obrazie przed otwarciem pierwszego badania. Opisy procentowe pozostają w dwóch osobnych oknach.
- Do dalszego sprawdzenia na rzeczywistych przykładach: czy przy braku umiejętności zostają puste pozycje, czy zwierzaki przesuwają się w lewo. Brak potwierdzenia tego układu nie pozwala zakładać, że każda nierozpoznana ikona oznacza puste miejsce.

### Kolejne proponowane rozwiązanie — jeszcze nie aktywowane

Rozpoznawanie ikona+poziom powinno mieć oddzielne obszary grafiki, koloru, gwiazdek i liczby. 18 wzorców umiejętności znajduje się już w assets. Przed pominięciem szczegółów trzeba sprawdzić dopasowanie na rzeczywistych profilach i wersjach z gwiazdkami, ustalić próg pewności i zachować otwieranie wyłącznie niepewnej pozycji jako fallback.

Reguły wróżek powinny pochodzić z wersjonowanego zewnętrznego katalogu z okresem ważności, parametrami zależnymi od poziomu i źródłem danych; aplikacja powinna zachowywać ostatnią poprawną wersję offline. Ikona i poziom identyfikują pozycję katalogu, a nie aktualność efektu. Przy nieaktualnym katalogu lub brakujących danych potrzebny jest odczyt szczegółów. Zewnętrzny katalog nie jest wdrożony w Test 56.

## Test 57 — koniec listy, ponowienia umiejętności i widoczne braki

- Koniec listy członków porównuje próbki obrazu niezależnie od zmiennego odczytu nazw. Próbki pomijają lewy pas animowanych awatarów. Podczas listy dostępny jest przycisk Zatrzymaj listę, a podczas powrotu na górę — Lista jest na górze. Zatrzymanie uruchamia ponowienia odczytu aktualnego widoku; nie omija kontroli kompletności składu. Niekompletna lista nie trafia do raportu. Kontynuuj poprawia aktualny widok, osobny przycisk pozwala przejść listę od góry bez ponownego skanowania postaci i badań.
- Nieczytelna umiejętność jest otwierana do trzech razy, z odczytem oryginalnego obrazu i trzech kontrastów przy każdym otwarciu. Zebrane dowody pozostają między otwarciami; sprzeczne nazwy lub poziomy wymagają korekty. Czytelna nazwa ma pierwszeństwo przed wskazaniem wzorca ikony. Gwiazdki i poziom zasłaniają część grafiki, dlatego sama ikona pozostaje wskazówką, bez aktywowania trybu pomijania okien szczegółów. Nie wprowadzono jeszcze nowej maski gwiazdek; nie można usuwać wszystkich żółtych gwiazd, bo Morale samo ma taką grafikę.
- Brakujące lub błędne dane w formularzu od razu mają czerwoną ramkę/opis; formularz przewija się do pierwszego problemu. Można poprawić również typ wróżki. Puste bonusy nadal oznaczają zero, natomiast brak obrażeń, zdrowia czy nieznana umiejętność nie są automatycznie zastępowane zerem.
- Potwierdzenie użytkownika: brak umiejętności zostawia puste miejsca, zwierzaki nie przesuwają się w lewo. To uzupełnia wcześniejszą niewiadomą Test 56.
- Bieżący silnik tekstu pozostaje ML Kit. PaddleOCR jest kandydatem do porównania na tych samych problematycznych wycinkach, bez potwierdzonej przewagi na naszych danych. Wersja diagnostyczna zachowuje lokalnie ostatni wycinek umiejętności w cache/skill-latest.png. Dokumentacja: https://developers.google.com/ml-kit/vision/text-recognition/v2/android oraz https://www.paddleocr.ai/v3.0.3/en/version3.x/deployment/on_device_deployment.html . Rozpoznawanie obrazków wymaga osobnej metody, nie samego OCR; maskowanie wzorców opisuje https://docs.opencv.org/4.x/de/da9/tutorial_template_matching.html .

Testy jednostkowe, lint i budowanie APK przeszły. Zachowanie na rzeczywistym skanie pozostaje do sprawdzenia przez użytkownika. Po instalacji resetowane są wyłącznie pola kalibracji; połączenie z Discordem i pozostałe dane pozostają.

## Test 58 — odczyt pól graczy i zachowanie konfiguracji

Na prośbę użytkownika aktualizacje od tej wersji zachowują konfigurację pól. Instalator domyślnie zachowuje kalibrację; jej wyczyszczenie wymaga przyszłego wyraźnego polecenia użytkownika i przełącznika -KeepCalibration:$false.

Lista klanu wykrywa pełne szare karty graczy niezależnie od awatara i nazwy. Z jednego obrazu przygotowuje wspólny obraz nazw i mocy, pomijając awatary i dolne statusy aktywności. Trzy kontrasty OCR są przypisywane do odpowiednich kart, a sprzeczne odczyty pozostają brakujące. Ucięte karty są pomijane do kolejnego widoku. Parser przyjmuje również nazwę i moc w jednym wierszu OCR.

Przewijanie w dół jest krótsze, wolniejsze (650 ms) i kończy się zatrzymaniem tego samego dotyku. Zachowuje duży wspólny obszar kolejnych widoków, większy przy nieodczytanych kartach. Powrót do góry ma limit 20 gestów, aby przy kontrolowanym zatrzymaniu objąć 50 osób i potwierdzić górę. Koniec listy porównuje głównie zmienioną treść zamiast pozwalać, by biały obszar przeważył nad drobnym tekstem. Sam licznik 50 nie kończy przebiegu przed potwierdzeniem dołu. Przy niepełnym składzie jest jedno automatyczne przejście samej listy od góry, bez utraty postaci, badań i już zebranych graczy. Przycisk ręcznego zatrzymania pozostaje dostępny. Raport wymaga kompletnego składu.

Zaznaczanie wielu pól: boczne przyciski mają ciemne tło, kolorowy obrys i ciemny kontur napisów. Wspólna zmiana dotyczy zasobów, walki, badań i wojny.

Testy wykrywają sześć pełnych kart na dzisiejszym i wcześniejszym obrazie klanu, pomijają ucięte karty, uwzględniają rozmiary 200/320/640 oraz sprawdzają konflikty OCR i małe zmiany tekstu na białym tle. To nie potwierdza jeszcze skuteczności OCR i odczytu wszystkich 50 osób w pełnym przebiegu na telefonie. Diagnostyka DEBUG przechowuje wyłącznie ostatni wycinek listy i lokalny tekst OCR w cache/roster-latest.png oraz cache/roster-latest.txt.

## Test 59 — tożsamość postaci niezależna od pisowni OCR

Błąd Test 58: mapa listy używała nicku OCR jako klucza. Kolejne odczyty tej samej postaci pod różnymi zapisami nicku dopisywały fałszywych graczy. Dodatkowo wspólny wycinek nicku, mocy i ikonek dawał błędne litery/cyfry. Pobrana diagnostyka pokazała kilka wariantów Ultralegion, Dawido960 i zdana oraz uszkodzone wartości mocy.

- RosterRegistry przechowuje stabilne pozycje postaci. Widoki są łączone według obrazu awatara i nicku oraz kolejności co najmniej dwóch wspólnych sąsiadów. Zgodność szarego tła weryfikuje pole; samo jednakowe tło albo sam awatar nie identyfikują gracza. Niepewne dopasowanie nie dopisuje postaci: ten sam widok jest ponawiany, a potem pojawia się odzyskiwanie bieżącego etapu. Liczba postaci nie wynika z liczby wariantów OCR.
- Wykrywana jest czarna ramka awatara. Jej rozmiar stabilizuje pozycje wycinków przy minimalnych różnicach krawędzi szarego pola i przesunięciach listy. Porównanie ma tolerancję antyaliasingu, sprawdza również proporcje obrazu nicku i wymaga jednoznacznego dopasowania kolejności.
- Osobne wycinki nicku i mocy z tego samego obrazu. Kolorowe oznaczenia płci/rangi kończą wycinek nicku, a skrzyżowane miecze i status aktywności są poza wycinkiem liczbowym. RosterFrameReader analizuje wspólny obraz tych osobnych fragmentów w trzech kontrastach. Powtórne widoki poprawiają odczyty tego samego wpisu. Konflikty pozostają brakującymi danymi. Różne graficznie karty z identycznym tekstem OCR również wymagają rozstrzygnięcia zamiast cichego scalenia.
- Nakładka pokazuje osobno liczbę postaci i liczbę kompletnych odczytów. Wysyłka wymaga pełnego składu, danych bez konfliktów oraz potwierdzonego końca listy.
- Regresje obejmują 50 postaci w nakładających się widokach i w drugim przejściu z błędną pisownią nicków, wspólne awatary/moc różnych graczy, niepasujący ekran bez mutacji listy, poprawianie odczytów i przesunięcia rzeczywistych obrazów. Celowany test urządzenia czyta wyłącznie dostarczony plik PNG, przed jakimkolwiek uruchomieniem aktywności, trybu demo czy połączeń z serwerem. Nie przeprowadza nawigacji po grze ani pełnego skanu listy.

Pola kalibracji pozostają zachowane zgodnie z prośbą użytkownika. Nowa logika dotyczy wyłącznie listy wojennej i jej odczytu.

### Test 59 — końcowe uzupełnienia kontroli

- Wiersze są wykrywane od zamkniętych ramek awatarów na całym polu listy, niezależnie od przerw w szarym tle powodowanych przez tekst rangi. Kontrola szerokiego tła dopuszcza również niebieskie wyróżnienie naszej postaci. Częściowe ramki i wewnętrzne elementy avatarów nie tworzą dodatkowych wierszy. Nowa regresja przechodzi dziesięć rzeczywistych klatek nagrania i obejmuje własną niebieską kartę.
- OCR ma dodatkowe warianty z samym białym wypełnieniem nicku i złotym wypełnieniem cyfr, bez czarnej obwódki; drugi wariant lekko pogrubia maskę. Pojedynczy niepotwierdzony odczyt nie tworzy kompletnego wpisu. Wstępny odczyt bez masek dawał np. 128b zamiast 1.23b. Test urządzenia na problematycznym obrazie potwierdził poprawne sześć mocy i czytelne nicki po użyciu masek; na drugim obrazie potwierdzono kolejne sześć mocy. Testy te korzystały z plików PNG, bez klikania w grze i bez wysyłania raportu. Pełny żywy przebieg 50 graczy wymaga próby użytkownika.
- DEBUG oprócz ostatniego obrazu listy i tekstu zachowuje prywatnie ostatni wspólny obraz wycinków oraz trzy warianty OCR (roster-composite.png, roster-pass-0.png, roster-pass-1.png, roster-pass-2.png). Pliki są nadpisywane, nie są automatycznie wysyłane.

## Test 60 — większe kroki i poprawianie wyłącznie brakujących danych

- Liczba docelowa pochodzi z nagłówka Członkowie X/Y. Parser rozdziela obecną liczbę od pojemności i odrzuca sprzeczne nagłówki. Testy obejmują 49/50, 31/50, 8/25 i skład siedmiu osób. Po zebraniu rzeczywistej liczby postaci i kompletnych danych lista kończy się od razu, bez dodatkowych ruchów potwierdzających dół.
- Ruch w dół jest wyliczany z rzeczywistych pozycji widocznych kart. Przy sześciu kartach przesuwa prawie cztery osoby, zachowując dwie wspólne do dopasowania; przy pięciu — prawie trzy. Brak danych z wcześniejszego wiersza nie zmniejsza kolejnych ruchów. Gest trwa 300 ms i jest wyhamowany; limity współdzielonej ochrony gestów nie zostały zmienione.
- Powrót na górę wykonuje trzy szybkie ruchy po 180 ms między obserwacjami. Pierwsze dwa pozwalają na szybkie przesunięcie, ostatni kończy się przytrzymaniem tego samego dotyku, bez klikania gracza. Kontrola góry następuje po serii, a nie po każdym ruchu. Ruchy respektują limit 35% wysokości obrazu.
- Zwykły odczyt listy korzysta z dwóch przetestowanych masek wypełnienia liter i cyfr. Usunięto kosztowny odczyt obwódek oraz zapisy pełnych wariantów PNG przy każdym widoku. Zachowano diagnostykę ostatniej listy i tekstu.
- Skan przechowuje niewielkie wycinki nicku i mocy. Gdy postaci jest np. 50/50, ale kompletnych danych 49, ponawia tylko brakujący wpis z pamięci w innych skalach i grubościach maski. Nie przewija listy ani nie analizuje ponownie pozostałych 49. Dodatkowe warianty są dowodami z tego samego obrazu, nie nowymi głosami klatek. Ponowienia są ograniczone do dwóch rund, w paczkach maksymalnie sześciu braków.
- Po nieudanym ponowieniu dostępne jest Uzupełnij braki: pojedynczy wpis z zapisanym podglądem i polami nick/moc. Zatwierdzenie poprawia tylko ten wpis. Przejście listy od góry pozostaje wyłącznie ręczną opcją; nie uruchamia się automatycznie. STOP/ukrycie usuwa tymczasowe wycinki z pamięci i unieważnia oczekujące odpowiedzi.

Weryfikacja: 453 przypadki, 419 zaliczonych i 34 pominięte opcjonalne wejścia; zero błędów, lint zaliczony. Test 50 postaci/49 danych naprawia dokładnie jeden wpis. Na telefonie oba zapisane rzeczywiste obrazy przeszły normalny odczyt oraz celowe usunięcie jednego wpisu i jego naprawę wyłącznie z zachowanego wycinka (12/12 poprawnych mocy, bez nawigacji po grze). Manualny formularz oraz pełny żywy przebieg i odczuwalną szybkość sprawdza użytkownik.

Zainstalowano releases/Botyslaw-WALKA-Test-60.apk. Sumy kontrolne trzech plików konfiguracji były identyczne przed i po instalacji. Połączenie z Discordem i pozostałe dane zachowano.

## Test 61 — nieaktualne zamknięcie i utrata zakładki listy

Screen 17:56:53 pokazywał ekran główny wewnątrz selektora Zamknięcie otwartego okna, z polem na obszarze licznika czatu. Wyodrębniony kadr jest poprawnie rozpoznawany jako HOME w rozmiarach 360 i 480; nie zawiera wykrytego okna ani przycisku zamknięcia. Dlatego nie przypisujemy tego dowodowi na wadę samego detektora X. Naprawiono lukę między obserwacją i wykonaniem: odzyskiwanie nigdy nie kalibruje ogólnego zamknięcia na nieznanym ekranie, wymaga rozpoznanego kontekstu oraz ponownie sprawdza aktualny obraz przed kliknięciem. Jeśli obraz jest już HOME, przechodzi dalej. W razie niepewności czeka, potem pozwala ponowić ten etap. Zapisane wejścia sekcji służą do identyfikacji ich wyjścia; stare mainClose nie zastępuje świeżego wyniku.

Usunięto pełnoekranowy tekstowy fallback podczas odzyskiwania nawigacji. Rozpoznanie domu przy starcie korzysta również z istniejącego sprawdzania bitmapy w kilku skalach, gdy pierwszy wariant nie wystarcza. Licznik członków jest odczytywany tylko z pasa nad skonfigurowaną listą; nie ma fallbacku do całego ekranu. Test na telefonie potwierdził HOME bez OCR i kalibracji oraz same nagłówki z liczbą 49 i 50.

Screen 17:59:00 oraz ostatnia klatka odczytu pokazały po ruchu tylko jednego wspólnego gracza, Wojtekg. Zmniejszono odległość ruchu i dodano zapas wokół dwóch wspólnych kart. Po ruchu ten sam trzymany wskaźnik pozostaje nieruchomy przez kolejne 180 ms przed dotychczasowym zwolnieniem z przytrzymaniem; nie jest wstrzykiwany nowy tap. Gdy pierwszy widoczny gracz pasuje jednoznacznie tylko do ostatniego znanego, skaner może cofnąć około jednego wiersza i ponowić dopasowanie. Pojedynczy gracz nigdy nie zezwala na dopisanie całej strony. Po korekcie kolejne ruchy są nieco krótsze. Zachowano limity gestów i szybkie serie powrotu na górę.

Regresja korzysta z rzeczywistego obrazu po zbyt dużym ruchu i zapisanego początku listy: odrzuca widok z jedną wspólną postacią, rozpoznaje możliwość cofnięcia i łączy widok z przywróconym drugim wspólnym graczem. Ostatni widok testu jest odtworzony z zapisanych kart — nie zastępuje próby rzeczywistego gestu. Dodatkowe testy sprawdzają odzyskiwanie domu bez selektora oraz ograniczenie obszaru nagłówka.

Weryfikacja: 461 przypadków, 427 zaliczonych i 34 opcjonalne pominięte, zero błędów; lint i oba APK zbudowane. Statyczne testy na telefonie: HOME oraz nagłówki 49 i 50 zaliczone. Zainstalowano releases/Botyslaw-WALKA-Test-61.apk; trzy pliki kalibracji identyczne przed i po instalacji, parowanie zachowane. Rzeczywiste przewijanie i automatyczna korekta pozostają do sprawdzenia przez użytkownika. Nie wykonywano przebiegu po grze ani wysyłki raportu.

## Test 62 — pojedyncze zakończenie gestu po przerwaniu w Test 61

Screen 18:27:30 pokazał komunikat Gest został przerwany po pierwszym odczycie listy. Ostatni odczyt nicków i mocy był dostępny; komunikat pochodzi z callbacku onCancelled usługi dostępności. W Test 61 nie zapisywano fazy gestu, a dostępny log Androida nie wskazał miejsca przerwania. Dokładna przyczyna systemowego anulowania nie jest więc potwierdzona.

Usunięto dodatkowy pośredni, nieruchomy stroke z willContinue=true dodany w Test 61. Lista kończy teraz ruch jedną kontynuacją zwalniającą palec po 360 ms. Pozostałe wywołania, w tym skaner zasobów, zachowują domyślne 180 ms i ten sam kształt zakończenia. Punkty ruchów listy są zaokrąglane do pełnych pikseli; [dokumentacja Androida](https://developer.android.com/reference/android/accessibilityservice/GestureDescription.StrokeDescription#continueStroke(android.graphics.Path,%20long,%20long,%20boolean)) wymaga zgodności punktu kontynuacji z końcem poprzedniego stroke. Nie oznacza to, że API ogólnie zabrania nieruchomych kontynuacji — usunięto nową, zbędną część łańcucha powiązaną czasowo z regresją.

Zakończenie nadal sprawdza aktywną sesję, STOP, limit czasu i zakres długości przytrzymania 80–500 ms. Anulowanie przez system nie jest automatycznie ignorowane ani ponawiane. DEBUG zapisuje wyłącznie stan/fazę ruchu, współrzędne i czas do cache/gesture-last.txt oraz znacznika ScannerGestureFlow; bez wartości graczy, tekstu gry lub danych połączenia. Pozwoli to odróżnić przerwanie ruchu od przerwania zwolnienia wskaźnika.

Nowy test urządzenia buduje rzeczywiste obiekty Android GestureDescription dla zakończeń 180/360 ms, sprawdza zgodność punktów, pojedynczy finalny stroke i zwolnienie wskaźnika. Nie wysyła gestu do systemu, nie otwiera gry ani aktywności i nie stanowi dowodu skutecznego przewinięcia w Forge. Jednostkowe regresje potwierdzają, że dłuższy hold nie omija STOP, wygaśnięcia sesji ani limitu czasu. Pełny ruch po tej zmianie sprawdza użytkownik.

Test 62 zainstalowany: 462 przypadki, 428 zaliczonych i 34 opcjonalne pominięte, zero błędów; lint zaliczony. Na telefonie przeszły cztery konstrukcje natywnego zakończenia gestu (180/360 ms, zgodne punkty, brak wstrzykiwania dotknięć). Trzy pliki kalibracji identyczne przed i po aktualizacji. Artefakt: releases/Botyslaw-WALKA-Test-62.apk. Faktyczne przewijanie w Forge pozostaje do próby użytkownika.


## Test 63 — dwie listy klanów, ruchoma nakładka i wdrożony raport testowy

Screen 18:44:12 pokazał błąd Not found na wysyłaniu, a screen 18:44:49 własny niebieski wiersz SirMaxeN. Odczyt z prywatnego wycinka drugiego obrazu potwierdził siedem kart i dokładne moce, w tym SirMaxeN 791m. Rozbieżność Lunarr12 / Lunarr12- została rozstrzygnięta istniejącym ponownym OCR zapisanej karty; nie przewijano gry i nie dopisano graczy. Nie ma dowodu, że wcześniejsze 49 kompletnych danych wynikało z niebieskiego wiersza. Obie zapisane kalibracje flag są osobne i odpowiadają lewej oraz prawej stronie. Sam screen błędu wysyłania nie dowodzi, że poprzedni przebieg rzeczywiście odczytał różne klany.

Przed odczytem każdej listy wyodrębniana jest nazwa klanu z już używanego, wąskiego nagłówka. Skaner odrzuca powtórzenie własnego klanu; porównuje również ścisłą próbkę obrazu nagłówka z tego samego screena, aby odmienny odczyt nawiasu/tagu przez OCR nie ominął kontroli. Nie wymaga to dodatkowego screena ani przebiegu OCR. Końcowa kontrola wymaga kompletnych obu list, zgodnych z osobnymi licznikami członków, i odrzuca identyczne zestawy nick/moc. Rozpoznanie innego nagłówka jest kontrolą konkretnego ryzyka pomylenia klanów, a nie pełnym rozpoznawaniem ekranu po każdym kliknięciu.

Błąd flagi pozwala zamknąć bieżącą listę i poprawić tylko pole rywala; dane własnego klanu pozostają w sesji. Błąd wysyłania pozwala ponowić samo wysłanie ze stałym request_id, nie udaje błędu kalibracji. DEBUG zapisuje cache/war-rosters-last.json z fazą, nazwami klanów, oczekiwanymi licznikami i liczbą kompletnych wpisów. Bez danych połączenia. Nazwy/tagi z OCR mogą nadal zawierać drobne przekłamania znaków.

WarOverlayChrome używa wspólnych kwadratowych przycisków ScannerIconBar, uchwytu ⠿, zwijania do ikon i rozwijania ⛶. STOP pozostaje dostępny w obu rozmiarach, a pomoc przy końcu/początku listy ma własną ikonę ↕. Długie komunikaty i przyciski naprawy mieszczą się w ograniczonym ScrollView. Przesuwanie i zmiana rozmiaru aktualizują wykluczenie panelu z blokady dotyku; odroczone operacje czekają na zakończenie przeciągania. Natywny test mierzy odłączony od WindowManager układ i sprawdza przewijanie treści, kwadratowe nieucięte ikony oraz callbacki rozwinięcia, pomocy i STOP. Nie jest to test fizycznego przeciągania nakładki podczas gestu w grze.

Przed wdrożeniem publiczny endpoint war-test zwracał 404, a Discord nie miał /report war-test. Po wyraźnej zgodzie użytkownika wdrożono release-81lUL1 z kopią poprzedniego kodu. Health potwierdził nową wersję, endpointy wojenne są dostępne, a ponowny GET komend Discorda potwierdził war-test i war-list. Serwer sprawdza również odrębność obu list i nazw klanów. Testowy raport działa codziennie, zakłada równe bonusy rywali, jest powtarzalny i nie zużywa niedzielnej blokady. Nie wysłano fikcyjnych danych ani wiadomości; raport z prawdziwymi graczami wymaga poprawnego skanu obu klanów.

Weryfikacja końcowego kodu: 466 przypadków Androida, 432 zaliczone, 34 opcjonalne pominięte, zero błędów; lint bez błędów blokujących, APK i APK testów zbudowane. Serwer: 79/79 testów, audyty obu repozytoriów bez wykrytych sekretów. Pełny przebieg po grze oraz fizyczne przesuwanie panelu sprawdza użytkownik. Kalibracji nie wolno resetować.

Końcowy Test 63 zainstalowano przez instalator z -KeepCalibration. Porównanie SHA-256 potwierdziło identyczność wszystkich trzech plików kalibracji. Na telefonie zaliczono trzy testy statyczne: układ nakładki, siedem kart z dokładnymi mocami i ponownym odczytem brakującego nicku oraz licznik 50 w nagłówku. To nie był przebieg po ekranach gry. Artefakt: releases/Botyslaw-WALKA-Test-63.apk.



## Test 64 — pełne profile klanów i symulacje krzyżowe (TEST)

Nowy, osobny przycisk w menu Walki: „Pełne profile klanów + symulacje (TEST)”. Wejście otwiera nakładkę i grę; skan zaczyna się dopiero przyciskiem „Skanuj / Rozpocznij”. Dotychczasowa szybka lista według mocy pozostaje dostępna. W tej wersji pełny tryb jest wyłącznie testowy i zakłada równe bonusy obu klanów.

Po rozpoznaniu kart członków skaner klika aktualnie potwierdzony awatar, odczytuje profil wspólnym skanerem walki, sprawdza nick, zapisuje wynik i wraca do listy. Nowe przejścia ROSTER → PROFILE → ROSTER nie są mylone z profilem własnym na ekranie głównym. Zapis własnej postaci i zaakceptowany przeciwnik nie są zastępowane masowymi skanami. Każdy nowy odczyt zaczyna z pustym stanem — także wtedy, gdy przygotowanie profilu się nie powiedzie. Niepewne poziomy, wyposażenie, nazwy i granice okna wróżki pozostają do poprawy. W trybie masowym nie ma pełnoekranowego fallbacku OCR opisu wróżki.

Osobny zapis full_war_profiles_v1 przechowuje profile i błędy, a full_war_pairs_v1.db zapisuje pojedyncze wyniki par przez SQLite/WAL. Nazwa klanu, strona, nick, moc i data polska decydują o ponownym użyciu profilu; zmiana mocy lub nowy dzień wymaga nowego odczytu. Wyniki par mają skrót obu profili, bonusów i wersji modelu. STOP zachowuje ukończone wpisy; nowy start ponownie odczytuje listę, ale pomija potwierdzone profile. „Nowy pełny skan” czyści wyłącznie ten moduł. Konfiguracja pól, parowanie i dane innych modułów pozostają zachowane.

Po zebraniu obu list udostępnianie ekranu zostaje zatrzymane. Obliczenia wykonuje jeden worker z obsługą przerwania: 128 prób każdej pary, następnie 1024 próby dla czołowych kandydatów i wybranych głównych par. Model walki pozostaje ten sam; wymieniono wielokrotne tworzenie tablic na ich ponowne użycie. Regresja porównuje wyniki z wcześniejszą wersją dla 72 zestawów i identycznych seedów. Tira/odbicie nie otrzymuje liczbowej oceny nawet wtedy, gdy OCR bonusu nie odczyta procentu; automatyczne przyjęcie Miry wymaga odczytu redukcji krytycznej.

Backend waliduje pełne pokrycie macierzy, liczniki wygranych/porażek/remisów oraz profile. Dobiera unikalne cele, maksymalizując oczekiwaną sumę zwycięstw z progiem >50%. Dodatkowi gracze mogą dostać jawnie wspólny cel. Do trzech alternatyw ma własne szacowane szanse. Braki i nieobsługiwane efekty nie są zerowym procentem ani dowodem, że ktoś jest za mocny. Raport /report war-test pokazuje próby, przedziały Wilsona 95%, kompletność oraz cały zestaw par w pliku. Przedział dotyczy losowości prób; zasady i czasy silnika nadal są eksperymentalne. Mistrzostwo umiejętności poza widoczną listą przyjmuje jawne założenie 0%, możliwe do ręcznej poprawy.

Po skanie dostępne jest sprawdzanie wszystkich zapisanych profili, poprawianie braków i przeliczenie bez odczytu gry. Ponowienie skanu po wyłączeniu udostępniania prowadzi przez aplikację i nową zgodę Androida; zapisane profile i wyniki są używane ponownie.

Walidacja: 474 przypadki Androida, 440 zaliczonych i 34 opcjonalne pominięte, zero błędów. Lint bez błędów blokujących. Testy obejmują ważone przypisanie porównane z wyczerpującym wyszukiwaniem, potwierdzone indeksy kart, rozróżnienie pustego/pełnego/nieznanego pola oraz przerwanie wewnątrz symulacji. Backend: 97/97 testów, m.in. pełna macierz 50×50, upload większy niż poprzedni limit, niedozwolone wyniki dla braków/Tiry, zakresy niepewności i zachowanie starego raportu mocy.

Statyczny test telefonu zaliczył osobną przestrzeń danych, wznowienie zapisu, odrzucenie złego nicku/brakujących pól, rzeczywistą macierz 1×1 i ponowne obliczenie z cache (1449 ms dla scenariusza testowego; to nie jest obietnica czasu dla 2500 par). Zaakceptowany własny profil pozostał identyczny. Zaliczone również odłączony układ nakładki i cztery konstrukcje zakończenia gestów. Nie wysyłano gestów do gry ani raportu z fikcyjnymi danymi. Kalibracja trzech plików została porównana hashami przed/po instalacji i zachowana.

Backend wdrożony jako release-ieosQt z kopią poprzedniego kodu i potwierdzeniem HTTP 200. Artefakt: releases/Botyslaw-WALKA-Test-64.apk. Pełny przebieg po 100 rzeczywistych profilach, pozycje wyposażenia na innych telefonach i trafność wyników względem nagranych walk wymagają próby użytkownika. Nie stanowią ich dowodu testy syntetyczne ani statyczne.


## Test 65 — wznowienie pełnych profili i diagnostyka

Odrzucony screen 21:33:19 pokazuje przycięty dolną krawędzią awatar SirMaxeN na niebieskim tle. Statyczna regresja odróżnia sześć pełnych kart od tego uciętego wiersza. Dodatkowo kolejność i moce zmieniały się w trakcie odwiedzania profili; pełny tryb rozpoznaje aktualne wpisy po obrazie awatara/nicku bez wymagania niezmiennej kolejności. Częściowy awatar wymaga wyśrodkowania przed kliknięciem. Szybki tryb listy mocy zachowuje dotychczasowe łączenie nakładających się widoków.

Nieudany nagłówek nie zeruje już potwierdzonego licznika 50. Pełny skan zapisuje obie listy, wizualne tożsamości, odczyty, klany, liczniki, stronę, aktualną postać, przetworzone osoby oraz etap i części profilu. Kontynuacja odtwarza zapis i lokalizuje aktualny awatar; „Ponów braki” czyści wyłącznie listę prób niekompletnych osób. Przed dialogiem i po każdej sekcji zapisują się częściowe dane. Potwierdzone sekcje można wykorzystać ponownie. Aktywny przebieg przeżywa zmianę daty z limitem świeżości 24 h. Nowy pełny skan nadal czyści tylko swój moduł.

Poszerzono obszar nicku i użyto odcięcia ikon płci. Geometria wyposażenia pochodzi z obrazu bez zasłaniającego panelu. Niezgodne poziomy wróżki pozostają pytaniem; zgodne odczyty Lory można dodatkowo potwierdzić niezależnym procentem efektu. Brak wyposażenia nadal wymaga poprawy i nie jest zastępowany domyślną wartością.

cache/full_war-events.jsonl przechowuje ograniczony dziennik etapów, osoby, próśb/zakończeń kliknięć, przewinięć, sekcji profilu, zapisu, STOP i błędów. cache/full-war-last-rejected.png zawiera ostatni odrzucony wycinek listy. Bez danych parowania Discorda. Pliki diagnostyczne są w cache i mogą zostać usunięte przez Androida; punkty wznowienia pozostają w trwałym zapisie.

Przed aktualizacją istniało 19 częściowych profili, zero kompletnych. Ich odczytane pola zachowujemy. Test 64 nie zapisywał historycznej pozycji listy, więc nie można jej odtworzyć z danych, których nie zapisał; pełne punkty wznowienia obowiązują od Test 65.

Automatyczna walidacja: 479 przypadków, 445 zaliczonych, 34 opcjonalne pominięte, zero błędów; lint bez błędów blokujących. Próba pełnego przebiegu po grze pozostaje testem użytkownika. Backend ma 99 zaliczonych testów i opublikowane maksymalnie dwie rozłożone alternatywy (release-oQxJSn); główne pary bez zmian.


Test 65 zainstalowano na podłączonym telefonie z -KeepCalibration. Porównanie hashy przed/po instalacji oraz po testach potwierdziło identyczność trzech plików kalibracji i full_war_profiles_v1.xml. Natywny test w osobnej przestrzeni zapisał punkt SirMaxeN / 50 / wróżka, następnie zamknięto proces i druga instancja odtworzyła postać, etap oraz tożsamość listy. Sprawdzenie aktywnego zapisu przez zmianę daty również zaliczone. Pozostałe testy natywne: zapis/profil/macierze 1×1/cache (1214 ms), odłączony układ nakładki i konstrukcje gestów — zaliczone. Nie wysyłano gestów do gry ani przykładowych raportów. Audyty obu repozytoriów bez wykrytych problemów. Artefakt: releases/Botyslaw-WALKA-Test-65.apk.


## Test 66 — odczyt nagłówka listy klanu

Log Test 65 z 23:50–23:51 czasu polskiego potwierdza przejście przez badania, klan, zakładkę wojny i flagę. Następnie trzy ręczne próby zatrzymały się na liczniku członków, bez rozpoczęcia odczytu profili. Screen 23:51:43 ma czytelne 50/50. Odtworzenie na tym screenie, także dla dokładnie zapisanej kalibracji (284,332)-(962,653) i produkcyjnego układania wierszy OCR, odczytało 50. Nie dowodzi to przyczyny wcześniejszego błędu na żywo: Test 65 nie zachował źródłowego wycinka ani tekstu tego odczytu. Stary war-rosters-last.json pochodził z poprzedniego przebiegu i nie jest dowodem aktualnego postępu.

Wspólny RosterHeaderReader obsługuje wejście na listę i wznowienie pełnego skanu. Z jednego ograniczonego wycinka próbuje oryginału 2×, a tylko przy niepowodzeniu maski jasnych liter z progami 220 i 180 w skali 3×. Sukces wymaga prawidłowego licznika oraz nazwy klanu; nie przyjmuje domyślnych 50. Nie ponawia nawigacji ani skanowania całej listy. W cache/war-header-last.png zapisuje dokładne piksele wejściowe, w cache/war-header-last.txt wyniki prób, a dziennik full_war-events.jsonl ma granice pola, tekst, odczytany licznik i nazwę klanu. Nieudana próba zostawia profile i kalibrację bez zmian. Kontynuuj powtarza nagłówek.

Brak dowodu, że pierwotny problem na żywo został odtworzony; nowy zapis pozwala go rozstrzygnąć po kolejnej próbie użytkownika. Statyczne testy używają tego samego czytnika co nakładka, dokładnego pola użytkownika i zapisanego screena.

Test 66 zbudowany i zainstalowany z -KeepCalibration. 479 przypadków: 445 zaliczonych, 34 opcjonalne pominięte, zero błędów. Lint bez błędów blokujących; audyty repozytoriów zaliczone. Statyczne testy telefonu: dokładny wycinek ze screena odczytał 50 w pierwszej próbie, wymuszona maska kontrastowa również odczytała 50 w pierwszej próbie, pusty nagłówek pozostał bez licznika po dokładnie trzech próbach. Test wznowienia procesu, macierz 1×1/cache (1223 ms), odłączony układ nakładki i konstrukcje zakończeń gestów zaliczone. Porównanie hashy potwierdziło zachowanie wszystkich trzech plików kalibracji i full_war_profiles_v1.xml po instalacji oraz testach. Nie wykonywano przebiegu po grze. Artefakt: releases/Botyslaw-WALKA-Test-66.apk.


## Test 67 — przegląd przepływu i granic nakładek

Szczegóły: SCANNER-REVIEW-2026-11-05.md. Rzeczywisty OCR nagłówka z Test 66 zawierał 50/5o, następny kontrast odczytał 50/50. Dwa kompletne profile zostały zapisane przed błędami zamknięcia. Zamknięcie korzysta teraz z X rozpoznanego na czystym obrazie otwieranego profilu. Nie używa X listy klanu ani pola innej osoby. Wznowienie i awaryjne zamykanie rozpoznają czysty obraz; nieznany ekran nie wywołuje zgadywanego przejścia przez klan. Dodano ochronę spóźnionych callbacków i odrzucanie powtórzonych kart przed zmianą rejestru. Niekompletne efekty wróżek nie blokują się w pętli odtwarzania tego samego cache. Identyczne częściowe punkty zapisu nie są zapisywane ponownie.

W zasobach czysta klatka dotyczy wyłącznie awaryjnego rozpoznawania nawigacji; normalny zapisany przebieg nie otrzymał dodatkowych kontroli ekranów. Konfiguracja, częściowe profile i pozostałe dane mają pozostać zachowane podczas aktualizacji.

Weryfikacja Test 67: 482 przypadki, 453 zaliczone, 29 opcjonalnych pominiętych, zero błędów. Włączono dodatkowe zapisane ekrany własnego profilu, dwóch przeciwników, broni i ekranu głównego. Lint: 0 błędów, 240 ostrzeżeń. Audyty obu repozytoriów bez wykrytych problemów. Testy natywne zaliczyły rzeczywisty screen z zasłoniętym X, czysty profil z rozpoznanym X, odrzucenie użycia tego X dla innej osoby/niezakończonych szczegółów, wznowienie procesu, zapis i macierz 1×1/cache (1266 ms), odłączony układ panelu, konstrukcje gestów oraz trzy próby nagłówka: zwykły, kontrast i pusty. Nie wysyłano gestów do gry ani raportów. Instalacja Test 67 przez -KeepCalibration; hashe trzech plików kalibracji i pełnych profili identyczne przed/po instalacji i po testach. Aktywny zapis wojenny nie był czyszczony. Artefakt: releases/Botyslaw-WALKA-Test-67.apk. Pełny rzeczywisty przebieg nadal sprawdza użytkownik.


## Test 68 — cyfry wróżek i długie skany

Poziomy 3 i 8 miały bezwarunkową blokadę automatycznego przyjęcia. Same powtórzenia OCR nadal nie rozstrzygają znanego pomylenia 3/5/8, ale są teraz dwa dodatkowe źródła: zgodny efekt Lory w kolejnych odczytach opisu/bieżącego bonusu oraz porównanie numerycznych pikseli z potwierdzonymi wzorcami czcionki. Rozstrzyganie Lory korzysta z dotychczasowego modelu +30% i +10 p.p. na poziom, wyłącznie wybierając spośród odczytanych kandydatów; konflikt procentów blokuje ten wybór. Zasady innego sezonu wymagają aktualizacji modelu i nie są odgadywane z koloru ikony.

Wzorce startowe 3/5/8 pochodzą z czytelnych znaków na dostarczonym screenie (3 pod wróżką, 5 i 8 na wyposażeniu tej samej czcionki). Zapisane są wyłącznie maski cyfr 20×28 i proporcje, bez zdjęcia gracza. Własne potwierdzenia mogą dodać kolejne wzorce; sprzeczne lub zbyt podobne wzorce nie dają automatycznego wyniku. Ekstraktor wymaga wiersza poziomu, kropki prefiksu i liczby znaków; dwucyfrowy poziom nie może stać się pojedynczą cyfrą. Źródła efekt/grafika/ręczne są zapisane w danych. Starsze niepotwierdzone poziomy 3/5/8 w masowym cache wymagają sprawdzenia tylko wróżki, a pozostałe odczytane sekcje pozostają dostępne. Dane źródłowe nie są kasowane podczas tej walidacji.

Ostateczne pytanie pokazuje powiększony wyodrębniony kształt cyfry, przyciski 3/5/8 wypełniające pole, ręczny zakres 1–20 i istniejące „Podejrzyj grę”. Brak pewności nie wpisuje domyślnego poziomu. Tira pozostaje nieobsługiwana numerycznie przez obecny silnik, niezależnie od pewności poziomu.

Wykryto dwa absolutne limity 5 minut: GestureSafety liczył od uzbrojenia sesji, AutoScanRunner od początku całego skanu zasobów. Sesja gestów wygasa teraz po 5 minutach bez rozpoczętej poprawnej akcji; samo sprawdzanie współrzędnych nie odnawia jej. Limit 2000 akcji, granice ekranu, STOP, odwołanie sesji i utrata gry pozostają aktywne. Zasoby odnawiają licznik braku postępu przy kolejnym kroku, zachowując ograniczenia pojedynczych kroków. Masowy skan klanu i wcześniej odnawiał sesję przy profilach — jego łączny czas >5 minut sam w sobie nie dowodził wyłączenia dostępności.

Instalator nie wykonuje już force-stop przy zwykłym -KeepCalibration. Kod AOSP onPackagesForceStoppedLocked usuwa usługi wymuszenie zatrzymanej aplikacji z ENABLED_ACCESSIBILITY_SERVICES: https://android.googlesource.com/platform/frameworks/base/+/master/services/accessibility/java/com/android/server/accessibility/AccessibilityManagerService.java . To jest dodatkowa przyczyna możliwego wyłączenia po naszych aktualizacjach, odrębna od limitu gestów. Nie zmieniamy ustawień dostępności poleceniem systemowym. W odczytanym stanie telefonu włączona była usługa oryginalnego pakietu, a nie .autotest; użytkownik musi ponownie włączyć usługę Test, jeśli nadal jest wyłączona.

Podczas pierwszej instalacji diagnostycznej trwał skan Kris84. Porównanie plików wykryło zmianę zapisu profilu w tym samym czasie; log potwierdza zapis nagłówka, bonusów, broni i trzech umiejętności przed restartem. Nie należy twierdzić, że wszystkie hashe tej pierwszej próby były identyczne. Próby grafiki cyfr używają wyłącznie izolowanej pamięci testowej. Końcową instalację należy sprawdzić osobno.

Końcowa weryfikacja Test 68: 491 przypadków, 462 zaliczone, 29 opcjonalnych pominiętych, zero błędów. Test logiczny sesji wykonuje 24 minuty symulowanego czasu regularnych gestów i potwierdza wygaśnięcie dopiero po bezczynności; osobny test zasobów przekracza 5 minut łącznie przy poprawnych kolejnych krokach. Nie jest to 24-minutowy przebieg po grze. Lint: 0 błędów, 243 ostrzeżenia; audyty obu repozytoriów zaliczone. Test natywny odróżnił rzeczywiste 3 spod wróżki i cyfry 5/8 tej samej czcionki, sprawdził odtworzenie pamięci i skalę 2×. Zaliczone również regresje nagłówka, zasłoniętego X, zapisu/wznowienia bez force-stop, macierzy 1×1/cache (1159 ms), panelu i konstrukcji gestów. Końcowa instalacja przez -KeepCalibration: trzy pliki kalibracji i full_war_profiles_v1.xml identyczne przed/po instalacji oraz po testach. Nie resetowano ustawień ani danych, nie wykonywano przejścia po grze ani wysyłania raportu. Po instalacji lista włączonych usług nadal wskazywała oryginalny pakiet, bez .autotest; użytkownik musi włączyć Test ręcznie. Artefakt releases/Botyslaw-WALKA-Test-68.apk.

## Test 69 — poziom ze współczynnika i zielonego przyrostu

Screen 00:51:57 pokazuje cyfrę 11 i OCR 10/11. Przyciski 3/5/8 były stałymi skrótami, nie wynikiem odczytu; usunięto je. Opcjonalne przyciski potwierdzenia pochodzą wyłącznie z rzeczywistych kandydatów OCR i obliczeń. FairyProgression liczy poziom jako 1 + (współczynnik − baza) / przyrost. Para współczynnik/przyrost jest czytana razem z opisu bezpośrednio przed nawiasem zielonego (+…%). Nie używa procentu wyzwalacza Tiry ani końcowego/capowanego bonusu postaci. Wymaga dwóch zgodnych odczytów; brak nawiasu, niecałkowity wynik, sprzeczność lub niezgodny znany przyrost nie dają automatycznej odpowiedzi. Tira 10,5%, baza 5,5%, przyrost 0,5 daje 11 także przy OCR Poz.10. Tira 6,5% z +0,5 daje 3; Lora 50% z +10 daje 3; Mira używa swojej bazy i przyrostu odczytanego z opisu, niezależnie od nasycenia końcowej redukcji. Bazy są w osobnym wersjonowanym assets/fairy_progressions_v1.json, pochodzą z dostarczonych ekranów danego sezonu; nie jest to zdalnie aktualizowany katalog. Zmiana sezonu wymaga aktualizacji danych reguł.

Wynik zapisuje współczynnik, bazę, przyrost, wersję reguł i levelSource=progression. Błędne niepotwierdzone dane cache sprzeczne z opisem wymagają ponowienia tylko wróżki; pozostałe sekcje pozostają. Potwierdzenie ręczne użytkownika nie jest nadpisywane automatycznie. Zasady symulacji Tiry nie zostały zmienione przez tę poprawkę odczytu.

Walidacja: 495 przypadków, 466 zaliczonych, 29 opcjonalnych pominiętych, zero błędów; lint 0 błędów/248 ostrzeżeń. Regresja używa dokładnego tekstu Wi3rzbA i sprawdza wyliczenie 11 oraz brak stałych przycisków 3/5/8. Po wyraźnym wyborze użytkownika instalacji teraz zatrzymano wyłącznie usługę przechwytywania własnej aplikacji przez run-as am stopservice --user 0. Nie wykonano force-stop. Test 69 zainstalowano z KeepCalibration; trzy pliki kalibracji, pełne profile i wpis ENABLED_ACCESSIBILITY_SERVICES były identyczne przed/po. Nie wykonywano przebiegu po grze ani restartu instrumentalnego usługi. Artefakt releases/Botyslaw-WALKA-Test-69.apk.

Pobrany później punkt wznowienia: własny klan 47 kart/50 osób, 45 ustalonych nicków i mocy, 39 profili zapisanych jako kompletne, dwa częściowe; dwie karty miały niepewne dane. Cel pXan, faza roster. Przeciwnicy jeszcze 0. To stan w trakcie przebiegu, nie dowód zakończenia ani ostateczna liczba pominiętych. Liczenie dopasowuje nick i moc do aktualnej listy, nie liczbę historycznych rekordów preferencji.
