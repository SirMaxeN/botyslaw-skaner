# Audyt wymagań — Test 27

Porównanie z rozbudowaną listą użytkownika i późniejszą uwagą o ikonach zasobów.

| Wymaganie | Stan i weryfikacja |
|---|---|
| Poziom umiejętności: ponowienia z kontrastem | Oryginał + 3 progi kontrastu. Test 27 zachowuje odczytaną nazwę/poziom pomiędzy próbami; sprzeczności wymagają ręcznej korekty. Testy SkillReadEvidenceTest. |
| Kwadratowe, wyśrodkowane ikony | Wspólny ScannerIconBar.button dla walk, automatu i ręcznego skanera. Wyłączone wyrównanie do linii tekstu, dopasowanie rozmiaru do szerokości. |
| Działanie ikon zasobów | Konfiguracja faktycznie rozpoczyna/wznawia krok, skan uruchamia start, rozwiń używa wspólnego ⛶, wyniki otwierają aplikację i kończą przechwytywanie, X kończy skaner. |
| Aktualizacja przycisków po konfiguracji | BattleActivity odtwarza menu w onResume. Nie pokazuje symulatora, jeśli konfiguracja nadal jest niepełna. |
| Stały wskaźnik pracy | Mały wskaźnik aktywnej sesji przechwytywania w prawym górnym rogu. Obszar maskowany przed analizą klatki; bez dodatkowego kopiowania całego bitmapowego obrazu. |
| Przyciski formularza danych | Osobna pionowa stopka w treści edytora, poza przewijanymi polami. Ograniczony rozmiar przewijanej treści, uwzględnienie miejsca na klawiaturę. |
| Powrót do aplikacji | Menu walk oraz oba skanery zasobów uruchamiają aplikację i kończą przechwytywanie. |
| Stała stopka wsparcia | SupportFooter współdzielony przez menu główne, zasoby i walki. |
| Informacja o udostępnianiu | Widoczna na ekranach sekcji. W walkach usunięty dodatkowy dialog aplikacji; systemowa zgoda Androida pozostaje. |
| Nagłówki sekcji | Zasoby i walki mają osobne oznaczenia i nagłówki. Nie generowano nowych bitmap; imagegen był opcjonalny. |
| Zapis zasobów i historia kosztów | Test 27 zapisuje częściowe odczyty oraz poprawki liczbowego szkicu. Odtwarza datę i flagi zatwierdzenia. Koszty potwierdzone wspierają wyłącznie obecne kandydatury OCR, nigdy brakujący odczyt. Testy ResourceDraftRestoreTest i ConfirmedCostPolicyTest. |
| Automatyczny skaner w ikonach | Dostępna widoczna akcja zwinięcia i cztery ikony. Podczas aktywnego automatu pozostaje panel z STOP, analogicznie do ograniczeń aktywnego skanu walki. |
| Mniej przesuwania nakładki | Zachowuje położenie, gdy nie zasłania kolejnego obszaru. |
| Miganie podczas wykrywania | Ograniczone; nie wyeliminowane całkowicie. Duży panel może krótko znikać do czystej klatki, stały wskaźnik pozostaje widoczny. |
| Powrót po wysłaniu zasobów | Udana wysyłka bieżącej sesji zamyka odczyt i wraca do wyboru trybu zasobów. Walki pozostają dostępne do kolejnego przeciwnika. |
| Widoczne Menu główne | Pełny przycisk w sekcji zasobów. |
| Badania po innym zaleceniu gry | Rozpoznanie aktualnego widoku; przy braku ikon powrót ku górze z wykrywaniem braku ruchu; wyszukiwanie w dół, z limitem i ręczną korektą po niepowodzeniu. Aktualne wykryte pozycje zastępują stare współrzędne. |

## Granice weryfikacji

Testy lokalne obejmują logikę, statyczną kontrolę Androida oraz dostępne klatki gry. Nie zastępują przebiegu na telefonie z trudnym tooltipem umiejętności, klawiaturą, różnymi skalami czcionki i dynamicznym przesunięciem drzewka. Podczas walidacji nie wysyłano żadnego raportu do Discorda. Wymagana po instalacji kalibracja jest resetowana bez usuwania profili, historii kosztów i połączenia z Discordem.
