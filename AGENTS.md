# Repository instructions

## Physical-device test installation

User instruction, 4 November 2026: preserve the current calibration fields on subsequent test updates. Clear them only when the user explicitly asks. This supersedes the earlier reset-after-every-install rule.

Use `tools/install-auto-test.ps1 -KeepCalibration` for updates. It verifies the separate `com.sirmaxen.BotyslawSkaner.autotest` package and leaves calibration, pairing, credentials, Keystore and unrelated data intact. Calibration is preserved by default too; use -KeepCalibration:$false only for a future explicit user reset request. Never use pm clear or uninstall, read/copy pairing secrets, or change the separate Google Play production installation.

## Validation workflow

Run automated unit/regression tests and appropriate static checks. The user performs manual testing of new APKs. Do not conduct manual UI walkthroughs or gameplay scan runs between builds unless the user explicitly requests a specific recording or device interaction. Continue to build and install authorized test updates using the calibration-only reset policy above.


## Visual consistency (user instruction, 6 November 2026)

The app and every overlay must look like one product. Home screens, setup/calibration, resource scanning, battle, war preparation, clan ranking, dialogs and minimized panels share the same palette, typography, button styles, icon style, spacing, progress/step header, STOP placement and support footer. Build UI only through the shared helpers in `ui/ScannerAppearance.kt` (and `ScannerDialogTheme`); do not hard-code colors, sizes or ad-hoc button styles in individual screens. When adding or changing any screen or overlay, check it against the existing ones and update the shared helpers instead of diverging. Mention any intentional visual exception in the change notes.

## Graphics and assets (user instruction, 6 November 2026)

When a screen, overlay, icon, header or store listing needs new artwork, generate new dedicated assets for it (image generation is allowed) in the shared visual style. Do not reuse unrelated existing images or game screenshots as decoration, and never commit user screenshots or private fixtures as app assets.
