# Backup the current database, restore into video_platform_restore_test, compare counts,
# then drop only the temporary restore database. Never touches named volumes with -v.

param(
    [string]$ComposeService = "postgres"
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

$user = if ($env:POSTGRES_USER) { $env:POSTGRES_USER } else { "video" }
$sourceDb = if ($env:POSTGRES_DB) { $env:POSTGRES_DB } else { "video_platform" }
$restoreDb = "video_platform_restore_test"

$tables = @("users", "videos", "media_objects", "comments", "notifications")

function Get-Counts([string]$Database) {
    $map = @{}
    foreach ($table in $tables) {
        $value = docker compose exec -T $ComposeService psql -U $user -d $Database -Atc "SELECT COUNT(*) FROM $table"
        if ($LASTEXITCODE -ne 0) {
            throw "COUNT failed for $Database.$table"
        }
        $map[$table] = $value.Trim()
    }
    return $map
}

Write-Host "Counting source database $sourceDb"
$before = Get-Counts $sourceDb
foreach ($table in $tables) {
    Write-Host ("  {0}: {1}" -f $table, $before[$table])
}

Write-Host "Creating backup"
& (Join-Path $PSScriptRoot "backup-postgres.ps1") -Database $sourceDb -ComposeService $ComposeService
$latest = Get-ChildItem (Join-Path $repoRoot "backups\postgres") -Filter "$sourceDb-*.dump" | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $latest) {
    throw "No dump file found"
}

$exists = docker compose exec -T $ComposeService psql -U $user -d postgres -Atc "SELECT 1 FROM pg_database WHERE datname = '$restoreDb'"
if ($exists -match "1") {
    Write-Host "Dropping leftover $restoreDb only"
    docker compose exec -T $ComposeService psql -U $user -d postgres -c "DROP DATABASE $restoreDb"
}

Write-Host "Restoring into $restoreDb"
& (Join-Path $PSScriptRoot "restore-postgres.ps1") -BackupFile $latest.FullName -TargetDatabase $restoreDb -ComposeService $ComposeService -ConfirmRestore

$after = Get-Counts $restoreDb
$mismatch = 0
foreach ($table in $tables) {
    $ok = $before[$table] -eq $after[$table]
    $status = if ($ok) { "OK" } else { "MISMATCH"; $mismatch++ }
    Write-Host ("  {0}: source={1} restored={2} {3}" -f $table, $before[$table], $after[$table], $status)
}

Write-Host "Dropping temporary $restoreDb"
docker compose exec -T $ComposeService psql -U $user -d postgres -c "DROP DATABASE $restoreDb"
if ($LASTEXITCODE -ne 0) {
    throw "Failed to drop $restoreDb"
}

if ($mismatch -gt 0) {
    throw "Restore verification failed ($mismatch table(s)). Source $sourceDb was not modified."
}
Write-Host "PostgreSQL backup/restore verification passed. Source $sourceDb unchanged."
Write-Host "Dump kept at $($latest.FullName)"
