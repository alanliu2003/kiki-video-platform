# Bounded read-only API smoke. Does not reset volumes or delete data.
# Optional -Auth registers a smoke14_* user and checks a protected endpoint.

param(
    [string]$BaseUrl = "http://127.0.0.1:8080",
    [switch]$Auth
)

$ErrorActionPreference = "Stop"
$failed = 0

function Assert-Ok {
    param([string]$Name, [scriptblock]$Call)
    try {
        $result = & $Call
        Write-Host "OK  $Name"
        return $result
    } catch {
        Write-Host "FAIL $Name : $($_.Exception.Message)"
        $script:failed++
        return $null
    }
}

Write-Host "API smoke against $BaseUrl"

Assert-Ok "health" { Invoke-RestMethod -Uri "$BaseUrl/api/health" } | Out-Null
$docs = Assert-Ok "openapi" { Invoke-RestMethod -Uri "$BaseUrl/v3/api-docs" }
if ($docs -and -not $docs.openapi) {
    Write-Host "FAIL openapi missing openapi field"
    $failed++
}
if ($docs -and ($docs | ConvertTo-Json -Depth 6) -match "/actuator") {
    Write-Host "FAIL openapi documents actuator"
    $failed++
}

$recent = Assert-Ok "recent" { Invoke-RestMethod -Uri "$BaseUrl/api/videos/recent?page=0&size=5" }
Assert-Ok "trending" { Invoke-RestMethod -Uri "$BaseUrl/api/videos/trending?page=0&size=5" } | Out-Null

try {
    Invoke-RestMethod -Uri "$BaseUrl/api/search/videos?q=test&page=0&size=5" | Out-Null
    Write-Host "OK  search"
} catch {
    $status = $_.Exception.Response.StatusCode.value__
    if ($status -eq 503) {
        Write-Host "OK  search (503 SEARCH_UNAVAILABLE)"
    } else {
        Write-Host "FAIL search : $($_.Exception.Message)"
        $failed++
    }
}

if ($recent -and $recent.items.Count -gt 0) {
    $ownerId = $recent.items[0].owner.id
    Assert-Ok "public profile" { Invoke-RestMethod -Uri "$BaseUrl/api/users/$ownerId" } | Out-Null
    Assert-Ok "user videos" { Invoke-RestMethod -Uri "$BaseUrl/api/users/$ownerId/videos?page=0&size=5" } | Out-Null
} else {
    Write-Host "SKIP public profile (no recent videos)"
}

if ($Auth) {
    $username = "smoke14_" + [guid]::NewGuid().ToString("N").Substring(0, 8)
    $login = Assert-Ok "register/login" {
        Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/auth/register" -ContentType application/json -Body (@{
            username = $username
            email = "$username@example.com"
            password = "StrongPassword123"
        } | ConvertTo-Json) | Out-Null
        Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/auth/login" -ContentType application/json -Body (@{
            identifier = $username
            password = "StrongPassword123"
        } | ConvertTo-Json)
    }
    if ($login) {
        Assert-Ok "protected me" {
            Invoke-RestMethod -Uri "$BaseUrl/api/users/me" -Headers @{ Authorization = "Bearer $($login.accessToken)" }
        } | Out-Null
    }
}

if ($failed -gt 0) {
    Write-Host "API smoke failed ($failed check(s))."
    exit 1
}
Write-Host "API smoke passed."
