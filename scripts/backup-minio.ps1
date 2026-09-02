# Mirror MinIO video prefixes to backups/minio/. Does not delete objects.
# Uses the running Compose MinIO container. Credentials stay inside the container.

param(
    [string]$OutputDir = "",
    [string]$ComposeService = "minio",
    [string]$Bucket = ""
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

if (-not $OutputDir) {
    $OutputDir = Join-Path $repoRoot "backups\minio"
}
if (-not $Bucket) {
    $Bucket = if ($env:MINIO_VIDEO_BUCKET) { $env:MINIO_VIDEO_BUCKET } else { "videos" }
}

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$dest = Join-Path $OutputDir $stamp
New-Item -ItemType Directory -Force -Path $dest | Out-Null

$containerDir = "/tmp/kiki-minio-$stamp"
$mirror = @"
mc alias set local http://127.0.0.1:9000 "`$MINIO_ROOT_USER" "`$MINIO_ROOT_PASSWORD" >/dev/null
mkdir -p $containerDir
mc mirror --overwrite local/$Bucket/raw $containerDir/raw
mc mirror --overwrite local/$Bucket/processed $containerDir/processed
if mc ls --json local/$Bucket/videos >/dev/null 2>&1; then mc mirror --overwrite local/$Bucket/videos $containerDir/videos; fi
if mc ls --json local/$Bucket/thumbnails >/dev/null 2>&1; then mc mirror --overwrite local/$Bucket/thumbnails $containerDir/thumbnails; fi
"@
docker compose exec -T $ComposeService sh -c $mirror
if ($LASTEXITCODE -ne 0) {
    throw "MinIO mirror failed"
}
docker compose cp "${ComposeService}:${containerDir}/." $dest
docker compose exec -T $ComposeService rm -rf $containerDir | Out-Null

Write-Host "Wrote MinIO mirror: $dest"
Write-Host "This is not transactional with PostgreSQL. See docs/operations/backup-restore.md"
Write-Host "Restore with: .\scripts\restore-minio.ps1 -Source `"$dest`" -TargetBucket videos-restore-test"
