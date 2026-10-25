# Security and data handling

Capture starts only after user action and Android MediaProjection consent. A visible overlay and foreground notification provide stop controls. Frame analysis and OCR run locally. Screen images are not uploaded, saved by the production scanner, or included in releases.

Optional Discord pairing exchanges a short-lived one-use code for a device credential. AndroidKeyStore protects the stored credential. Synchronization sends reviewed resource numbers over HTTPS to the fixed backend origin. No Discord bot token is bundled in the app. The local demo simulates pairing and sending without contacting the backend or updating a real clan.

The app does not request microphone, accessibility-service, root, contacts, SMS, broad storage or package-installation permissions. ML Kit SDK data disclosures are described in the public privacy policy; local screen processing does not imply that every dependency is telemetry-free.

Google Play builds open Google Play for update checks. Website builds may offer an update link that opens in the browser after user action. The scanner does not silently download or install executable updates.

Production signing material belongs only in ignored `.signing/`. Keep a separate private backup. Never upload signing keys, local pairing data, private fixtures, account setup history or server credentials to Git or public download directories. Only intentional public store screenshots belong in `docs/play-console/assets/`.

Run `python tools/check_repository.py` before committing. It checks Git's actual candidate files for excluded artifacts and common credential patterns without printing matched secret values. This is a guard against accidental disclosure, not a security certification.
