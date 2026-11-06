@echo off
setlocal
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\build-and-install-auto-test.ps1" %*
exit /b %errorlevel%
