$ErrorActionPreference = 'Stop'
$project = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$archive = Join-Path $project '.local/inventory-fixtures.zip'
if (!(Test-Path -LiteralPath $archive)) { throw 'Private replay archive is not installed. Standard JVM tests do not require it.' }
$scratch = Join-Path $project ('.local/fixture-run-' + [guid]::NewGuid().ToString('N'))
$fixtures = @{
    SCANNER_VIDEO_FIXTURE='slow-60.gray'
    SCANNER_LATEST_FIXTURE='latest-auto.gray'
    SCANNER_FAST_FIXTURE='fast-60.gray'
    SCANNER_GRID_FIXTURE='current-phone.gray'
    SCANNER_MOUNT_SCREEN='mount-phone.gray'
    SCANNER_CONTROLLED_FIXTURE='controlled-fast-07.gray'
    SCANNER_SPARSE_FIXTURE='controlled-fast-07-30.gray'
    SCANNER_PARTIAL_TAIL_FIXTURE='new-20261025-123131.native.gray'
}
$previous = @{}
$result = 1
try {
    Expand-Archive -LiteralPath $archive -DestinationPath $scratch
    foreach ($name in $fixtures.Keys) {
        $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
        [Environment]::SetEnvironmentVariable($name, (Join-Path $scratch $fixtures[$name]), 'Process')
    }
    Push-Location $project
    try { & ./gradlew.bat :app:testDebugUnitTest --no-daemon; $result = $LASTEXITCODE }
    finally { Pop-Location }
} finally {
    foreach ($name in $previous.Keys) { [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process') }
    $resolvedScratch = [IO.Path]::GetFullPath($scratch)
    $allowedPrefix = [IO.Path]::GetFullPath((Join-Path $project '.local/fixture-run-'))
    if (!$resolvedScratch.StartsWith($allowedPrefix, [StringComparison]::OrdinalIgnoreCase)) { throw 'Unsafe fixture cleanup path' }
    if (Test-Path -LiteralPath $resolvedScratch) { Remove-Item -LiteralPath $resolvedScratch -Recurse -Force }
}
exit $result
