# Dump PostgreSQL to a timestamped custom-format file. Does not reset volumes.
# Credentials come from the environment (or Compose defaults). Nothing is printed.

param(
    [string]$OutputDir = "",
    [string]$Database = "",
    [string]$ComposeService = "postgres"
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

if (-not $OutputDir) {
    $OutputDir = Join-Path $repoRoot "backups\postgres"
}
if (-not $Database) {
    $Database = if ($env:POSTGRES_DB) { $env:POSTGRES_DB } else { "video_platform" }
}
$user = if ($env:POSTGRES_USER) { $env:POSTGRES_USER } else { "video" }

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$dest = Join-Path $OutputDir "$Database-$stamp.dump"

$containerDump = "/tmp/kiki-pg-$stamp.dump"
docker compose exec -T $ComposeService pg_dump -U $user -d $Database -Fc -f $containerDump
if ($LASTEXITCODE -ne 0) {
    throw "pg_dump failed"
}
docker compose cp "${ComposeService}:${containerDump}" $dest
docker compose exec -T $ComposeService rm -f $containerDump | Out-Null

Write-Host "Wrote custom-format dump: $dest"
Write-Host "Restore with: .\scripts\restore-postgres.ps1 -BackupFile `"$dest`""
