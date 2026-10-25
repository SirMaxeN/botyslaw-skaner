param([Parameter(Mandatory=$true)][string]$Apk, [Parameter(Mandatory=$true)][int]$VersionCode, [Parameter(Mandatory=$true)][string]$VersionName, [Parameter(Mandatory=$true)][string]$SshHost, [Parameter(Mandatory=$true)][string]$RemoteDirectory, [Parameter(Mandatory=$true)][string]$SshKey, [int]$SshPort=22)
$ErrorActionPreference = "Stop"
if ($VersionName -notmatch '^\d+\.\d+\.\d+$' -or $VersionCode -lt 1) { throw "Invalid version" }
$apkPath = (Resolve-Path -LiteralPath $Apk).Path
$releaseName = "forge-scanner-$VersionName-release.apk"
$remote = $RemoteDirectory
$target = $SshHost
if ($target -notmatch '^[a-zA-Z0-9_.@-]+$' -or $remote -notmatch '^/[a-zA-Z0-9_./-]+$' -or $remote.Split('/') -contains '..' -or $SshPort -notin 1..65535) { throw 'Invalid SSH destination' }
$key = (Resolve-Path -LiteralPath $SshKey).Path
$tempManifest = Join-Path ([IO.Path]::GetTempPath()) ([guid]::NewGuid().ToString() + '.json')
try {
    @{applicationId="com.sirmaxen.BotyslawSkaner"; versionCode=$VersionCode; versionName=$VersionName; url="https://komars.eu/downloads/botyslaw-skaner/$releaseName"; sha256=(Get-FileHash -LiteralPath $apkPath -Algorithm SHA256).Hash.ToLowerInvariant()} | ConvertTo-Json | Set-Content -LiteralPath $tempManifest -Encoding utf8NoBOM
    & ssh -p $SshPort -i $key -o BatchMode=yes $target "mkdir -p $remote"
    if ($LASTEXITCODE) { throw "Remote directory failed" }
    & scp -P $SshPort -i $key $apkPath "${target}:$remote/$releaseName"
    if ($LASTEXITCODE) { throw "APK upload failed" }
    & scp -P $SshPort -i $key $tempManifest "${target}:$remote/latest.json.tmp"
    if ($LASTEXITCODE) { throw "Manifest upload failed" }
    & ssh -p $SshPort -i $key -o BatchMode=yes $target "chmod 644 $remote/$releaseName $remote/latest.json.tmp && mv $remote/latest.json.tmp $remote/latest.json"
    if ($LASTEXITCODE) { throw "Publication failed" }
    Write-Host "Published $VersionName ($VersionCode) successfully."
} finally { Remove-Item -LiteralPath $tempManifest -ErrorAction SilentlyContinue }
