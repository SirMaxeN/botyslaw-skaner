# Botysław Skaner na iOS — plan

## Kontekst
Na Androidzie aplikacja ma panel nad grą i automatyczne klikanie. iOS nie pozwala na żadne z nich. Da się za to nagrywać ekran przez ReplayKit (Broadcast Upload Extension) i analizować klatki na żywo.

Zakres iOS:
- **skan zasobów** (młotki, wierzchowce, zwierzaki, umiejętności, badania);
- **symulacja walki 1 na 1**;
- **bez** listy wojennej i rankingu, które zostają na Androidzie i na Discordzie (`/report`).

Gracz włącza nagrywanie raz na cały skan i sam przechodzi po ekranach. Aplikacja mówi mu, co robić, przez powiadomienia z dźwiękiem, np. „✅ Młotki: 6660 → otwórz wierzchowce”.

Ustalenia z użytkownikiem:
- logika jest współdzielona przez **Kotlin Multiplatform**;
- budowanie odbywa się w **Xcode na Macu** użytkownika, a stamtąd trafia do App Store i TestFlight;
- konto Apple Developer użytkownik dopiero założy;
- na razie nikt nie testuje na iPhonie, więc wszystko sprawdzamy na nagraniach, w symulatorze Xcode i w symulatorze na Windowsie.

## Identyfikatory (sprawdzone 2026-11-08)
- **Bundle ID = Android `applicationId`: `com.sirmaxen.BotyslawSkaner`** (`app/build.gradle.kts`). Przedrostek `com.sirmaxen.` jest ten sam co w WhoStarts (`com.sirmaxen.WhoStarts`).
- `eu.komars.forgescanner` to tylko pakiet kodu Kotlin (`namespace`). **Nie** jest to identyfikator aplikacji.
- Rozszerzenie nagrywania: `com.sirmaxen.BotyslawSkaner.broadcast`. App Group: `group.com.sirmaxen.BotyslawSkaner`.

## Co już mamy (z przeglądu kodu)
- **Czysty Kotlin**, bez Androida, działa na `IntArray` ARGB albo `GrayScrollFrame`:
  - wykrywanie ekranów: `model/GameUiGeometryDetector.kt` (`screenKind()`), `GridGeometryDetector`, `InventoryEndDetector`, `ResearchWindowGeometry`;
  - czcionka gry: `battle/GameDigitFont.kt`, `model/GameNumberParser.kt`, `vision/CounterDigitMask.kt`;
  - uzgadnianie odczytów z kilku klatek: `vision/CounterOcrConsensus.kt`, `HammerOcrConsensus.kt`, `OcrChoices.kt`;
  - liczenie przy przewijaniu: `model/ScrollSequenceAnalyzer.kt`, `ScrollAnalysisSession.kt`, `InventoryTracker.kt`, `SourceFrameSequence.kt`;
  - kroki i model skanu: `automation/ResourceSetupScreens.kt`, `AutoScanPlan.kt`, `model/ScanSession.kt` (`ResourceField`);
  - walka: `battle/BattleModel.kt`, `PvpBattleEngine.kt`, `AscensionStars.kt`, `ShortBonusList.kt`, `IconThumbnails.kt`, `SkillIconMatcher.kt`, pliki wróżek i geometrii.
- **Zależne od JVM, do drobnej zamiany:**
  - `java.text.Normalizer` w BattleModel;
  - `BigDecimal` w GameNumberParser;
  - `String.format` w GameDigitFont;
  - `java.util.ArrayDeque` i `Long.bitCount`;
  - `CancellationException`;
  - `System.currentTimeMillis`.
  
  Zastąpimy je wywołaniami `expect/actual` albo czystymi odpowiednikami w Kotlinie.
- **Zależne od Androida** (zostają w `:app`, a na iOS powstają odpowiedniki w Swifcie):
  - ML Kit (`vision/OcrAnalyzer.kt`, `BattleOverlay.kt`, `BattleFieldDetector.kt`, `CapturedTextReader.kt`) → na iOS **Apple Vision** (`VNRecognizeTextRequest`);
  - przechwytywanie ekranu (`capture/FrameCaptureService.kt`) → na iOS **ReplayKit**;
  - nakładki;
  - `MobileSyncClient` → na iOS `URLSession` i Keychain, z tymi samymi trasami `pair` i `resources` na `https://komars.eu/discord/botyslaw-smith/mobile/…`.
- Testy JUnit na zrzutach `.argb` z `.local/` (fixtures) przeniosą się do `shared` jako testy commonTest i jvmTest.

## Krok 0 — ✅ zrobione 2026-11-08
1. Zapisać plan w repo jako `botyslaw-skaner/ios/PLAN.md`.
2. Utworzyć osobny folder `botyslaw-skaner/ios/`. To wszystko, co potem trzeba przenieść do Xcode:
   ```
   ios/
     PLAN.md                    ← ten plan
     README.md                  ← jak otworzyć na Macu: xcodegen → Xcode → Archive → App Store
     project.yml                ← XcodeGen: target aplikacji + Broadcast Upload Extension + App Group
     BotyslawSkaner/            ← aplikacja (SwiftUI)
       BotyslawSkanerApp.swift, ContentView.swift (Start skanu / Walka / Discord / Opcje)
       Info.plist, BotyslawSkaner.entitlements (App Group group.com.sirmaxen.BotyslawSkaner)
     BroadcastExtension/        ← ReplayKit
       SampleHandler.swift (szkielet: klatka → pomniejszenie → kolejka analizy)
       Info.plist, BroadcastExtension.entitlements
     Shared/                    ← Swift wspólny dla obu targetów
       Notifier.swift (powiadomienie z dźwiękiem przy zmianie kroku)
       VisionOcr.swift (szkielet Apple Vision)
       SyncClient.swift (pair/resources, Keychain)
   ```
   Pliki Swift na tym etapie to szkielety z komentarzami TODO. Nie da się ich skompilować na Windowsie.
3. Commity, bez pushowania, na gałęzi `master` w obu repo:
   - **polska-moc-bot**: niezacommitowane zmiany (promote, copy tylko z oficjalnej listy, podgląd w niedzielę i inne);
   - **botyslaw-skaner**: wersje 1.4.6–1.4.8 (poprawki listy, przesuwany panel zasobów, changelog), a w osobnym commicie folder `ios/`.

## Etap 1 — wspólny moduł `:shared` (Windows) — ✅ zrobione 2026-11-08
Co powstało:
- Moduł `shared/` (Kotlin Multiplatform 2.0.21) ma cel `jvm()`, z którego korzysta Android (`:app` zależy od `project(":shared")`), oraz cele `iosArm64()` i `iosSimulatorArm64()`. Z nich powstaje framework `BotyslawShared` (statyczny, w formie XCFramework). Na Windowsie cele iOS są pomijane dzięki `kotlin.native.ignoreDisabledTargets=true`.
- **86 plików** czystej logiki przeniesiono przez `git mv` do `shared/src/commonMain/kotlin` bez zmiany pakietów. Obejmują rozpoznawanie ekranów, czcionkę gry, uzgadnianie OCR, liczenie przy przewijaniu, kroki skanu, model walki i `PvpBattleEngine`.
- W `app` zostaje 14 plików czystych, ale niepotrzebnych na iOS (wojna, gesty, aktualizacja z Play), a także wszystko, co zależy od Androida.
- Wydzielone, żeby logika nie zależała od Androida:
  - `model/CalibrationModel.kt` (z `CalibrationStore.kt`);
  - `vision/OcrModel.kt` (modele OCR i `OcrCandidateExtractor`, wydzielone z `OcrAnalyzer.kt`);
  - `model/CounterConfirmations` (haczyk nauki cyfr, który ustawia `GameDigitMemory.shared`).
- API z JVM zamienione na wspólne:
  - `platform/Platform.kt` przez `expect/actual`: `normalizeNfd`, `nowMillis`, `formatFixed4`; wersja dla JVM to dokładnie te same wywołania co wcześniej;
  - `platform/ExactMath.kt`: `addExact` i `multiplyExact` z tym samym algorytmem co w JDK;
  - `GameNumberParser` bez `BigDecimal`, z dokładnym `GameDecimal`; `GameNumberParserParityTest` porównuje go ze starą wersją na 300 tys. losowych napisów;
  - pamięć podręczna LRU w `GrayRegionFrameCache` bez `accessOrder`;
  - `kotlin.collections.ArrayDeque`, `countOneBits()`, `kotlin.math.PI`, `kotlin.coroutines.cancellation.CancellationException`.
- Deklaracje `internal` w `shared` stały się publiczne, żeby `:app` je widziało. Działanie się nie zmienia.

**Do zrobienia na Macu przy pierwszym buildzie iOS:**
- Uruchomić `./gradlew :shared:assembleBotyslawSharedXCFramework`. Plik `shared/src/iosMain/.../Platform.kt` nie był jeszcze kompilowany, bo Kotlin/Native dla iOS działa tylko na macOS.
- Sprawdzić `Regex` w Kotlin/Native (lookbehind, `\p{L}`) testami `commonTest`.
- Dane potrzebne logice (`game_digits_v1.txt`, `battle_skills/`, wróżki `*.json`) na razie zostają w `app/src/main/assets`. Na iOS trafią do pakietu aplikacji i będą przekazywane do `shared` jako tekst (Etap 4).

## Etap 2 — scenariusz z instrukcjami (shared)
- Nowa maszyna stanów `GuidedRecordingFlow` w `shared`. Dostaje klatkę i jej wynik, a zwraca zdarzenia: `Prompt(text, sound)`, `StepDone(field, value)`, `Problem(text)`, `Finished(result)`.
- Kolejność kroków:
  1. ekran główny (młotki, bilety, klucze);
  2. wierzchowce;
  3. zwierzaki;
  4. umiejętności;
  5. badania.
  
  Do tego przewijanie list, które liczy już `ScrollAnalysisSession`.
- Wyniki z kilku klatek uzgadniamy tymi samymi klasami Consensus co na Androidzie. Powiadomienie wysyłamy tylko przy zmianie kroku lub przy problemie, a do tego obowiązuje przerwa między powiadomieniami.
- Symulacja walki: gracz otwiera swój profil, potem profil rywala. Flow czyta je tym samym kodem co szybki odczyt 1 na 1 i daje wynik `PvpBattleEngine`.

## Etap 3 — symulator na Windowsie
- Narzędzie JVM w `shared/jvmTest` albo jako zadanie Gradle `:shared:replay`. Bierze nagranie (klatki wyciągnięte przez ffmpeg) albo folder `.argb` i puszcza je przez `GuidedRecordingFlow`. Wypisuje powiadomienia, jakie dostałby gracz, i końcowy wynik.
- Klatki przeskalowujemy do proporcji iPhone'a (np. 1179×2556) i do szerokości analizy 360 px, żeby sprawdzić reguły geometrii przy innych proporcjach ekranu.
- Testy regresji na `.local/` (zrzuty z Androida) przechodzą także po przeskalowaniu.

## Etap 4 — aplikacja iOS (Swift, na Macu w Xcode)
- **Aplikacja (SwiftUI):**
  - parowanie z Discordem (ten sam kod co na Androidzie);
  - przycisk „Rozpocznij skan”, który pokazuje `RPSystemBroadcastPickerView` z wybranym naszym rozszerzeniem;
  - ekran wyników i wysłanie ich na `resources`;
  - ekran walki z wynikiem;
  - opcje z wersją aplikacji.
- **Broadcast Upload Extension:**
  - w `processSampleBuffer` co około 150 ms pomniejsza klatkę (vImage) do 360 px i przerabia ją na `IntArray` ARGB;
  - tam, gdzie Android używa ML Kit, wywołuje `VNRecognizeTextRequest`;
  - potem woła `GuidedRecordingFlow` z frameworka `BotyslawShared`;
  - powiadomienia lokalne pokazuje przez `UNUserNotificationCenter` z dźwiękiem; trzeba sprawdzić, czy rozszerzenie może je wysyłać, a jeśli nie, przekazuje je przez App Group i aplikację;
  - po zakończeniu kończy transmisję przez `finishBroadcastWithError` z komunikatem „Gotowe, wróć do Botysława”.
- **Limit pamięci rozszerzenia** to około 50 MB (do potwierdzenia w dokumentacji Apple). Dlatego trzymamy tylko pomniejszone klatki i nie przechowujemy wideo.
- **Wspólne dane aplikacji i rozszerzenia** idą przez App Group `group.com.sirmaxen.BotyslawSkaner`: token w Keychain z access group, wyniki w pliku JSON.
- **Tryb symulatora Xcode:** symulator nie ma ReplayKit ani gry, więc w aplikacji jest przełącznik debug „Źródło: nagranie”. Przepuszcza wybrany plik wideo (AVAssetReader) przez ten sam kod co rozszerzenie. W symulatorze sprawdzamy w ten sposób UI, powiadomienia i cały przebieg.

## Etap 5 — Apple i wydanie
- Konto Apple Developer, Bundle ID `com.sirmaxen.BotyslawSkaner` (jak na Androidzie) z rozszerzeniem `.broadcast`, App Group, a w Xcode podpisywanie automatyczne.
- Na Macu: `./gradlew :shared:assembleBotyslawSharedXCFramework`, potem `xcodegen`, potem Xcode → Archive → App Store Connect → TestFlight.
- W App Store Connect: opis po polsku, polityka prywatności (strona jak dla Androida) i notatka dla recenzji, że nagrywanie służy tylko do odczytu własnych zasobów, a wideo nie jest zapisywane.
- Pierwszy test na iPhonie przez TestFlight, gdy znajdzie się tester. Logi diagnostyczne trafiają do bota.

## Weryfikacja
- **Krok 0:** `git status` w obu repo jest czysty, a `git log` pokazuje nowe commity.
- **Etap 1:** `:app:testDebugUnitTest :app:lintPlay` i testy `:shared:jvmTest` przechodzą. AUTO Test na Androidzie daje te same wyniki co przed zmianą (test ręczny użytkownika).
- **Etap 3:** symulator na nagraniach `local-artifacts/*.mp4` daje te same liczby co skan na Androidzie.
- **Etap 4:** na Macu `xcodebuild` dla symulatora iPhone kończy się sukcesem, a tryb „Źródło: nagranie” przechodzi cały skan.

## Ryzyka
- Brak testera z iPhone'em: proporcje i jakość klatek z iPhone'a znamy tylko z przeskalowania.
- Recenzja App Store może dopytywać o nagrywanie ekranu innej aplikacji.
- Nie wiadomo jeszcze, czy powiadomienia z rozszerzenia działają i czy limit pamięci wystarczy. Oba punkty trzeba potwierdzić na pierwszym urządzeniu.
