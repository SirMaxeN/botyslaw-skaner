# Botysław Skaner na iOS i iPadOS

Pierwsza wersja testowa 0.1.0 (3) obejmuje skan zasobów, liczenie przewijanych list, wysyłkę na Discorda i symulację walki 1 na 1. iPadOS prowadzi przez transmisję systemową, a gracz otwiera i przewija widoki ręcznie. Brakujące lub niepewne odczyty można poprawić przed użyciem. Zakres i ograniczenia opisuje [plan](PLAN.md).

Odczyty powstają z dwóch kolejnych próbek. Dane odczytu trafiają do App Group, a klucz parowania do systemowego pęku kluczy aplikacji. Wysyłka odbywa się dopiero po zatwierdzeniu wyniku przez gracza. Klatki nie są zapisywane do pliku ani wysyłane.

## 1. Przenieś źródła na Maca

Skopiuj do jednego katalogu `botyslaw-skaner` katalogi `ios`, `shared` (bez `shared/build`) i `gradle` oraz pliki `gradlew`, `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`. Użyj bieżących plików z Windowsa, w tym nowych plików w `ios/Shared` i `shared/src`. Możesz przenieść je prywatnym dyskiem, przez sieć lokalną albo własne repozytorium.

Do tego uruchomienia nie są potrzebne katalogi `.signing`, `.local`, `.gradle`, `.kotlin`, `releases`, `app` ani żadne pliki z kodami parowania, logami lub nagraniami.

## 2. Zbuduj projekt na Macu

1. Zainstaluj Xcode obsługujący wersję iPadOS na tablecie. Otwórz go raz, żeby zakończyć instalację komponentów. Potrzebne są też JDK 17 i XcodeGen. Jeśli używasz Homebrew, zainstalujesz XcodeGen przez `brew install xcodegen`.
2. W Terminalu, w katalogu `botyslaw-skaner`, uruchom:

   ```sh
   export JAVA_HOME=$(/usr/libexec/java_home -v 17)
   bash ./gradlew -PiosOnly :shared:assembleBotyslawSharedReleaseXCFramework
   cd ios
   xcodegen generate
   open BotyslawSkaner.xcodeproj
   ```

3. W Xcode w **Settings → Accounts** dodaj swoje konto Apple. W **Signing & Capabilities** włącz automatyczne podpisywanie dla **BotyslawSkaner** i **BroadcastExtension**. W obu wybierz ten sam zespół i sprawdź, czy mają przypisaną tę samą grupę `group.com.sirmaxen.BotyslawSkaner`. Zespół musi mieć dostęp do tych identyfikatorów i do App Groups. Jeśli Xcode odrzuci możliwości podpisu na Personal Team, użyj zespołu z dostępem do App Groups. Bez tego rozszerzenie nie przekaże odczytu aplikacji. [Konfiguracja App Groups w Xcode](https://developer.apple.com/documentation/xcode/configuring-app-groups)

## 3. Uruchom na iPadzie bez TestFlight

1. Podłącz odblokowanego iPada kablem do Maca. Potwierdź zaufanie do komputera, jeśli urządzenie o to poprosi.
2. Po rozpoczęciu parowania w Xcode na iPadzie wejdź w **Ustawienia → Prywatność i ochrona → Tryb dewelopera**, włącz go, uruchom tablet ponownie i potwierdź. [Tryb dewelopera na iPadOS](https://developer.apple.com/documentation/xcode/enabling-developer-mode-on-a-device)
3. W Xcode wybierz schemat **BotyslawSkaner**, jako urządzenie wybierz iPada, a następnie naciśnij **Run** (⌘R). Zwykłe uruchomienie z Xcode instaluje aplikację lokalnie. [Uruchamianie aplikacji na urządzeniu](https://developer.apple.com/documentation/xcode/running-your-app-on-simulated-or-physical-devices)

## 4. Pierwsza próba odczytu

1. Otwórz Botysława na iPadzie, zezwól na powiadomienia, naciśnij systemowy przycisk transmisji i wybierz **Botysław**. Przełącz się do Forge Master w pionie, na pełnym ekranie.
2. Zostań chwilę na ekranie głównym. Otwórz wierzchowce od góry listy. Pokaż klucze i koszt ×1, po czym przewijaj spokojnie, zostawiając wspólny rząd między widokami. Zatrzymaj się chwilę na dole i zamknij listę. Pokaż bilety i cenę ×5 w Umiejętnościach, a potem analogicznie przewiń listę Zwierzaków. Opcjonalnie wejdź do Drzewa technologii po liczniki fiolek. Jeśli nie pojawiają się powiadomienia, przejdź dalej ręcznie.
3. Zatrzymaj transmisję w Centrum sterowania i wróć do aplikacji. Porównaj odczyty z ekranem gry. Sprawdź, czy szczególnie młotki, koszty i liczniki z sufiksem `k`/`m` mają prawidłowe wartości. Sprawdź liczbę wierzchowców (bez jednego bazowego) i zwierzaków lub jajek (bez trzech bazowych). Jeśli wcięło rząd lub pole pozostało puste, popraw liczbę albo zeskanuj ponownie wolniej. Pierwszy test można zrobić bez wysyłania.
4. Jeśli chcesz sprawdzić synchronizację, wpisz kod z Discorda z `/mobile pair`. Nowe parowanie zastępuje poprzednie połączenie tego samego konta na tym serwerze. Do prób użyj konta testowego. Po zatwierdzeniu wysyłki porównaj liczby z `/report resources`.

## 5. Pierwsza próba walki

1. W aplikacji wybierz **Symulacja walki 1 na 1 → Moja postać**. Rozpocznij transmisję, przejdź do Forge Master i otwórz własny profil. Poczekaj na odczyt łącznych obrażeń i zdrowia, a następnie powoli przewiń bonusy, zatrzymując się na każdym widoku.
2. Zakończ transmisję w Centrum sterowania. Wróć do aplikacji, wybierz **Przeciwnik** i powtórz skan jego profilu w osobnej transmisji.
3. Potwierdź lub popraw zdrowie, obrażenia, odczytane bonusy i ich limity. W obu profilach wybierz broń, liczbę wyposażonych zwierzaków i wierzchowca. Wpisz posiadane umiejętności z poziomami, mistrzostwo, wzniesienie i wróżkę, jeśli są wyposażone. Te szczegóły wybiera się ręcznie w pierwszym wydaniu.
4. Naciśnij **Symuluj walkę 1 na 1**. Wynik powinien pokazać procent wygranych, przegranych i remisów. Symulacja działa lokalnie i nie wysyła profili do Discorda.

### Co zebrać przy pierwszym teście

Jeśli Xcode zgłosi problem, zapisz pierwszy błąd z **Issue Navigator** wraz z nazwą targetu. Jeśli odczyt się nie pojawi, zanotuj model iPada, wersję iPadOS, ekran gry, który nie zadziałał, i czy pojawiły się powiadomienia. Kompilacja Swift, geometria prawdziwych klatek i działanie ReplayKit w tle wymagają sprawdzenia na Macu i iPadzie.
