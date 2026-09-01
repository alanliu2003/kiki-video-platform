#!/usr/bin/env bash
# Bounded read-only API smoke. Does not reset volumes or delete data.
set -euo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
failed=0

ok() {
  local name="$1"
  shift
  if "$@"; then
    echo "OK  $name"
  else
    echo "FAIL $name"
    failed=$((failed + 1))
  fi
}

echo "API smoke against $BASE_URL"
ok health curl -sf "$BASE_URL/api/health" >/dev/null
ok openapi curl -sf "$BASE_URL/v3/api-docs" >/dev/null
ok recent curl -sf "$BASE_URL/api/videos/recent?page=0&size=5" >/dev/null
ok trending curl -sf "$BASE_URL/api/videos/trending?page=0&size=5" >/dev/null
if curl -sf "$BASE_URL/api/search/videos?q=test&page=0&size=5" >/dev/null; then
  echo "OK  search"
else
  code="$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/api/search/videos?q=test&page=0&size=5" || true)"
  if [[ "$code" == "503" ]]; then
    echo "OK  search (503 SEARCH_UNAVAILABLE)"
  else
    echo "FAIL search ($code)"
    failed=$((failed + 1))
  fi
fi

if [[ "$failed" -gt 0 ]]; then
  echo "API smoke failed ($failed check(s))."
  exit 1
fi
echo "API smoke passed."
