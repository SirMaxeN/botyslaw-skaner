# Accessibility declaration preparation

Status on 2026-10-29: factual draft for release version 1.3.0 (versionCode 35). This document is not a statement of Google Play approval. Match the submitted bundle and the actual Console questions before completing the declaration.

## Implementation facts

- Botysław Skaner is a Forge Master resource-reading utility. It is not an accessibility tool for people with disabilities; the service configuration sets `isAccessibilityTool="false"`.
- The optional automatic mode uses `AccessibilityService.dispatchGesture` for game-screen navigation and inventory scrolling. Its fixed reading route contains no purchase, summon, forge or merge operation.
- Users see an in-app disclosure and can decline. If accepted, they enable the service in Android settings. Enabling it does not start a scan. Each scan requires a separate user action.
- Setup displays proposed counter, list and navigation rectangles for confirmation or redrawing. Subsequent scans use the saved regions and navigation positions. Limited screen recognition is available for preparation and qualified failure recovery.
- The service subscribes to `TYPE_WINDOW_STATE_CHANGED`. It uses package/class event metadata to restrict gestures to the visible Forge Master package, `com.hariwn.legendofcivilizations`. It does not request accessibility node trees or window contents (`canRetrieveWindowContent="false"`). Event metadata is not sent to the synchronization server.
- OCR and inventory recognition use separately authorized Android MediaProjection screen capture. Raw captures and inventory samples are processed in device memory and are not uploaded. Local configuration can retain geometry and coarse, masked 32 × 64 grayscale layout signatures.
- STOP revokes the current gesture session; an already dispatched bounded gesture may finish before release. Leaving the game, locking the device or stopping capture also stops further gestures. A visible touch shield discourages interference but its temporary gesture corridor and Android system controls remain reachable.
- Manual read remains available without the accessibility service. Both modes require screen-capture consent for actual game scanning.
- Completed results open for review. Only a separate user-confirmed synchronization sends numeric resources/costs to the paired Discord account. The public demo uses local sample results and simulated sending; it does not demonstrate live automation or transmit demo resources.

## Suggested description for the Console

Botysław Skaner is a game resource-reading utility, not an accessibility tool. Its optional, user-started automatic scan uses AccessibilityService gestures to open Forge Master screens and scroll inventory lists. Users confirm or redraw reading areas and navigation controls during setup. The service receives foreground window-state package metadata to restrict gestures to the game and cannot retrieve accessibility window contents. Screen recognition uses separately authorized MediaProjection capture and local processing. The fixed route does not purchase, summon, merge or forge items. STOP ends the sequence after the current bounded gesture; leaving the game or locking the device revokes further gesture authorization. Manual scanning works without this service. Discord synchronization sends reviewed numeric results only after user action.

## Review video: recorded, hosted and declaration saved

On 2026-10-29, the factual AccessibilityServices declaration was saved in Play Console for 1.3.0 (35): App functionality selected, no personal/sensitive data collected or shared through AccessibilityServices, and the reviewed video URL entered. Both undeclared-permission release errors disappeared. This is not Google approval.

Reviewer page: https://komars.eu/botyslaw-skaner/accessibility-demo.html (HTTP 200). Direct MP4: https://komars.eu/botyslaw-skaner/accessibility-demo.mp4 (HTTP 200, video/mp4, 9,108,964 bytes, duration 177 seconds). The real disclosure/decline/re-entry/settings recording is combined with the user-provided setup and scan recording. English captions were added; unrelated game chat was masked and audio removed. The setup/scan segment uses the separate AUTO Test installation of the same release source. STOP and Finish list are visible; no separate STOP demonstration is claimed. Results are shown before synchronization.

The existing `foreground-service-demo.html` clip demonstrates screen-capture disclosure/consent, overlay, foreground notification and stopping capture. It is a capture-only reference from the earlier UI, not evidence of the new accessibility workflow. Do not use it as if it showed automatic navigation or service consent.

The following is the full future-recording checklist; it is not a claim that every optional demonstration below appears in the current clip. Do not fabricate service screens, game actions or successful counts:

1. Start from disabled gesture control and open **Automatyczny odczyt**.
2. Show the full in-app accessibility explanation and the affirmative/decline choices. Demonstrate that declining leaves manual mode available.
3. Accept, open the actual Android service settings and explicitly enable **Botysław: automatyczny skan**. Return to the app and start automatic mode again.
4. Show the separate Android MediaProjection prompt and select only Forge Master.
5. In the real game, show setup proposals, a user confirmation/redraw, and the hands-off warning. Do not conceal limitations or claim every layout is supported.
6. Press Scan and show actual saved navigation plus inventory scrolling. Keep STOP and Finish list visible. Demonstrate STOP and the end of further gestures; do not spend game resources.
7. Show that leaving the foreground game stops the run, and show the persistent capture notification/its stop control.
8. Show the review screen before any send action. Use an unpaired or explicitly isolated test session; do not send test results to a real clan.

Recordings must exclude account credentials, pairing codes, private chats and unrelated personal notifications. Upload/host the real recording and verify its reviewer access before replacing the pending status above.

## Proposed Polish release notes

The following text contains 399 characters, within the 500-character limit:

Nowy automatyczny odczyt: po jednorazowym zatwierdzeniu pól aplikacja otwiera kolejne ekrany i przewija listy wierzchowców oraz zwierzaków lub jajek. Dodaliśmy propozycje obszarów, szybszy odczyt liczników i list oraz przyciski STOP i „Zakończ listę”. Manualny odczyt pozostaje dostępny. Automatyzacja wymaga osobnej zgody na sterowanie gestami; wyniki zawsze sprawdzasz przed wysłaniem do Discorda.

## Update availability disclosure

The Play build queries official Google Play update availability, offers an app-listing link and does not start the SDK download/install flow. Google documents device metadata, app version and installed modules/asset packs used for update requests. Do not call this anonymous. Sources checked on 2026-10-29: [Play In-App Updates, Data safety](https://developer.android.com/guide/playcore/in-app-updates#data-safety) and [Google privacy policy](https://policies.google.com/privacy).


On 2026-10-29 the Alpha release 1.3.0 (35) was submitted through the publishing overview. Console confirmed "Changes in review" while preliminary automated checks were still running; review submission will proceed after those checks. This is not an approval or confirmation of availability to testers.
