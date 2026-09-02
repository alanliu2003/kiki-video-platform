#!/usr/bin/env bash
# Dump PostgreSQL to a timestamped custom-format file. Does not reset volumes.
# Credentials come from the environment (or Compose defaults). Nothing is printed.
set -euo pipefail

repo_root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$repo_root"

output_dir="${1:-${repo_root}/backups/postgres}"
database="${POSTGRES_DB:-video_platform}"
user="${POSTGRES_USER:-video}"
compose_service="${COMPOSE_POSTGRES_SERVICE:-postgres}"

mkdir -p "$output_dir"
stamp="$(date +%Y%m%d-%H%M%S)"
dest="${output_dir}/${database}-${stamp}.dump"
container_dump="/tmp/kiki-pg-${stamp}.dump"

docker compose exec -T "$compose_service" pg_dump -U "$user" -d "$database" -Fc -f "$container_dump"
docker compose cp "${compose_service}:${container_dump}" "$dest"
docker compose exec -T "$compose_service" rm -f "$container_dump"

echo "Wrote custom-format dump: $dest"
echo "Restore with: ./scripts/restore-postgres.sh $dest"
