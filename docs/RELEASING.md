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

The Play variant disables the external website update prompt and opens Google Play for manual updates. Upload `app-play.aab`, not an APK or a bundle from another variant.

## Google Play

Application ID: `com.sirmaxen.BotyslawSkaner`. Use the app's Alpha closed-testing track, enter accurate release notes, save the release and submit the publishing overview for review. A successful upload is not approval. Store configuration and permission declarations must continue to match actual behavior.

Version 1.0.10 (19) was submitted to the Alpha review flow on 2026-10-25. Its confirmed inventory replay contains 372 items, or 369 after three base pets. An older recording with irregular scrolling remains an unresolved low-confidence case. The release passed 81 JVM checks and release lint with no errors before submission.

Public pages and intentional listing graphics are maintained in `docs/play-console/`. The hosted foreground-service demonstration is a real emulator UI walkthrough; it does not demonstrate gameplay count accuracy. The public demo does not send data to a real clan.

## Website channel

Optional website publication uses `tools/publish-update.ps1` with explicit SSH host, destination and key parameters. The APK is uploaded before the public manifest is atomically replaced. Never copy signing files or private data to the server.

Direct-install updates preserve data only when the package ID and signing certificate match. Google Play App Signing may use a different app-signing certificate; do not assume cross-channel installation compatibility.
