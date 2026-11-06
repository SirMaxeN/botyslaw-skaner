# Repository instructions

## Physical-device test installation

User instruction, 4 November 2026: preserve the current calibration fields on subsequent test updates. Clear them only when the user explicitly asks. This supersedes the earlier reset-after-every-install rule.

Use `tools/install-auto-test.ps1 -KeepCalibration` for updates. It verifies the separate `com.sirmaxen.BotyslawSkaner.autotest` package and leaves calibration, pairing, credentials, Keystore and unrelated data intact. Calibration is preserved by default too; use -KeepCalibration:$false only for a future explicit user reset request. Never use pm clear or uninstall, read/copy pairing secrets, or change the separate Google Play production installation.

## Validation workflow

Run automated unit/regression tests and appropriate static checks. The user performs manual testing of new APKs. Do not conduct manual UI walkthroughs or gameplay scan runs between builds unless the user explicitly requests a specific recording or device interaction. Continue to build and install authorized test updates using the calibration-only reset policy above.

