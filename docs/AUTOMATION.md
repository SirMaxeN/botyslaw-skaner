# Automatic scanning (Android device-test build)

Version 1.2.10 keeps automatic read the primary home action. The original overlay remains available as **Manualny odczyt** (Manual read) below it. This is a device-test build; it is not a claim of Google Play approval or universally exact counting.

## Consent and scope

The user explicitly enables the accessibility service after a separate in-app disclosure. The service receives foreground package events and performs confirmed navigation/scroll gestures; it does not request accessibility window contents. MediaProjection consent remains separate and is required for each capture session. Enabling the service never starts a scan: every run needs the Scan button and a bounded gesture session. Leaving the game, locking the phone, stopping capture or pressing STOP revokes it. A dispatched bounded gesture may finish before release. Initial gestures allow a short input-window commit interval after opening the shield corridor; continued held-pointer segments do not repeat that delay. Overlay windows use full-screen pixel coordinates without system-bar inset offsets.

A transparent touch shield blocks accidental interaction outside the STOP/Finish panel. During a list read one narrow vertical drag corridor remains open, including between strokes; during other injected gestures only their temporary corridor is opened; physical touches in that corridor cannot be distinguished from injected input. Android system bars remain usable. Touching a blocked area stops the run. Keep hands off the game during automation. Capture coordinates must match the supported portrait display geometry. The route contains no forging, purchase, summon or merge operation. Confirm navigation controls carefully; recorded summon-cost areas are not navigation targets.

## Thirteen independent setup steps

1. Hammers counter.
2. Open mounts button.
3. Mount keys counter.
4. Single mount summon cost.
5. Mount list viewport, at its top.
6. Close mounts button.
7. Open skills/eggs/research screen.
8. Skills tab.
9. Skill tickets counter.
10. Skill x5 summon cost.
11. Pets tab.
12. Pets/eggs viewport, at its top, excluding equipped strip and incubators.
13. Close skills/eggs/research, returning to the hammer screen.

During configuration the app attempts to propose every non-inventory region using language-independent icons, colors and layout anchors. Mount artwork is not an identity anchor. Tab locations come from their strip and surrounding controls rather than Polish labels. Unsupported or ambiguous layouts fall back to manual marking. Both inventory viewports are proposed from their surrounding fixed controls, including the empty portion of a short list. Every proposal is shown for confirmation or manual redrawing; unknown layouts still require manual selection.

**Every proposal waits for user confirmation or redrawing**, including an automatically found red close circle with a white X. The rectangle is shown on a still frame before it is used. Confirming a numeric region validates its OCR immediately; unreadable selections remain on that field. Counter regions include their icon and complete counter bar, with horizontal and vertical room for larger values. Hammers cover the lower anvil base, not the neighboring forge upgrade control. Confirming a navigation region records its normalized coordinates and allows setup to navigate onward.

Full configuration and one-field repair are distinct flows. Repair saves only the selected field and returns to the main panel; it never restarts the remaining wizard. Setup callbacks are cancelled when the selection or flow is abandoned. The input shield covers actual setup navigation while leaving cancellation available. Numeric-only setup validation does not arm gestures or install a shield. Selection windows preserve the underlying game's focus; ownerless accessibility events are not treated as proof of leaving the game.

## Fast runtime from saved fields

Before a new or repeated read, a bounded preparation step recognizes the current game screen. It can close mounts or skills/pets using the confirmed close coordinate and verify the home screen, then start from hammers. It never repeats a close tap or blindly clicks an unknown screen. This one-time return-home check is separate from normal route scanning.

The normal fixed route reads confirmed crops and taps confirmed navigation coordinates without per-step screen classification. AutoScanRunner publishes each step before performing it. The saved navigation runner sends one tap, waits for its callback and a fixed settling interval, then completes that step once. Source recognition remains available for one-time return-home preparation, configuration and qualified read-failure recovery. It never compares full-screen checkpoints or item artwork.

Each numeric counter uses a burst of three fresh source frames. The first frame runs the full original, contrast, inverse and threshold set. Follow-up frames can finish after two different variants agree without competing numeric candidates; missing or conflicting evidence uses the full set. Mount keys and cost, and tickets and x5 cost, share packed OCR passes over the same three source screenshots. Slot mapping keeps the fields independent; verified costs are reused once by the next route step. Variants from one source frame cast only one vote. An unsuccessful burst is retried once on the same saved crop, without repeating navigation or silently moving the region. Missing frames, conflicting values or an unsuccessful retry expose that field for repair. Stable abbreviated values such as 53.7k retain their approximation flag.

## Inventory counting and controlled scrolling

Automatic mode uses the same inventory recording/counting pipeline as the manual overlay. Controlled scroll gestures replace the user's swipes; they do not create a second item-counting algorithm. Counting uses actual movement observed in distinct captured source frames, not the number of issued gestures. Progress remains visible, and STOP and Finish list are available throughout list scanning. A prominent hands-off warning stays on the running overlay; its fixed-height progress area keeps STOP aligned with the touch shield. The compact setup action is labeled "Ustaw pola" and uses centered, adequately sized buttons.

Lists must start at the top. Short single-viewport lists, including a single mount, can finish without scrolling. Longer lists scroll upward in bounded gestures with capture feedback. A left-packed partial final row can establish the end with a short white footer. Full rows require at least one entire row pitch of whitespace; shorter ordinary gaps between different artwork are not accepted as end evidence. Logical scroll passes cover about seven rows, with fractional endpoints. Balanced long physical strokes respect the saved viewport and platform distance/time limits, avoiding a tiny remainder. Actual distinct source-frame timestamps are measured only within moving gestures; idle and release gaps never enter the motion cadence. Slower capture lengthens the whole pass instead of shrinking it into tiny movements. Shorter probes are used after a genuinely blocked pass near the boundary. Cached capture polls never authorize another scroll gesture or establish a blocked bottom; a bounded wait for a fresh frame fails explicitly on a capture stall. End checks run separately from live-counting work and release the held pointer while a visible footer is checked. The narrow touch corridor is reused across the entire list, including finger repositioning, and is closed when the list ends. Stable input geometry does not incur another window-commit wait. A full final row or ambiguous repeated rows may require Finish list; uncertain counting is labeled as an estimate instead of being presented as exact. Runtime does not reject an inventory merely because it differs from an old setup screenshot.

Repeated eggs, capture gaps, display changes and incomplete list coverage remain accuracy limits. Recording memory, list duration, step duration and the overall route are bounded. A bounded run may stop rather than publish incomplete results. Do not describe estimates as exact.

## Results and manual fallback

Only completion of all steps creates a new resource session and opens the results screen automatically. Values remain unconfirmed until user review, and no automatic Discord upload occurs. STOP or failure keeps the prior session and discards partial results.

Manual read opens the original overlay for manual navigation, field marking and scanning. It uses the same capture/OCR/inventory analysis components without gesture-control permission. This provides an independent way to read resources if an automatic route needs repair.

Raw screen frames and setup references remain on the phone. Only reviewed numeric values are synchronized with the paired account.

## Distribution and verification

Do not upload this build to Google Play without updating the accessibility declaration, store/privacy disclosures and review demonstration. This build is installed locally for testing. Unit tests cover sequencing, stale callbacks, gesture bounds, repeated source buffers, OCR voting, geometry and list-counting regressions. Stored-screen and JVM checks do not prove full physical-device end-to-end accuracy; report device verification separately.

For a phone already running the Play-signed app, build `./gradlew.bat :app:assembleAutoTest`. It uses the separate package `com.sirmaxen.BotyslawSkaner.autotest` and label Botysław Skaner AUTO Test, preserving the production installation and its data. The test package has independent permissions, calibration and pairing. The service-specific accessibility settings page is used when supported, with a general-settings fallback for restricted vendor pages.

After each test update, `tools/install-auto-test.ps1` clears only saved calibration geometry/navigation and legacy checkpoint files. It verifies their removal before launch. Discord pairing, credentials, Keystore data and unrelated preferences remain intact. Do not use a whole-app reset or uninstall in this workflow.

## Version 1.2.2 verification (2026-10-29)

The AUTO Test build completed 192 JVM tests: 186 passed, six optional private-fixture tests were skipped, and none failed. AUTO Test lint completed without errors. Included private regressions covered the real one-mount viewport, all five numeric proposal regions, the short-footer two-egg screenshot, and the confirmed 372-item recording. That recording still carries the existing estimation warning; matching its confirmed count is not proof that every irregular capture is unambiguous.

A generated 702-item repeated-egg sequence retained its two-item tail through 549 distinct frames, with no uncertainty or estimation. Its simulated capture duration was 18.27 seconds and final analysis took about 0.41 seconds on the development PC. These are replay measurements, not a measured full phone scan. Actual phone throughput depends on source-frame cadence, navigation, and OCR.

The signed separate AUTO Test APK was installed on the connected phone as version 1.2.2 (26). The installation helper verified removal of calibration/navigation files while preserving pairing and unrelated data. Full automatic-route timing on the user's current inventory was not completed in this iteration.

## Recorded 1.2.2 performance baseline

A real ADB recording on 2026-10-29 lasted 119.95 seconds. Accurate video-PTS inspection places the Scan action at approximately 42.5-43.0 seconds and pets-list counting at 67.5 seconds: about 25 seconds elapsed before the long list began. At the end of the file the scanner was still moving, with roughly 52 seconds of pets-list capture and about 77 seconds of total scanning recorded. The file ended before the scan, so it cannot establish full completion time. Rendering in that interval was smooth; source-video frame rate is not proof of the scanner ImageReader's own cadence.

The baseline exposed idle/release intervals polluting the old slowest-frame pacing window. The 1.2.3 movement-only planner and paired numeric reads address those observed costs. Treat model/transport budgets as estimates until a full device run is measured.

## Version 1.2.4 counting and speed changes

Progress and final results now share one full-detail incremental analysis session. Six-frame lookahead preserves the existing repeated-row recovery logic without replaying all captured frames after Stop. Geometry is still checked against source images: commanded finger distance alone cannot establish movement at an elastic boundary, during a blocked gesture, or after a capture stall.

Actual 1.2.3 diagnostics showed approximately 72 seconds for a full route, including 51.3 seconds for pets (6.7 seconds of final replay). A 275 ms physical stroke started only every 1.1-1.3 seconds. Version 1.2.4 removes repeated touch-shield window reconstruction between these strokes and removes duplicate final replay. This is a targeted speed change; no twofold device-speed claim is made without a user-measured run.

Automated checks passed after extracting the common stream, including the existing private 372-item and 702-item regressions. A generated 505-item sequence also verifies that progress remains within a viewport of the visible end and equals the final result after flushing. The user's approximate 101-row recollection is not used as a production constant or claimed as a confirmed current count.

The user performs manual testing. Future development should run automated tests, build and install authorized APKs, and avoid manual device walkthroughs unless explicitly requested.

## Version 1.2.5 refinement

Uncertain counter setup validates the same selected region again before asking the user. Persistent readable ambiguity is resolved by explicit value selection during configuration and scanning; a wrong OCR value does not establish that the rectangle itself is wrong. The scan can retry the same crop, accept an explicitly selected value, repair that field on request, or stop. Stale prompts close when the route is cancelled or times out. Three-source consensus and contrast disagreement safeguards remain in place.

The source-paced profile uses longer viewport-bounded strokes, retaining a 0.45-row margin and the same platform distance bounds. The ordinary pass is approximately 7.15 rows, split into two long strokes on the tested geometry. The source-frame budget is 0.28 row, below half-row ambiguity; capture and end checks remain active. Transport-budget tests compare against 1.2.4 without claiming a measured wall-clock improvement. User diagnostics for 1.2.4 showed a 25.3-second pets phase, smooth capture without the previous long inter-stroke gaps, and a 1 ms final flush.

## Version 1.2.6 structural recognition

Screen identity and numeric proposals use fixed UI controls and their relative layout. Dynamic inventory content is excluded from control-component searches. The selected research tab distinguishes skills, pets and technology without inspecting the item illustrations, labels or incubator artwork. Mount header recognition is independent of its summon price; gray disabled buttons and red prices remain valid UI states. Forbidden-action detection must continue to include those disabled controls because they can become enabled later.

Setup navigation now accepts only the expected structural screen identity as automatic destination evidence. An image changing or an X disappearing does not establish that a close reached Home. Bounded read-only retries can fall back to explicit user destination confirmation; they never repeat the tap. Saved legacy pixel signatures remain configuration metadata and are not used for runtime matching.

The technology tab supports recognizing/closing the shared research screen and selecting the existing skills/pets tabs. It introduces no technology-upgrade gesture. Regression samples include arbitrary inventory repainting and enabled/disabled controls; original screenshots remain private and excluded from Git.


## Version 1.2.7 processing and overlay overhead

The numeric reader overlaps a single fresh-frame request with the current frame's OCR. The request is still delayed by 32 ms and still requires a new native image. Exactly three source frames vote, with the original contrast and conflict checks. Cancellation removes only the owning pending request; a late result is released. At most one prefetched full screenshot is retained.

During a run the panel retains its window, STOP button and fixed-size text/action slots. Labels and the step border update in place. WindowManager receives a position update only when avoiding the protected field requires movement. Setup and idle panels keep their existing behavior. This removes repeated panel removal/insertion while leaving STOP and Finish list available.

A two-entry cache shares immutable grayscale crops only when the native frame identity, source crop, requested region and output dimensions all match. Native source changes and capture lifecycle changes invalidate it. Cached polls retain their original timestamps and do not become new motion evidence.

Motion matching evaluates predicted shifts first, stopping a candidate only when its accumulated nonnegative error already proves it cannot be a winning or eligible repeated-row peak. The selected peak's adjacent errors are evaluated exactly for subpixel fitting. Exhaustive-reference tests cover 420 cases; a separate deterministic work test requires less than one quarter of the old pixel comparisons for a representative translating frame. This is a processing-work result, not a full-device speedup measurement.

Accepted user-run diagnostics before this change recorded pets-list steps of 18.3 and 18.7 seconds with zero uncertain frames, plus approximately 19–23 seconds across the other route steps. No manual phone scan is performed by the agent for this release. Scroll distance, motion cadence limits, footer stability requirements and short-list proof duration are unchanged.


## Version 1.2.8 viewport proposals and navigation recovery

Supported portrait layouts receive full inventory viewport proposals during the existing setup wizard. Mount bounds come from the modal header and summon control; pets bounds come from the resource header and equipped strip above summon/incubators. Current item count or artwork does not define the viewport height. The user sees and confirms every proposed rectangle or redraws it. Grid validation requires actual visible icons, and an empty or unreadable image cannot silently create a zero inventory. Approved proposals use cropped grid fitting and preserve their complete lower bounds; manual redraws retain the prior loose-fitting path.

A navigation gesture's completion means that Android delivered input, not that the game opened its destination. Each transition therefore observes fresh screenshots and requires two matching structural screen identities at least 120 ms apart. Openings settle for at least 240 ms; closes settle for at least 450 ms. An individual transition has a 1.8-second observation deadline and the entire original navigation transaction, including recovery, has a 6.5-second deadline.

After a missed transition, two recent consistent observations of a known actual screen can authorize one restoration route of at most three existing navigation actions. A recognized wrong modal can be closed via the X detected on a fresh image, then the desired dialog/tab can reopen. A missed button may be retried once from the verified original screen. Recovery steps do not recursively recover, and unknown or stale screen observations authorize no taps. Cancellation invalidates callbacks and cancels only the owning pending screenshot. The surrounding scan continues to own gesture permission and STOP.

No manual device walkthrough or scan was performed by the agent for this release. The user verifies the installed test APK after automated regression and static checks.


## Version 1.2.9 screen reconciliation

Every navigation action first reconciles two time-separated observations of the current structural screen. If the destination is already visible, the action waits for its settling condition and finishes without a tap. A recognized source allows the confirmed navigation coordinate. A different known window triggers the one permitted restoration route before that coordinate can be used. Unknown layouts authorize no taps.

Navigation observes the latest rendered image, including a static destination which arrived before the request. It does not require the producer to render an additional frame just to acknowledge an already-closed window. This changes navigation only: numeric OCR still requires three independent source frames. Screen classification runs on one background worker; cancelled observation results are recycled/ignored and never advance the route. Debug navigation diagnostics contain step names and screen kinds only.

The original 1.8-second observation cutoff could expire before a late capture had provided enough HOME evidence. The updated observation window is three seconds plus a 750 ms no-action settling grace, with an 8.5-second total navigation deadline including recovery. The normal path completes as soon as its stable source/destination is established. A transition failure no longer implies invalid calibration or automatically opens a button-repair flow.

The actual user-supplied state after the failed CLOSE_MOUNTS is covered by a private HOME identity test, including its visible scanner panel. The agent performs automated tests only; the user validates the installed build in the game.


## Version 1.2.10 restored normal route

Versions 1.2.8–1.2.9 made screen recognition a mandatory gate for every navigation step. User testing showed that this regressed the previously working saved-coordinate route. Version 1.2.10 removes those mandatory pre/post observations from ordinary navigation. The screen-aware coordinator remains a separate exception path, not the default route.

The sequencer updates its internal step and publishes the state before calling its port. The overlay yields one UI frame before the saved tap so the visible label can render. The saved-navigation runner accepts the tap callback once, then waits 600 ms for closing, 450 ms for research opening, 400 ms for mounts opening or 320 ms for tab selection. Neither a quick close nor a duplicate callback can cause an extra step or another close tap. The 10-second saved-action deadline and STOP invalidate queued callbacks without another action.

Only a numeric field with zero usable candidates after two consecutive complete three-source-frame bursts qualifies for a screen check. Any candidate (including a valid zero), incomplete source capture, earlier repair attempt, stopped capture or foreground loss blocks that fallback. If the observed screen already matches the field, or is unknown, the existing retry/value/rectangle prompt remains available. A different known screen may invoke one bounded restoration route, then retry only that field once. Accepted other resources remain in the sequencer; only the corresponding paired counter cache is cleared. Inventory counting is never silently restarted after scrolling.

Tests connect the real sequencer and saved-navigation runner with a delayed close callback. They verify that CLOSE_MOUNTS is published before the tap, OPEN_RESEARCH cannot run until the callback plus its 600 ms settle, duplicate callbacks cannot double-advance, and STOP prevents the next action. Physical gameplay testing remains the user's responsibility.
