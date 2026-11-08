# Botysław Skaner — iOS

Wszystko, co dotyczy wersji na iPhone'a, jest w tym folderze. Pełny plan opisuje [PLAN.md](PLAN.md).

Zakres: **skan zasobów** i **symulacja walki 1 na 1**. Bez listy wojennej i rankingu.

Gracz włącza nagrywanie ekranu przez Botysława, a aplikacja prowadzi go powiadomieniami z dźwiękiem.

## Stan
To szkielet. Pliki Swift zawierają tylko strukturę i komentarze `TODO`. Logika analizy, czyli wykrywanie ekranów, czcionka gry i silnik walki, trafi do wspólnego modułu Kotlin Multiplatform `:shared` (Etap 1 planu). Na iOS zostanie dołączona jako `BotyslawShared.xcframework`.

## Struktura
| Ścieżka | Co to jest |
|---|---|
| `project.yml` | Opis projektu dla [XcodeGen](https://github.com/yonaskolb/XcodeGen). Z niego na Macu powstaje `.xcodeproj`. |
| `BotyslawSkaner/` | Aplikacja (SwiftUI): parowanie z Discordem, start skanu, wyniki, walka, opcje. |
| `BroadcastExtension/` | Rozszerzenie ReplayKit (Broadcast Upload Extension), które dostaje klatki ekranu i je analizuje. |
| `Shared/` | Kod Swift wspólny dla aplikacji i rozszerzenia: powiadomienia, OCR Apple Vision, klient serwera bota. |

## Jak zbudować na Macu
1. Zainstaluj Xcode z App Store, a potem `brew install xcodegen`.
2. Zbuduj wspólny moduł w katalogu głównym repo, gdy już powstanie (Etap 1):
   ```bash
   ./gradlew :shared:assembleBotyslawSharedXCFramework
   ```
3. W folderze `ios/` wygeneruj projekt i otwórz go:
   ```bash
   xcodegen generate && open BotyslawSkaner.xcodeproj
   ```
4. W Xcode wybierz **Signing & Capabilities → Team**, czyli swoje konto Apple Developer. Zrób to dla obu targetów.
5. Do testu w symulatorze wybierz *iPhone 16* i kliknij Run. W symulatorze nie ma ReplayKit ani gry, więc użyj trybu debug „Źródło: nagranie”.
6. Wydanie: **Product → Archive → Distribute App → App Store Connect**, a potem TestFlight.

## Identyfikatory
- Aplikacja: `com.sirmaxen.BotyslawSkaner`, czyli dokładnie `applicationId` z Androida, z tym samym przedrostkiem co WhoStarts (`com.sirmaxen.WhoStarts`). `eu.komars.forgescanner` to tylko pakiet kodu, nie identyfikator.
- Rozszerzenie: `com.sirmaxen.BotyslawSkaner.broadcast`.
- App Group: `group.com.sirmaxen.BotyslawSkaner`.
- Serwer bota: `https://komars.eu/discord/botyslaw-smith/mobile/` (trasy `pair` i `resources`, tak jak na Androidzie).
