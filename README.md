# Botysław Skaner

Native Android companion for the BotysławSmith Discord bot. Reads Forge Master resource counters and inventory lists through a small user-controlled overlay. Screens are processed on the device; reviewed numeric results can be copied or synchronized with a paired Discord account.

## Requirements and build

- Android 8.0 or later; explicit overlay and screen-capture consent.
- JDK 17 or 21, Android SDK 36, Gradle wrapper 8.13.
- Application ID: `com.sirmaxen.BotyslawSkaner`; source namespace: `eu.komars.forgescanner`.

Set `JAVA_HOME` and `ANDROID_HOME`, or configure an ignored `local.properties` through Android Studio.

```powershell
./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

Signed APK: `./gradlew.bat :app:assembleRelease`. Google Play bundle: `./gradlew.bat :app:bundlePlay`. Signing files must be configured locally; see [release instructions](docs/RELEASING.md).

## Use

1. Pair with Discord if synchronization is wanted; local scanning also works without pairing. Options opens settings, Help contains the instructions, and Demo mode is available in Options even before granting overlay permission.
2. Grant overlay access and choose the primary **Automatyczny odczyt** (Automatic read) action. Separately enable gesture control after reading its disclosure, then share only Forge Master in Android's capture dialog.
3. Complete the initial configuration. The app proposes counters and navigation controls from stable headers, footers, modal frames and selected tabs; inventory artwork and currency-button state can change, but every proposed rectangle, including a detected close X, waits for your confirmation or redrawing. Both full inventory viewports, including empty space below a short list, are proposed from surrounding fixed controls. Confirm them or redraw the areas; unsupported layouts require manual selection. Counter proposals include the icon and the complete counter bar with room for growing resource values; the hammer region covers the lower anvil base.
4. For later scans, start on the hammer screen and press Scan. The app reads the saved regions, taps saved navigation coordinates and scrolls the lists. Runtime does not repeat full-scene detection or checkpoint comparisons. STOP and Finish list remain available.
5. The completed automatic scan opens its results immediately. Review the result. Optional base deductions are one mount and three pets. Copy the Discord import command or send reviewed values to the paired account.

**Manualny odczyt** (Manual read), below the main action, opens the original manual overlay. Navigate between screens yourself, mark fields with Set, and press Scan; scroll inventories from top to bottom while following the progress hints. Gesture-control permission is unnecessary in this mode.

Numeric counters use a short burst of three fresh source frames and multiple contrast variants. Variants from one source frame count as one vote. Automatic mode retries an unsuccessful burst once on the same confirmed region before offering repair of that field. Missing or conflicting values require review; agreement reduces transient errors but does not guarantee correct OCR.

Both modes use the same inventory recording/counting pipeline. Controlled scrolling replaces the user's swipes in automatic mode, using approximately seven-row passes and shorter boundary probes. Frame cadence is measured only during movement, excluding gesture-release and idle gaps. Identical rows and missing captures can still make motion ambiguous; estimated results are labeled and should be reviewed before sending. Navigation does not include forging, summoning, merging or purchases.

## Automatic scanning

The Android 1.2.10 device-test build provides the main automatic action with confirmed setup regions, saved navigation coordinates, confirmed automatic inventory viewport proposals with manual correction and bounded gestures. See [automatic scanning](docs/AUTOMATION.md) for setup, limits and distribution requirements. Manual read remains available, and neither mode sends results without review. Stored-screen and unit checks do not establish full physical-device end-to-end accuracy.

## Source layout

- `app/src/main`: capture, OCR, counting, overlay UI, synchronization and update handling.
- `app/src/test`: JVM regression tests.
- `app/src/androidTest`: Android UI and integration checks.
- `docs`: integration, security and release instructions.
- `docs/play-console`: public privacy/landing pages and store assets.
- `tools`: local verification and optional website release publishing.

The Discord backend is maintained separately in the sibling `polska-moc-bot` repository. Its mobile integration is documented in `docs/MOBILE.md`. Android and backend releases have independent version histories.

## Repository hygiene

Run `python tools/check_repository.py` before committing. Keep signing keys, credentials, SDK paths, generated builds, installers and private replay data out of Git. `.signing/`, `.local/` and `releases/` are local-only. Back up the production signing key privately; deleting it prevents future updates signed with that key.

An optional `.local/inventory-fixtures.zip` holds curated private regression samples. Run `./tools/test-inventory.ps1` to use them; temporary extracted files are removed afterward. The archive and original recordings are not distributed with the app or repository.

See [security](docs/SECURITY.md), [release instructions](docs/RELEASING.md) and [change log](CHANGELOG.md).
