# Opt-in cleanup for M12 load-test fixtures (load12_* users only).
# Review scripts/demo-cleanup.sql before running.

$ErrorActionPreference = "Stop"
Write-Host "This deletes only users whose username starts with load12_ and their owned rows."
Write-Host "It does not reset Docker volumes or delete unrelated user data."
$confirm = Read-Host "Type DELETE-LOAD12 to continue"
if ($confirm -ne "DELETE-LOAD12") {
    Write-Host "Aborted."
    exit 1
}

Get-Content -Raw "$PSScriptRoot\demo-cleanup.sql" | docker compose exec -T postgres psql -U video -d video_platform
Write-Host "load12_* cleanup finished."
