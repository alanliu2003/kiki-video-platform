# Opt-in local demo users (demo_* prefix). Does not upload media or reset volumes.
# Repeatable: existing demo users are logged in instead of re-registered.

param(
    [string]$BaseUrl = "http://127.0.0.1:8080"
)

$ErrorActionPreference = "Stop"
$password = "DemoPass123"
$users = @(
    @{ username = "demo_alice"; email = "demo_alice@example.com" },
    @{ username = "demo_bob"; email = "demo_bob@example.com" },
    @{ username = "demo_cara"; email = "demo_cara@example.com" }
)

function Invoke-Json {
    param([string]$Method, [string]$Url, [string]$Body, [string]$Token)
    $headers = @{ "Content-Type" = "application/json" }
    if ($Token) {
        $headers.Authorization = "Bearer $Token"
    }
    return Invoke-RestMethod -Method $Method -Uri $Url -Headers $headers -Body $Body
}

function Get-DemoToken([string]$Username, [string]$Email) {
    try {
        $login = Invoke-Json -Method Post -Url "$BaseUrl/api/auth/login" -Body (@{
            identifier = $Username
            password = $password
        } | ConvertTo-Json)
        return $login
    } catch {
        $register = Invoke-Json -Method Post -Url "$BaseUrl/api/auth/register" -Body (@{
            username = $Username
            email = $Email
            password = $password
        } | ConvertTo-Json)
        $login = Invoke-Json -Method Post -Url "$BaseUrl/api/auth/login" -Body (@{
            identifier = $Username
            password = $password
        } | ConvertTo-Json)
        Write-Host "Registered $($register.username)"
        return $login
    }
}

Write-Host "Seeding demo_* users against $BaseUrl (no media, no volume reset)."
$alice = Get-DemoToken "demo_alice" "demo_alice@example.com"
$bob = Get-DemoToken "demo_bob" "demo_bob@example.com"
$cara = Get-DemoToken "demo_cara" "demo_cara@example.com"
$aliceId = $alice.user.id

Invoke-RestMethod -Method Put -Uri "$BaseUrl/api/users/$aliceId/follow" -Headers @{ Authorization = "Bearer $($bob.accessToken)" } | Out-Null
Invoke-RestMethod -Method Put -Uri "$BaseUrl/api/users/$aliceId/follow" -Headers @{ Authorization = "Bearer $($cara.accessToken)" } | Out-Null

$recent = Invoke-RestMethod -Uri "$BaseUrl/api/videos/recent?page=0&size=1"
if ($recent.items.Count -gt 0) {
    $videoId = $recent.items[0].id
    try {
        Invoke-RestMethod -Method Put -Uri "$BaseUrl/api/videos/$videoId/like" -Headers @{ Authorization = "Bearer $($bob.accessToken)" } | Out-Null
        Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/videos/$videoId/comments" -Headers @{
            Authorization = "Bearer $($cara.accessToken)"
            "Content-Type" = "application/json"
        } -Body '{"content":"Nice clip from the demo seed."}' | Out-Null
        Write-Host "Attached like/comment to existing video $videoId"
    } catch {
        Write-Host "Skipped social attachments on video $videoId"
    }
} else {
    Write-Host "No videos in the catalog. Upload an MP4 as demo_alice to finish the demo."
}

Write-Host "Demo users: demo_alice, demo_bob, demo_cara / $password"
Write-Host "Alice public profile: $BaseUrl/api/users/$aliceId"
Write-Host "Cleanup: .\scripts\demo-seed-cleanup.ps1"
