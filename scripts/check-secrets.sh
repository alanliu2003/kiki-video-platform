#!/usr/bin/env bash
# Scan tracked files for likely committed secrets. Does not print secret values.
set -euo pipefail

repo_root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$repo_root"

failed=0
fail() {
  echo "FAIL $1"
  failed=$((failed + 1))
}

if git ls-files | grep -E '(^|/)\.env$' >/dev/null; then
  fail "a .env file is tracked"
fi
if git ls-files | grep -E '^backups/' >/dev/null; then
  fail "backup artifacts are tracked"
fi

while IFS= read -r file; do
  case "$file" in
    *.env.example|docs/*|CHANGELOG.md|scripts/demo-seed*) continue ;;
  esac
  if grep -Eq 'BEGIN (RSA |OPENSSH |EC )?PRIVATE KEY' "$file"; then
    fail "private-key matched in $file"
  fi
  if grep -Eq 'AKIA[0-9A-Z]{16}' "$file"; then
    fail "aws-access-key matched in $file"
  fi
  if grep -Eq 'ghp_[A-Za-z0-9]{20,}' "$file"; then
    fail "github-pat matched in $file"
  fi
  if grep -Eq 'Authorization:[[:space:]]*Bearer[[:space:]]+[A-Za-z0-9._-]{20,}' "$file"; then
    fail "generic-bearer matched in $file"
  fi
done < <(git ls-files)

if [[ "$failed" -gt 0 ]]; then
  echo "Secret check failed ($failed)."
  exit 1
fi
echo "Secret check passed (tracked files only; values not printed)."
