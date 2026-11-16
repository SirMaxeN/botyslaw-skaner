@echo off
rem Nowa wersja Botysław Skaner (numer wersji i kod rosną automatycznie):
rem   wersja sklep     - paczka do Google Play (AAB w releases\)
rem   wersja test      - aplikacja testowa (APK w releases\)
rem   wersja telefon   - aplikacja testowa + instalacja na telefonie przez USB
rem Zapyta, czy podnieść wersję (Enter = tak); -Bump podnosi bez pytania, -NoBump zostawia numer.
rem Bez argumentu zapyta, co zbudować. Dwuklik w Eksploratorze też działa.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\release.ps1" %*
