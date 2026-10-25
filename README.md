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

1. Pair with Discord if synchronization is wanted; local scanning also works without pairing.
2. Grant overlay access and start a screen-capture session.
3. Follow the prompts: hammers, mounts and keys/cost, skill tickets and x5 cost, pets or eggs.
4. On first use, select each counter or the approximate inventory viewport. Start lists at the top and scroll to the bottom. Stable evidence of the end can finish a scan automatically; manual finish remains available.
5. Review the result. Optional base deductions are one mount and three pets. Copy the Discord import command or send reviewed values to the paired account.

Identical rows and missing captures can make fast scrolling ambiguous. Estimated results are labeled; review them before sending. The app does not operate the game or spend resources.

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
