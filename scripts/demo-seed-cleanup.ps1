# Opt-in cleanup for M14 demo-seed users (demo_* only).
# Review scripts/demo-seed-cleanup.sql before running.

$ErrorActionPreference = "Stop"
Write-Host "This deletes only users whose username starts with demo_ and rows they own."
Write-Host "It does not reset Docker volumes or delete load12_* / ordinary user data."
$confirm = Read-Host "Type DELETE-DEMO to continue"
if ($confirm -ne "DELETE-DEMO") {
    Write-Host "Aborted."
    exit 1
}

Get-Content -Raw "$PSScriptRoot\demo-seed-cleanup.sql" | docker compose exec -T postgres psql -U video -d video_platform
Write-Host "demo_* cleanup finished."
