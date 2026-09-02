# Copy a MinIO backup into an explicit bucket. Never uses --remove.
# Default destination is a disposable test bucket, not the live videos bucket.

param(
    [Parameter(Mandatory = $true)]
    [string]$Source,
    [string]$TargetBucket = "videos-restore-test",
    [string]$ComposeService = "minio",
    [switch]$ConfirmRestore
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

if (-not (Test-Path $Source)) {
    throw "Source not found: $Source"
}

$liveBucket = if ($env:MINIO_VIDEO_BUCKET) { $env:MINIO_VIDEO_BUCKET } else { "videos" }
if ($TargetBucket -eq $liveBucket) {
    throw "Refusing to restore into the live bucket '$liveBucket'. Use -TargetBucket videos-restore-test."
}

Write-Host "This will copy objects:"
Write-Host "  source: $Source"
Write-Host "  target: $TargetBucket"
Write-Host "  live bucket '$liveBucket' will not be deleted or synced with --remove."
if (-not $ConfirmRestore) {
    $answer = Read-Host "Type RESTORE to continue"
    if ($answer -ne "RESTORE") {
        throw "Restore cancelled"
    }
}

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$containerDir = "/tmp/kiki-minio-restore-$stamp"
docker compose exec -T $ComposeService mkdir -p $containerDir
docker compose cp "${Source}/." "${ComposeService}:${containerDir}"
$restore = @"
mc alias set local http://127.0.0.1:9000 "`$MINIO_ROOT_USER" "`$MINIO_ROOT_PASSWORD" >/dev/null
mc mb --ignore-existing local/$TargetBucket
mc mirror --overwrite $containerDir/ local/$TargetBucket
"@
docker compose exec -T $ComposeService sh -c $restore
if ($LASTEXITCODE -ne 0) {
    throw "MinIO restore mirror failed"
}
docker compose exec -T $ComposeService rm -rf $containerDir | Out-Null

Write-Host "Copied backup into bucket $TargetBucket (no --remove)."
Write-Host "Remove the test bucket after review:"
Write-Host "  docker compose exec -T $ComposeService mc rb --force local/$TargetBucket"
