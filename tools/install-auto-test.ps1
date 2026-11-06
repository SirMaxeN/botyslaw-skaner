[CmdletBinding()]
param(
    [string]$ApkPath,
    [string]$DeviceSerial,
    [string]$AndroidSdkPath,
    [switch]$KeepCalibration = $true
)

$ErrorActionPreference = 'Stop'
$projectDirectory = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$testPackage = 'com.sirmaxen.BotyslawSkaner.autotest'
if ([string]::IsNullOrWhiteSpace($ApkPath)) {
    $ApkPath = Join-Path $projectDirectory 'app/build/outputs/apk/autoTest/app-autoTest.apk'
}
$resolvedApk = (Resolve-Path -LiteralPath $ApkPath).Path
if (!(Test-Path -LiteralPath $resolvedApk -PathType Leaf)) { throw 'The AUTO Test APK does not exist.' }
if ([string]::IsNullOrWhiteSpace($AndroidSdkPath)) {
    $AndroidSdkPath = $env:ANDROID_HOME
    if ([string]::IsNullOrWhiteSpace($AndroidSdkPath)) { $AndroidSdkPath = $env:ANDROID_SDK_ROOT }
    if ([string]::IsNullOrWhiteSpace($AndroidSdkPath)) { $AndroidSdkPath = Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
}
$adb = Join-Path $AndroidSdkPath 'platform-tools/adb.exe'
if (!(Test-Path -LiteralPath $adb -PathType Leaf)) { throw 'ADB was not found. Set ANDROID_HOME or pass -AndroidSdkPath.' }
$buildToolDirectories = Get-ChildItem -LiteralPath (Join-Path $AndroidSdkPath 'build-tools') -Directory |
    Sort-Object { try { [version]$_.Name } catch { [version]'0.0' } } -Descending
$aapt = $null
foreach ($directory in $buildToolDirectories) {
    foreach ($name in @('aapt2.exe', 'aapt.exe')) {
        $candidate = Join-Path $directory.FullName $name
        if (Test-Path -LiteralPath $candidate -PathType Leaf) { $aapt = $candidate; break }
    }
    if ($aapt) { break }
}
if (!$aapt) { throw 'Android build-tools aapt/aapt2 is required to verify the APK package before installation.' }
$badging = & $aapt dump badging $resolvedApk 2>&1
if ($LASTEXITCODE -ne 0) { throw 'Could not verify the APK package.' }
$packageLine = @($badging | Where-Object { $_ -match "^package: name='([^']+)'" } | Select-Object -First 1)
if ($packageLine.Count -ne 1 -or $packageLine[0] -notmatch "^package: name='([^']+)'" -or $Matches[1] -ne $testPackage) {
    throw 'Refusing to install: the APK is not the separate Botyslaw AUTO Test package.'
}
$devices = & $adb devices
if ($LASTEXITCODE -ne 0) { throw 'Could not list connected Android devices.' }
$readySerials = @($devices | ForEach-Object {
    if ($_ -match '^(\S+)\s+device(?:\s|$)') { $Matches[1] }
})
if ([string]::IsNullOrWhiteSpace($DeviceSerial)) {
    if ($readySerials.Count -ne 1) { throw 'Connect exactly one authorized device or pass -DeviceSerial.' }
    $DeviceSerial = $readySerials[0]
}
if ($readySerials -notcontains $DeviceSerial) { throw 'The selected device is not connected and authorized.' }
function Invoke-TestAdb([string[]]$Arguments) {
    & $adb -s $DeviceSerial @Arguments
    if ($LASTEXITCODE -ne 0) { throw 'ADB operation failed; installation/reset was not completed.' }
}

Write-Host 'Installing the separate AUTO Test application...'
Invoke-TestAdb -Arguments @('install', '-r', $resolvedApk)
# Package replacement already restarts the code. A force-stop also disables accessibility
# on Android, so reserve it for an explicitly requested calibration reset.
if ($KeepCalibration) {
    Write-Host 'Calibration preserved as requested. Discord pairing and unrelated data were preserved.'
} else {
Invoke-TestAdb -Arguments @('shell', 'am', 'force-stop', $testPackage)
# Exact files only. Pairing, credentials, Keystore and all other preferences are untouched.
$calibrationFiles = @(
    'shared_prefs/scan_calibrations_v1.xml',
    'shared_prefs/scan_calibrations_v1.xml.bak',
    'shared_prefs/auto_scan_navigation_v1.xml',
    'shared_prefs/auto_scan_navigation_v1.xml.bak',
    'shared_prefs/auto_read_checkpoints_v1.xml',
    'shared_prefs/auto_read_checkpoints_v1.xml.bak'
)
Invoke-TestAdb -Arguments (@('shell', 'run-as', $testPackage, 'rm', '-f') + $calibrationFiles)
foreach ($calibrationFile in $calibrationFiles) {
    Invoke-TestAdb -Arguments @('shell', 'run-as', $testPackage, 'test', '!', '-e', $calibrationFile)
}
Write-Host 'Calibration reset completed. Discord pairing and unrelated data were preserved.'
}
Invoke-TestAdb -Arguments @('shell', 'am', 'start', '-n', "$testPackage/eu.komars.forgescanner.update.UpdateActivity")

