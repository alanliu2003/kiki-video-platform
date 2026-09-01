#!/usr/bin/env bash
set -euo pipefail

echo "This deletes only users whose username starts with demo_ and rows they own."
echo "It does not reset Docker volumes or delete load12_* / ordinary user data."
read -r -p "Type DELETE-DEMO to continue: " confirm
if [[ "$confirm" != "DELETE-DEMO" ]]; then
  echo "Aborted."
  exit 1
fi

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
docker compose exec -T postgres psql -U video -d video_platform < "$script_dir/demo-seed-cleanup.sql"
echo "demo_* cleanup finished."
