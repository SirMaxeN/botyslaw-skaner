[CmdletBinding()]
param(
    [switch]$SkipInstall,
    [switch]$NoWait,
    [switch]$Offline,
    [string]$DeviceSerial
)

$ErrorActionPreference = 'Stop'
$projectDirectory = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$apkPath = Join-Path $projectDirectory 'app/build/outputs/apk/autoTest/app-autoTest.apk'
$resultCode = 1
$resultMessage = 'Build was not completed.'

function Get-JdkMajor([string]$Directory) {
    if ([string]::IsNullOrWhiteSpace($Directory)) { return 0 }
    if (!(Test-Path -LiteralPath (Join-Path $Directory 'bin/java.exe') -PathType Leaf) -or
        !(Test-Path -LiteralPath (Join-Path $Directory 'bin/javac.exe') -PathType Leaf)) { return 0 }
    $releaseFile = Join-Path $Directory 'release'
    if (!(Test-Path -LiteralPath $releaseFile -PathType Leaf)) { return 0 }
    $releaseText = Get-Content -LiteralPath $releaseFile -Raw
    if ($releaseText -match '(?m)^JAVA_VERSION="(\d+)') { return [int]$Matches[1] }
    return 0
}

try {
    $jdkCandidates = @()
    if (![string]::IsNullOrWhiteSpace($env:JAVA_HOME)) { $jdkCandidates += $env:JAVA_HOME.Trim('"') }
    $gradleJdks = Join-Path $env:USERPROFILE '.gradle/jdks'
    if (Test-Path -LiteralPath $gradleJdks -PathType Container) {
        foreach ($directory in @(Get-ChildItem -LiteralPath $gradleJdks -Directory | Sort-Object Name)) {
            $jdkCandidates += $directory.FullName
            $jdkCandidates += @(Get-ChildItem -LiteralPath $directory.FullName -Directory | Select-Object -ExpandProperty FullName)
        }
    }
    $selectedJdk = $null
    foreach ($candidate in $jdkCandidates) {
        if ((Get-JdkMajor $candidate) -in @(17, 21)) { $selectedJdk = [IO.Path]::GetFullPath($candidate); break }
    }
    if (!$selectedJdk) { throw 'JDK 17 or 21 was not found. Set JAVA_HOME to an installed JDK, or install it in your Gradle JDK directory.' }

    $sdkCandidates = @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT)
    if (![string]::IsNullOrWhiteSpace($env:LOCALAPPDATA)) { $sdkCandidates += Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
    $selectedSdk = $null
    foreach ($candidate in $sdkCandidates) {
        if ([string]::IsNullOrWhiteSpace($candidate)) { continue }
        $candidate = $candidate.Trim('"')
        if ((Test-Path -LiteralPath (Join-Path $candidate 'platforms') -PathType Container) -and
            (Test-Path -LiteralPath (Join-Path $candidate 'build-tools') -PathType Container)) {
            $selectedSdk = [IO.Path]::GetFullPath($candidate); break
        }
    }
    if (!$selectedSdk) { throw 'Android SDK was not found. Set ANDROID_HOME, or install it in LocalAppData/Android/Sdk.' }
    $env:JAVA_HOME = $selectedJdk
    $env:ANDROID_HOME = $selectedSdk
    $env:ANDROID_SDK_ROOT = $selectedSdk

    Write-Host 'Building the separate AUTO Test application, running unit tests and lint...'
    $gradleArguments = @(
        ':app:testAutoTestUnitTest', ':app:lintAutoTest', ':app:assembleAutoTest',
        '--no-daemon', '--max-workers=1', '--console=plain',
        '-Dorg.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8',
        '-Pkotlin.compiler.execution.strategy=in-process'
    )
    if ($Offline) { $gradleArguments += '--offline' }
    Push-Location -LiteralPath $projectDirectory
    try {
        & (Join-Path $projectDirectory 'gradlew.bat') @gradleArguments
        $gradleExitCode = $LASTEXITCODE
        if ($gradleExitCode -ne 0) { throw "Gradle failed (exit code $gradleExitCode). Nothing was installed." }
    }
    finally { Pop-Location }
    if (!(Test-Path -LiteralPath $apkPath -PathType Leaf)) { throw 'Gradle completed, but the expected AUTO Test APK was not found.' }

    if ($SkipInstall) { $resultMessage = 'SUCCESS: tests and lint passed; AUTO Test APK was built. Installation was skipped.' }
    else {
        Write-Host 'Installing on the authorized USB device and preserving calibration...'
        $installArguments = @{ KeepCalibration = $true; ApkPath = $apkPath; AndroidSdkPath = $selectedSdk }
        if (![string]::IsNullOrWhiteSpace($DeviceSerial)) { $installArguments.DeviceSerial = $DeviceSerial }
        & (Join-Path $PSScriptRoot 'install-auto-test.ps1') @installArguments
        if ($LASTEXITCODE -ne 0) { throw "Installation failed (exit code $LASTEXITCODE)." }
        $resultMessage = 'SUCCESS: tests and lint passed; AUTO Test was installed and opened. Calibration and Discord pairing were preserved.'
    }
    $resultCode = 0
}
catch {
    $resultMessage = 'FAILED: ' + $_.Exception.Message
}
finally {
    Write-Host ''
    Write-Host $resultMessage -ForegroundColor $(if ($resultCode -eq 0) { 'Green' } else { 'Red' })
    Write-Host "AUTO Test APK: $apkPath"
    if (!$NoWait) {
        do { $answer = Read-Host 'Type exit to close this window' } while ($null -eq $answer -or $answer.Trim() -ine 'exit')
    }
}
exit $resultCode
