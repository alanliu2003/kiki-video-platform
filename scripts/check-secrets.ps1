# Scan tracked files for likely committed secrets. Does not print secret values.
# .env.example placeholders are allowed. Local .env must remain untracked.

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

$failed = 0

function Fail([string]$Message) {
    Write-Host "FAIL $Message"
    $script:failed++
}

$tracked = git ls-files
if ($tracked -contains ".env" -or ($tracked | Where-Object { $_ -like "*.env" -and $_ -notlike "*.env.example" })) {
    Fail "a .env file is tracked"
}

$backupHits = $tracked | Where-Object { $_ -like "backups/*" }
if ($backupHits) {
    Fail "backup artifacts are tracked"
}

$patterns = @(
    @{ Name = "private-key"; Regex = "BEGIN (RSA |OPENSSH |EC )?PRIVATE KEY" },
    @{ Name = "aws-access-key"; Regex = "AKIA[0-9A-Z]{16}" },
    @{ Name = "github-pat"; Regex = "ghp_[A-Za-z0-9]{20,}" },
    @{ Name = "generic-bearer"; Regex = "Authorization:\s*Bearer\s+[A-Za-z0-9\-_\.]{20,}" }
)

foreach ($file in $tracked) {
    if ($file -like "*.env.example") {
        continue
    }
    if ($file -like "scripts/demo-seed*" -or $file -like "docs/*" -or $file -like "CHANGELOG.md") {
        continue
    }
    $content = Get-Content -Raw -ErrorAction SilentlyContinue $file
    if (-not $content) {
        continue
    }
    foreach ($pattern in $patterns) {
        if ($content -match $pattern.Regex) {
            Fail "$($pattern.Name) matched in $file"
        }
    }
}

if ($failed -gt 0) {
    Write-Host "Secret check failed ($failed)."
    exit 1
}
Write-Host "Secret check passed (tracked files only; values not printed)."
