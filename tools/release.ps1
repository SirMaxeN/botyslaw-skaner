<#
.SYNOPSIS
  One command for a new Botysław Skaner build. Raises versionCode by 1 and the last part of versionName
  (1.6.0 -> 1.6.1) automatically; a failed build restores the previous numbers.

.EXAMPLE
  wersja sklep      # Google Play: tests + lint + signed AAB in releases\ (no test APK)
  wersja test       # AUTO Test APK in releases\ (no installation)
  wersja telefon    # AUTO Test APK + installation on the USB phone (calibration and pairing kept)
#>
[CmdletBinding()]
param(
    [Parameter(Position = 0)][string]$Mode,
    [switch]$NoBump,
    [switch]$NoWait,
    [string]$DeviceSerial
)

$ErrorActionPreference = 'Stop'
$projectDirectory = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$gradleFile = Join-Path $projectDirectory 'app/build.gradle.kts'
$releases = Join-Path $projectDirectory 'releases'
$resultCode = 1
$resultMessage = 'Nie ukończono budowania.'
$originalGradle = $null

$modes = @{ 'sklep' = 'store'; 'store' = 'store'; 'play' = 'store'
            'test' = 'test'; 'testowa' = 'test'
            'telefon' = 'install'; 'install' = 'install'; 'instaluj' = 'install' }

function Find-Jdk {
    $candidates = @()
    if (![string]::IsNullOrWhiteSpace($env:JAVA_HOME)) { $candidates += $env:JAVA_HOME.Trim('"') }
    $gradleJdks = Join-Path $env:USERPROFILE '.gradle/jdks'
    if (Test-Path -LiteralPath $gradleJdks -PathType Container) {
        foreach ($directory in @(Get-ChildItem -LiteralPath $gradleJdks -Directory | Sort-Object Name)) {
            $candidates += $directory.FullName
            $candidates += @(Get-ChildItem -LiteralPath $directory.FullName -Directory | Select-Object -ExpandProperty FullName)
        }
    }
    foreach ($candidate in $candidates) {
        $release = Join-Path $candidate 'release'
        if ((Test-Path -LiteralPath (Join-Path $candidate 'bin/javac.exe')) -and (Test-Path -LiteralPath $release) -and
            ((Get-Content -LiteralPath $release -Raw) -match '(?m)^JAVA_VERSION="(17|21)')) { return [IO.Path]::GetFullPath($candidate) }
    }
    throw 'Nie znaleziono JDK 17 ani 21. Ustaw JAVA_HOME albo zainstaluj JDK przez Gradle.'
}

function Find-Sdk {
    $candidates = @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT)
    if (![string]::IsNullOrWhiteSpace($env:LOCALAPPDATA)) { $candidates += Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
    foreach ($candidate in $candidates) {
        if ([string]::IsNullOrWhiteSpace($candidate)) { continue }
        $candidate = $candidate.Trim('"')
        if ((Test-Path -LiteralPath (Join-Path $candidate 'platforms')) -and (Test-Path -LiteralPath (Join-Path $candidate 'build-tools'))) {
            return [IO.Path]::GetFullPath($candidate)
        }
    }
    throw 'Nie znaleziono Android SDK. Ustaw ANDROID_HOME.'
}

function Get-Version([string]$text) {
    $code = [regex]::Match($text, 'versionCode\s*=\s*(\d+)')
    $name = [regex]::Match($text, 'versionName\s*=\s*"(\d+)\.(\d+)\.(\d+)"')
    if (!$code.Success -or !$name.Success) { throw 'Nie znaleziono versionCode / versionName w app/build.gradle.kts.' }
    return [pscustomobject]@{
        Code = [int]$code.Groups[1].Value
        Major = [int]$name.Groups[1].Value; Minor = [int]$name.Groups[2].Value; Patch = [int]$name.Groups[3].Value
    }
}

function Invoke-Gradle([string[]]$tasks) {
    $arguments = $tasks + @('--console=plain', '-Dorg.gradle.jvmargs=-Xmx3072m -Dfile.encoding=UTF-8')
    Push-Location -LiteralPath $projectDirectory
    try {
        & (Join-Path $projectDirectory 'gradlew.bat') @arguments
        if ($LASTEXITCODE -ne 0) { throw "Gradle zakończył się błędem (kod $LASTEXITCODE)." }
    } finally { Pop-Location }
}

try {
    $mode = $modes[("$Mode").Trim().ToLowerInvariant()]
    if (!$mode) {
        $choice = Read-Host "Co zbudować? [1] sklep (Google Play)  [2] test  [3] test + instalacja na telefonie"
        $mode = @{ '1' = 'store'; '2' = 'test'; '3' = 'install' }[$choice.Trim()]
        if (!$mode) { throw 'Nieznany wybór. Użyj: wersja sklep | wersja test | wersja telefon' }
    }

    $env:JAVA_HOME = Find-Jdk
    $sdk = Find-Sdk
    $env:ANDROID_HOME = $sdk; $env:ANDROID_SDK_ROOT = $sdk

    # 1. Version: +1 code, +1 last part of the name. Restored if anything below fails.
    $originalGradle = [IO.File]::ReadAllText($gradleFile)
    $current = Get-Version $originalGradle
    if ($NoBump) {
        $code = $current.Code; $name = "$($current.Major).$($current.Minor).$($current.Patch)"
    } else {
        $code = $current.Code + 1; $name = "$($current.Major).$($current.Minor).$($current.Patch + 1)"
        $updated = [regex]::Replace($originalGradle, 'versionCode\s*=\s*\d+', "versionCode = $code", 1)
        $updated = [regex]::Replace($updated, 'versionName\s*=\s*"\d+\.\d+\.\d+"', "versionName = `"$name`"", 1)
        [IO.File]::WriteAllText($gradleFile, $updated)
        Write-Host "Wersja: $($current.Major).$($current.Minor).$($current.Patch) ($($current.Code)) -> $name ($code)" -ForegroundColor Cyan
    }
    New-Item -ItemType Directory -Force -Path $releases | Out-Null

    if ($mode -eq 'store') {
        # 2a. Google Play: tests, lint and the signed bundle only (never the test APK together with it).
        Write-Host 'Testy, lint i paczka do Google Play...'
        Invoke-Gradle @(':shared:jvmTest', ':app:testDebugUnitTest', ':app:lintPlay', ':app:bundlePlay')
        $built = Join-Path $projectDirectory 'app/build/outputs/bundle/play/app-play.aab'
        $target = Join-Path $releases "botyslaw-skaner-$name-google-play.aab"
        Copy-Item -LiteralPath $built -Destination $target -Force
        $signature = & (Join-Path $env:JAVA_HOME 'bin/jarsigner.exe') -verify -verbose:summary -certs $target 2>&1 | Out-String
        if ($signature -notmatch 'CN=SirMaxeN') { throw 'Paczka nie jest podpisana kluczem SirMaxeN.' }
        $resultMessage = "GOTOWE: $name ($code) do Google Play.`nPlik: $target`nW Play Console: nowa wersja na ścieżce, przeciągnij ten plik."
    } else {
        # 2b. AUTO Test: tests, lint and the separate test application.
        Write-Host 'Testy, lint i aplikacja testowa (AUTO Test)...'
        Invoke-Gradle @(':shared:jvmTest', ':app:testDebugUnitTest', ':app:lintAutoTest', ':app:assembleAutoTest')
        $built = Join-Path $projectDirectory 'app/build/outputs/apk/autoTest/app-autoTest.apk'
        $target = Join-Path $releases "Botyslaw-AUTO-Test-$name-$code.apk"
        Copy-Item -LiteralPath $built -Destination $target -Force
        $resultMessage = "GOTOWE: aplikacja testowa $name ($code).`nPlik: $target"
        $originalGradle = $null   # the APK exists with these numbers: keep them even if the installation fails
        if ($mode -eq 'install') {
            Write-Host 'Instaluję na telefonie (kalibracja i połączenie z Discordem zostają)...'
            $install = @{ KeepCalibration = $true; ApkPath = $built; AndroidSdkPath = $sdk }
            if (![string]::IsNullOrWhiteSpace($DeviceSerial)) { $install.DeviceSerial = $DeviceSerial }
            & (Join-Path $PSScriptRoot 'install-auto-test.ps1') @install
            if ($LASTEXITCODE -ne 0) { throw "Instalacja nie powiodła się (kod $LASTEXITCODE). Aplikacja $name ($code) jest zbudowana: $target — podłącz telefon i uruchom tools\install-auto-test.ps1." }
            $resultMessage += "`nZainstalowano na telefonie."
        }
    }
    $originalGradle = $null   # success: keep the new version numbers
    $resultCode = 0
}
catch {
    $resultMessage = 'BŁĄD: ' + $_.Exception.Message
}
finally {
    if ($null -ne $originalGradle) {
        [IO.File]::WriteAllText($gradleFile, $originalGradle)
        if (!$NoBump) { $resultMessage += "`nNumer wersji przywrócony (nieudane budowanie nie zużywa numeru)." }
    }
    Write-Host ''
    Write-Host $resultMessage -ForegroundColor $(if ($resultCode -eq 0) { 'Green' } else { 'Red' })
    if (!$NoWait) { Read-Host 'Naciśnij Enter, aby zamknąć' | Out-Null }
}
exit $resultCode
