# Przegląd skanera — 5 listopada 2026

Zakres: konfiguracja i przechwytywanie obrazu, zasoby i ich nawigacja naprawcza, pojedyncze oraz masowe profile, lista członków, zapis częściowy, wznowienie, anulowanie i przekazanie sterowania między nakładkami. Bez przebiegu po grze i bez publikowania raportów na Discordzie.

## Co pokazują rzeczywiste logi

Około 00:04 skaner otworzył właściwy klan. Pierwszy OCR zwrócił `50/5o`; drugi, kontrastowy, zwrócił prawidłowe `50/50`. Zapisano kompletne profile DzikiRoman i XlawoK. Zatrzymania nastąpiły dopiero podczas zamykania profili. Screen 00:05:34 pokazuje nakładkę na dole profilu, zasłaniającą obszar czerwonego X. Wcześniejszy problem nagłówka został więc odzyskany przez Test 66; obecny błąd dotyczy innej granicy między modułami.

## Znalezione i poprawione problemy

| Waga | Problem | Zmiana w Test 67 |
| --- | --- | --- |
| Wysoka | Zamknięcie profilu szukało czerwonego X na obrazie zasłoniętym własnym panelem. | Przy otwieraniu profilu istniejący czysty obraz dostarcza pozycję X. Po udanym skanie zamknięcie wykorzystuje tę pozycję, przypisaną do konkretnej osoby i wymiarów obrazu. Panel odsuwa się przed kliknięciem. |
| Wysoka | Wznowienie analizowało obraz z panelem i po braku rozpoznania przechodziło przez klan/wojnę zamiast zamknąć aktualny profil. | Wznowienie i awaryjne zamykanie pobierają czysty obraz. Rozpoznany profil wraca na listę; nieznany ekran zatrzymuje się z zachowaniem zapisu, zamiast zgadywać przejście. |
| Wysoka | Zapis wróżki Mira z poziomem, ale bez redukcji krytycznej, mógł kończyć kolejne próby tym samym niekompletnym profilem. | Ponowne użycie wymaga efektu. Niekompletny opis jest odczytywany ponownie. To samo dotyczy brakującego efektu Lory. |
| Średnia | W starych częściowych zapisach sama obecność tekstu broni oznaczała również kompletne bonusy. | Kompletność broni i bonusów są ustalana oddzielnie. Jawnie zapisane etapy zachowują pierwszeństwo. |
| Średnia | Dwie identyczne nowe karty w jednym odczycie mogły dostać dwa indeksy członków. | Cały widok jest sprawdzany przed zmianą rejestru. Powtórzenie awatara i grafiki nicku odrzuca widok bez dopisania osób. |
| Średnia | Odpowiedź gestu po anulowaniu i rozpoczęciu nowej sesji mogła zmienić stan wznowienia. | Dodatkowe sprawdzenia aktywności i numeru sesji przy uzbrojeniu wznowienia i awaryjnym zamykaniu. |
| Średnia | Masowy skan wielokrotnie zapisywał identyczny stan przy przechodzeniu przez zapisane umiejętności. | Pomijanie identycznych punktów zapisu, z wyłączeniem zmiennego znacznika czasu. Zmiana danych lub etapu nadal zapisuje się natychmiast. |
| Średnia | Naprawa nawigacji zasobów mogła rozpoznawać ekran z własną nakładką. | Tylko w tej ścieżce pobierany jest świeży obraz bez panelu, z ochroną przed spóźnionymi odpowiedziami. Normalna nawigacja zapisanymi przyciskami pozostaje bez dodatkowego rozpoznawania. |

## Granice zmian

Nie resetowano kalibracji, danych Discorda, częściowych profili ani aktywnego zapisu wojennego. Nie zmieniono modelu walki, zasad dobierania celów, terminów wojny ani serwera. Komunikat „Bot skanuje” pozostaje podczas pracy; jego obszar nadal usuwa warstwa przechwytywania. Czysty obraz potrzebny jest przy rozpoznawaniu całego okna i odzyskiwaniu stanu, a nie po każdym normalnym kliknięciu.

Nowe testy obejmują tożsamość i rozmiar zapamiętanego X, niekompletny efekt wróżki oraz atomowe odrzucanie powtórzonych kart. Test statyczny telefonu porównuje rzeczywisty screen błędu ze znanym czystym profilem oraz zasłoniętym kontrolnie X. Nie jest to dowód ukończenia rzeczywistego skanu 100 postaci. Zapisane dane są używane tylko zgodnie z dotychczasową tożsamością klanu, postaci, mocą i ograniczeniem świeżości.

W logach szukanie badań zajmuje około 20 sekund. Ten przegląd nie zmienia gestów ani wyszukiwania drzewa technologii; ich faktyczne przyspieszenie wymaga osobnego pomiaru przebiegu. Nie potwierdzono przyczyny ewentualnych błędów procentu badań na podstawie nowego screena — przedstawia on profil członka, a nie opis badania.

Weryfikacja Test 67: 482 przypadki, 453 zaliczone, 29 opcjonalnych pominiętych, zero błędów. Włączono dodatkowe zapisane ekrany własnego profilu, dwóch przeciwników, broni i ekranu głównego. Lint: 0 błędów, 240 ostrzeżeń. Audyty obu repozytoriów bez wykrytych problemów. Testy natywne zaliczyły rzeczywisty screen z zasłoniętym X, czysty profil z rozpoznanym X, odrzucenie użycia tego X dla innej osoby/niezakończonych szczegółów, wznowienie procesu, zapis i macierz 1×1/cache (1266 ms), odłączony układ panelu, konstrukcje gestów oraz trzy próby nagłówka: zwykły, kontrast i pusty. Nie wysyłano gestów do gry ani raportów. Instalacja Test 67 przez -KeepCalibration; hashe trzech plików kalibracji i pełnych profili identyczne przed/po instalacji i po testach. Aktywny zapis wojenny nie był czyszczony. Artefakt: releases/Botyslaw-WALKA-Test-67.apk. Pełny rzeczywisty przebieg nadal sprawdza użytkownik.
