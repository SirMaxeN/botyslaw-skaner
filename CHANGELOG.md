# Change log

## 1.2.10 (34)

- Restore the normal saved-coordinate route without screen classification before or after each navigation tap. Retain foreground, gesture-session, geometry, touch-shield and STOP checks.
- Publish the step before tapping, yield one UI frame for its label, and wait for the gesture callback plus a fixed settling gap before advancing. Closing waits 600 ms; opening research waits 450 ms, mounts 400 ms and tabs 320 ms.
- Reserve screen-aware recovery for two consecutive complete numeric OCR bursts with zero usable candidates. Valid zeroes, ambiguous digits, incomplete captures and list-count failures do not start navigation recovery.
- Preserve accepted resources, permit at most one failed-field retry after restoring a different recognized screen, and clear only that counter pair's cached values. Correct or unknown screen identity opens the existing read-resolution prompt instead of starting a navigation loop.
- Add direct saved-route tests and an integration test proving close-state publication, gesture completion, settling, duplicate-callback handling and STOP ordering before research opens.

## 1.2.9 (33)

- Reconcile the current recognized screen before every navigation action. An already-reached destination completes after settling without another tap; a wrong known window is restored before any saved coordinate is used.
- Inspect the latest rendered image rather than discarding an already-rendered destination while waiting for another frame. Numeric OCR retains its independent three-frame requirement.
- Run structural navigation classification on a background worker, reuse one analysis for a recovery X, and discard results from cancelled or superseded observations.
- Allow a bounded late-transition settling grace while retaining one recovery route, source checks and a global navigation deadline.
- Keep valid navigation calibration after a transition failure instead of forcing the user to remark a button that already worked.
- Add the actual failed-close HOME screenshot as a private regression and cover already-closed windows, wrong source states, late captures and cancellation.

## 1.2.8 (32)

- Propose full mount and pets/eggs scroll viewports from modal/header/footer/equipment controls, including empty space for short inventories. Both proposals always require user confirmation or redrawing.
- Fit approved viewport grids strictly inside those bounds and retain the empty lower viewport instead of shrinking to the currently visible items. Manual redraws retain their existing loose-grid fitting.
- Verify that each runtime navigation reaches a stable expected screen before another step runs; closing transitions receive a longer settle interval.
- Restore a known wrong screen through one bounded navigation route after a missed transition. Recovery closes only recognized windows using a freshly detected X and may retry a missed button only from a recognized state.
- Stop on unknown screens, capture stalls, unsuccessful recovery or the global navigation deadline. No recovery action purchases, summons, merges or forges resources.
- Add automated regressions for short inventories, full viewport proposals, slow transitions, missed taps, wrong windows, cancellation and recovery limits.

## 1.2.7 (31)

- Prefetch one fresh numeric source frame while OCR processes the previous one, retaining three independent source votes and all contrast checks. Cancellation affects only the owning screenshot request and releases late images.
- Reuse the running overlay window, STOP controls and stable layout across step changes; move it only when a protected field requires another position.
- Reuse grayscale crops only for the same native source frame and identical crop/output geometry, with a two-entry memory bound and capture-lifecycle invalidation.
- Prune provably ineligible motion matches while preserving exhaustive-search peaks, tie order and exact subpixel fits. Regression tests compare 420 scenarios to the previous exhaustive matcher.
- Preserve the accepted scroll pacing and end-of-list evidence. These changes reduce processing and window overhead; physical-device speed gains remain for user verification.

## 1.2.6 (30)

- Recognize screens from fixed modal, header, footer and tab controls rather than inventory contents or full-screen pixel similarity.
- Recognize enabled and disabled summon controls, including gray buttons and red prices after currency is spent; a price failure no longer hides an independent currency field.
- Restrict control searches to stable UI zones so mount/pet artwork cannot create competing currency bars or buttons.
- Recognize the technology tab as part of the shared research screen for return-home and tab navigation; no upgrade action is added.
- Remove the remaining setup-close pixel-change fallback. Unknown destinations require explicit confirmation after bounded read-only checks; close taps are never repeated blindly.
- Add regressions for changed inventories, currency values and button states, including the actual five-mount /17-key screen.

## 1.2.5 (29)

- Retry uncertain numeric setup reads once on the same rectangle instead of treating OCR ambiguity as invalid geometry.
- Offer explicit value confirmation, same-region retry and optional redraw during setup and scanning when contrast passes disagree; never silently resolve 8/3 ambiguity or preselect an answer.
- Use more of the saved viewport for longer strokes and a slightly faster source-frame budget; keep the independent short tail probes and capture-stall guards.
- Add transport-budget and faster repeated-row regressions; manual speed verification remains with the user.

## 1.2.4 (28)

- Keep the narrow scroll corridor open for a complete inventory read instead of rebuilding touch-shield windows between every stroke; skip input-window waits once the geometry is stable.
- Feed full-detail frames to one shared incremental analysis session for progress and final results, replacing the separate lower-resolution live counter.
- Keep six-frame lookahead for repeated-row ambiguity while processing continuously; finishing flushes pending work rather than replaying the complete recording a second time.
- Open the results screen automatically after the entire automatic route completes successfully. Partial/failing reads still preserve the previous session and never send data automatically.
- Add a 505-item synthetic regression for live/final agreement, plus capture-gap warning coverage.

## 1.2.3 (27)

- Replace cadence-dependent tiny movements with logical passes of about five rows, split into balanced long physical strokes that respect the saved viewport and Android gesture bounds.
- Measure source cadence only while a scroll gesture is moving; idle, release and reposition gaps no longer shrink later movements. Slow capture lengthens a pass rather than reducing its distance to a few pixels.
- Retain shorter controlled probes near a blocked final row, with independent visible-end checks and manual Finish.
- Read mount keys/cost and skill tickets/cost together from the same three fresh source frames and reuse verified costs in the following route step.
- Ignore Samsung launcher's generic transient FrameLayout events while continuing to stop on actual launcher, recents, Settings or other application activities.
- Add value-free debug timing for numerical reads, route steps and capture throughput.

## 1.2.2 (26)

- Propose whole counter bars with their icons and horizontal/vertical headroom, including the complete lower anvil counter for hammers; every proposal still needs confirmation.
- Keep three fresh source frames for numeric verification while avoiding redundant contrast work on clean follow-up frames; conflicting variants use the full verification path.
- Pace longer controlled scrolls from actual fresh-frame intervals instead of repeated tiny gestures; retain viewport overlap and bounded gesture distances.
- Pause scrolling while checking a visible bottom, including a partial final row, rather than continuously bouncing against the list boundary.
- Separate end checks from expensive live counting and retain captured frames for final analysis.
- Reuse the narrow touch-shield corridor during a continued drag while keeping STOP protected and available.
- Preserve pairing and reset only calibration when installing the separate AUTO Test build.

## 1.2.1 (25)

- Make Automatic read the primary home action and retain the original manual overlay under Manual read.
- Show every proposed setup region, including detected close buttons, for confirmation or redrawing before it is saved or used.
- Reuse confirmed counter regions and navigation coordinates during scanning instead of repeatedly detecting layouts or comparing full-screen checkpoints.
- Read counters using a three-frame contrast burst, with one retry on the same saved region when needed; repair remains limited to the failed field.
- Use the manual inventory recording/counting pipeline with controlled scrolling in automatic mode; keep visible progress, STOP, Finish list and estimation warnings.
- Return to the recognized main game screen before repeated reads using saved close controls, without repeating a failed close.
- Keep a prominent hands-off warning and stable STOP panel; shorten and align the setup button.
- Preserve Discord pairing when installing the separate AUTO Test build, resetting only calibration settings.

## 1.0.11 (20)

- Check hammers across three fresh source frames, with original, inverse and white-digit contrast variants.
- Treat contrast variants as one vote per source frame; conflicting or incomplete reads require confirmation, with no preference for the larger number.
- Keep cached MediaProjection images from counting as fresh evidence; release all burst frames and cancel on overlay close or screen changes.

- Move demo mode into a dedicated Options screen, available before overlay permission.
- Add a Help page containing setup, scanning, export and privacy instructions.
- Replace the repeated app capture popup with an inline disclosure immediately before the explicit Start action. Android screen-capture consent remains mandatory.
- Preserve navigation state and restore real results when leaving demo mode.


## 1.0.10 (19)

- Reduce cumulative fractional scroll drift by retaining a reference over several captures; live progress and final analysis use the same policy.
- Preserve incomplete final inventory rows, including the confirmed two-item tail in the 372-item replay.
- Distinguish a stationary image from an older identical reference reached after moving a complete repeated row.
- Measure fast-motion warnings using consecutive movement.
- Keep explicit estimate warnings for ambiguous repeated rows, capture gaps and unconfirmed list ends.

The scanner supports local capture/OCR, reviewed resource export, optional Discord pairing and synchronization, a public offline demo, and separate website/Google Play update channels.
