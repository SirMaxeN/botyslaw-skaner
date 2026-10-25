@echo off
setlocal
cd /d "%~dp0"
if not defined JAVA_HOME if exist "C:\Program Files\Android\Android Studio\jbr\bin\java.exe" set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
if not defined ANDROID_HOME if exist "%LOCALAPPDATA%\Android\Sdk" set "ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk"
call gradlew.bat :app:assembleRelease :app:testDebugUnitTest :app:lintRelease
set "SCANNER_RESULT=%ERRORLEVEL%"
echo.
if "%SCANNER_RESULT%"=="0" (
  echo Build and checks completed. APK: app\build\outputs\apk\release\app-release.apk
) else (
  echo Build failed with exit code %SCANNER_RESULT%. Review the output above.
)
echo Press a key to exit.
pause >nul
exit /b %SCANNER_RESULT%
