# Restore a PostgreSQL dump into a separate database. Does not drop the current DB.
# Default target: video_platform_restore_test

param(
    [Parameter(Mandatory = $true)]
    [string]$BackupFile,
    [string]$TargetDatabase = "video_platform_restore_test",
    [string]$ComposeService = "postgres",
    [switch]$ConfirmRestore
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

if (-not (Test-Path $BackupFile)) {
    throw "Backup file not found: $BackupFile"
}

$sourceDb = if ($env:POSTGRES_DB) { $env:POSTGRES_DB } else { "video_platform" }
$user = if ($env:POSTGRES_USER) { $env:POSTGRES_USER } else { "video" }

if ($TargetDatabase -eq $sourceDb) {
    throw "Refusing to restore into the current database '$sourceDb'. Use -TargetDatabase video_platform_restore_test."
}

Write-Host "This will restore:"
Write-Host "  file:   $BackupFile"
Write-Host "  target: $TargetDatabase"
Write-Host "  source database '$sourceDb' will not be dropped."
if (-not $ConfirmRestore) {
    $answer = Read-Host "Type RESTORE to continue"
    if ($answer -ne "RESTORE") {
        throw "Restore cancelled"
    }
} else {
    Write-Host "ConfirmRestore provided."
}

$exists = docker compose exec -T $ComposeService psql -U $user -d postgres -Atc "SELECT 1 FROM pg_database WHERE datname = '$TargetDatabase'"
if ($LASTEXITCODE -ne 0) {
    throw "Failed to inspect databases"
}
if ($exists -match "1") {
    throw "Target database '$TargetDatabase' already exists. Drop it manually after review, or choose another name."
}

docker compose exec -T $ComposeService psql -U $user -d postgres -c "CREATE DATABASE $TargetDatabase OWNER $user"
if ($LASTEXITCODE -ne 0) {
    throw "CREATE DATABASE failed"
}

$containerDump = "/tmp/kiki-pg-restore.dump"
docker compose cp $BackupFile "${ComposeService}:${containerDump}"
docker compose exec -T $ComposeService pg_restore -U $user -d $TargetDatabase --no-owner --no-privileges $containerDump
$restoreCode = $LASTEXITCODE
docker compose exec -T $ComposeService rm -f $containerDump | Out-Null
if ($restoreCode -ne 0) {
    throw "pg_restore failed with exit $restoreCode"
}

Write-Host "Restored into $TargetDatabase"
Write-Host "Compare counts, then drop only the restore database:"
Write-Host "  docker compose exec -T $ComposeService psql -U $user -d postgres -c `"DROP DATABASE $TargetDatabase;`""
