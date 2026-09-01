#!/usr/bin/env bash
# Opt-in local demo users (demo_* prefix). Does not upload media or reset volumes.
set -euo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
PASSWORD="DemoPass123"

login_or_register() {
  local username="$1"
  local email="$2"
  if curl -sf -X POST "$BASE_URL/api/auth/login" \
      -H "Content-Type: application/json" \
      -d "{\"identifier\":\"$username\",\"password\":\"$PASSWORD\"}"; then
    return 0
  fi
  curl -sf -X POST "$BASE_URL/api/auth/register" \
      -H "Content-Type: application/json" \
      -d "{\"username\":\"$username\",\"email\":\"$email\",\"password\":\"$PASSWORD\"}" >/dev/null
  curl -sf -X POST "$BASE_URL/api/auth/login" \
      -H "Content-Type: application/json" \
      -d "{\"identifier\":\"$username\",\"password\":\"$PASSWORD\"}"
}

echo "Seeding demo_* users against $BASE_URL (no media, no volume reset)."
alice_json="$(login_or_register demo_alice demo_alice@example.com)"
bob_json="$(login_or_register demo_bob demo_bob@example.com)"
cara_json="$(login_or_register demo_cara demo_cara@example.com)"

alice_id="$(printf '%s' "$alice_json" | python -c "import json,sys; print(json.load(sys.stdin)['user']['id'])")"
bob_token="$(printf '%s' "$bob_json" | python -c "import json,sys; print(json.load(sys.stdin)['accessToken'])")"
cara_token="$(printf '%s' "$cara_json" | python -c "import json,sys; print(json.load(sys.stdin)['accessToken'])")"

curl -sf -X PUT "$BASE_URL/api/users/$alice_id/follow" -H "Authorization: Bearer $bob_token" >/dev/null
curl -sf -X PUT "$BASE_URL/api/users/$alice_id/follow" -H "Authorization: Bearer $cara_token" >/dev/null

echo "Demo users: demo_alice, demo_bob, demo_cara / $PASSWORD"
echo "Alice public profile: $BASE_URL/api/users/$alice_id"
echo "Cleanup: ./scripts/demo-seed-cleanup.sh"
