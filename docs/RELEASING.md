# Build and release

## Signing

The production build expects `.signing/production.jks` and `.signing/release.properties`. Both are ignored. Properties are `storePassword` and `keyPassword`; the configured key alias is `scanner`. Restore the existing signing material from a private backup, never generate a replacement for an established installation channel without planning the migration.

## Checks and artifacts

1. Increase `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Run `./gradlew.bat :app:testDebugUnitTest :app:lintRelease`.
3. Optionally run `./tools/test-inventory.ps1` with the local private fixture archive.
4. Build a direct-install APK with `./gradlew.bat :app:assembleRelease`.
5. Build the Google Play artifact with `./gradlew.bat :app:bundlePlay`.

Outputs are `app/build/outputs/apk/release/app-release.apk` and `app/build/outputs/bundle/play/app-play.aab`. Keep retained installers in ignored `releases/`. Use Android build-tools `apksigner verify --print-certs` to verify APK signature continuity.

The Play variant disables the external website update prompt. It checks update availability through the official Google Play library and offers to open the app listing; it does not start an SDK download/install flow. Upload `app-play.aab`, not an APK or a bundle from another variant.

## Google Play

Application ID: `com.sirmaxen.BotyslawSkaner`. Use the app's Alpha closed-testing track, enter accurate release notes, save the release and submit the publishing overview for review. A successful upload is not approval. Store configuration and permission declarations must continue to match actual behavior.

The current prepared release is **1.3.0 (versionCode 35)**. The application ID remains `com.sirmaxen.BotyslawSkaner`. It includes the optional accessibility gesture mode, user-confirmed geometry and navigation setup, automatic list scrolling, manual fallback, reviewed results and official Play update-availability checks. See [accessibility declaration preparation](play-console/accessibility-declaration.md) for the implementation facts, 399-character Polish release notes and the required recording sequence.

Build/artifact verification, the current automatic-mode review video, its public upload and Console submission must each be verified separately; this documentation update does not claim any of those steps has completed. Do not use earlier test totals as validation of 1.3.0. The existing foreground-service movie is a capture-only reference and is not the new accessibility review demonstration.

The public site destination was confirmed through read-only SSH on 2026-10-29: `/home/srv80270/domains/komars.eu/public_html/botyslaw-skaner`. Keep the existing `foreground-service-demo.html` and `foreground-service-demo.mp4` as capture-only assets. Reserve separate `accessibility-demo.html` and `accessibility-demo.mp4` for the reviewed real-device recording; neither new file was present when checked. The intended reviewer page is `https://komars.eu/botyslaw-skaner/accessibility-demo.html`. Do not publish a placeholder page as if the demonstration were complete.

Version 1.0.10 (19) was submitted to the Alpha review flow on 2026-10-25. Its confirmed inventory replay contains 372 items, or 369 after three base pets. An older recording with irregular scrolling remains an unresolved low-confidence case. The release passed 81 JVM checks and release lint with no errors before submission.

Public pages and intentional listing graphics are maintained in `docs/play-console/`. The hosted foreground-service demonstration is a real emulator UI walkthrough; it does not demonstrate gameplay count accuracy. The public demo does not send data to a real clan.

## Website channel

Optional website publication uses `tools/publish-update.ps1` with explicit SSH host, destination and key parameters. The APK is uploaded before the public manifest is atomically replaced. Never copy signing files or private data to the server.

Direct-install updates preserve data only when the package ID and signing certificate match. Google Play App Signing may use a different app-signing certificate; do not assume cross-channel installation compatibility.

## Local AUTO Test installation policy

After every AUTO Test installation, reset only marking/calibration settings and preserve Discord pairing. Run `./tools/install-auto-test.ps1` (optionally `-DeviceSerial <serial>` and `-ApkPath <apk>`). The script verifies the APK targets `com.sirmaxen.BotyslawSkaner.autotest`, installs it, force-stops it, and removes only the three calibration preference files (`scan_calibrations_v1.xml`, `auto_scan_navigation_v1.xml`, `auto_read_checkpoints_v1.xml`) plus their `.bak` companions before launching. It never reads or clears pairing credentials or Android Keystore data. Do not use `pm clear`, uninstall the app, or delete the preferences directory. The separate Play package is untouched.


## One-click local AUTO Test build

Connect and authorize the Android phone, then double-click `build-and-install-auto-test.cmd` in the repository root. The launcher discovers JDK 17/21 from `JAVA_HOME` or the user's Gradle JDK directory, and the Android SDK from `ANDROID_HOME`, `ANDROID_SDK_ROOT` or `LocalAppData/Android/Sdk`. It runs AUTO Test unit tests, lint and APK assembly before invoking the existing verified installer. Only the separate AUTO Test package is installed; calibration is reset and Discord pairing is preserved.

The final result and APK path stay visible until you type `exit` (case-insensitive). Failures also keep the window open. The launcher does not commit, upload or publish anything.

```powershell
# Build, check and install; wait for exit.
./tools/build-and-install-auto-test.ps1

# Build and check without touching a phone.
./tools/build-and-install-auto-test.ps1 -SkipInstall

# Non-interactive verification (also available with -SkipInstall).
./tools/build-and-install-auto-test.ps1 -SkipInstall -NoWait

# Optional: use cached dependencies, or choose one of multiple connected devices.
./tools/build-and-install-auto-test.ps1 -Offline -DeviceSerial <serial>
```

The command launcher forwards these same options. Without `-Offline`, Gradle can download missing dependencies. No signing secrets are required for the debug-signed AUTO Test variant.


### Verified 1.3.0 release preparation, 2026-10-29

Play bundle 35 (1.3.0) is uploaded and its Alpha release saved. Play unit checks: 298 total, 292 passed, 6 optional skipped, 0 failures; Play lint passed. The AccessibilityServices declaration is saved with the reviewed, redacted 177-second video at https://komars.eu/botyslaw-skaner/accessibility-demo.html; both undeclared-permission release errors disappeared. The remaining warnings concern optional deobfuscation and native debug symbols. Google review/approval remains a separate state.


On 2026-10-29 the Alpha release 1.3.0 (35) was submitted through the publishing overview. Console confirmed "Changes in review" while preliminary automated checks were still running; review submission will proceed after those checks. This is not an approval or confirmation of availability to testers.


### Prepared 1.3.1 (36): stop capture on review

Entering or restoring the results screen closes both scanner overlays and stops MediaProjection through the existing capture shutdown path. ScanSession values and Discord pairing are retained; sending/copying is no longer required to stop capture. Starting another scan requires fresh Android screen-capture consent. This change has not been submitted to Google Play.

The counter mask also preserves red insufficient-resource digits in both paired and single-field OCR, including skill x5 costs and mount costs. It retains white digits and the existing independent-frame consensus; no extra recognition passes are added. Automated color-mask regression covers white/red text and dark/cyan/green button backgrounds.


### 1.4.0 (37) submitted, 2026-11-06

Clan war tools (war list, clan ranking, Discord update status, Sunday one-list lock) and the adaptive icon fix (22 dp safe-zone inset, navy background) were uploaded to the Alpha closed-testing track as "1.4.0 Wojna klanów: lista i ranking" and submitted; Console showed "Changes in review" with preliminary checks running. Same upload certificate as 1.3.2. Checks: 513 JVM tests (53 optional skipped, 0 failures), Play lint passed. Warnings: deobfuscation file and native debug symbols only. Not an approval.


### 1.4.1 (38) hotfix submitted, 2026-11-06

Opens on the main menu, ignores the red unread-chat badge in home detection (it read as a close X and stopped the resource scan), and clears 1.3.x screen fields once on first start (not in AUTO Test). Uploaded to Alpha and submitted by the user; 514 JVM tests, Play lint passed. Not an approval.
