# Compare PostgreSQL video rows with the Elasticsearch alias count.
# Does not delete the production alias or kiki-videos-v1.

param(
    [string]$ElasticsearchUrl = "",
    [string]$Alias = "",
    [string]$ComposeService = "postgres",
    [string]$DisposableIndex = ""
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

if (-not $ElasticsearchUrl) {
    $ElasticsearchUrl = if ($env:ELASTICSEARCH_URL) { $env:ELASTICSEARCH_URL } else { "http://127.0.0.1:9200" }
}
if (-not $Alias) {
    $Alias = if ($env:ELASTICSEARCH_VIDEO_INDEX) { $env:ELASTICSEARCH_VIDEO_INDEX } else { "kiki-videos" }
}
$user = if ($env:POSTGRES_USER) { $env:POSTGRES_USER } else { "video" }
$db = if ($env:POSTGRES_DB) { $env:POSTGRES_DB } else { "video_platform" }

$pgCount = docker compose exec -T $ComposeService psql -U $user -d $db -Atc "SELECT COUNT(*) FROM videos"
if ($LASTEXITCODE -ne 0) {
    throw "PostgreSQL count failed"
}
$pgCount = $pgCount.Trim()

$esHealth = Invoke-RestMethod -Uri "$ElasticsearchUrl/_cluster/health"
Write-Host "Elasticsearch cluster: $($esHealth.status)"

$esCount = Invoke-RestMethod -Uri "$ElasticsearchUrl/${Alias}/_count"
Write-Host "PostgreSQL videos: $pgCount"
Write-Host "Elasticsearch $Alias documents: $($esCount.count)"
Write-Host "Counts can differ while search_index_outbox is pending. Rebuild from PostgreSQL:"
Write-Host "  cd backend"
Write-Host "  .\mvnw.cmd -pl api -am spring-boot:run `"-Dspring-boot.run.arguments=--app.search.rebuild=true`""

if ($DisposableIndex) {
    $forbidden = @("kiki-videos", "kiki-videos-v1", $Alias)
    if ($forbidden -contains $DisposableIndex) {
        throw "Refusing to delete production/alias index '$DisposableIndex'."
    }
    if ($DisposableIndex -notlike "kiki-videos-*-it" -and $DisposableIndex -notlike "kiki-videos-m15*") {
        throw "Disposable index must look like kiki-videos-m15* or *-it. Got '$DisposableIndex'."
    }
    Write-Host "Deleting disposable index $DisposableIndex only."
    Invoke-RestMethod -Method Delete -Uri "$ElasticsearchUrl/$DisposableIndex"
    Write-Host "Deleted $DisposableIndex. Rebuild is still required to refill a live alias."
}
