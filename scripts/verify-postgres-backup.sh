#!/usr/bin/env bash
# Backup the current database, restore into video_platform_restore_test, compare counts,
# then drop only the temporary restore database.
set -euo pipefail

repo_root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$repo_root"

user="${POSTGRES_USER:-video}"
source_db="${POSTGRES_DB:-video_platform}"
restore_db="video_platform_restore_test"
compose_service="${COMPOSE_POSTGRES_SERVICE:-postgres}"
tables="users videos media_objects comments notifications"

count_table() {
  local database="$1"
  local table="$2"
  docker compose exec -T "$compose_service" psql -U "$user" -d "$database" -Atc "SELECT COUNT(*) FROM ${table}" | tr -d '[:space:]'
}

echo "Counting source database $source_db"
for table in $tables; do
  echo "  $table: $(count_table "$source_db" "$table")"
done

./scripts/backup-postgres.sh
latest="$(ls -1t backups/postgres/${source_db}-*.dump | head -n 1)"
if [[ -z "$latest" ]]; then
  echo "No dump file found"
  exit 1
fi

exists="$(docker compose exec -T "$compose_service" psql -U "$user" -d postgres -Atc "SELECT 1 FROM pg_database WHERE datname = '${restore_db}'" || true)"
if [[ "$exists" == "1" ]]; then
  echo "Dropping leftover $restore_db only"
  docker compose exec -T "$compose_service" psql -U "$user" -d postgres -c "DROP DATABASE ${restore_db}"
fi

CONFIRM_RESTORE=RESTORE ./scripts/restore-postgres.sh "$latest" "$restore_db"

mismatch=0
for table in $tables; do
  src="$(count_table "$source_db" "$table")"
  dst="$(count_table "$restore_db" "$table")"
  if [[ "$src" == "$dst" ]]; then
    echo "  $table: source=$src restored=$dst OK"
  else
    echo "  $table: source=$src restored=$dst MISMATCH"
    mismatch=$((mismatch + 1))
  fi
done

echo "Dropping temporary $restore_db"
docker compose exec -T "$compose_service" psql -U "$user" -d postgres -c "DROP DATABASE ${restore_db}"

if [[ "$mismatch" -gt 0 ]]; then
  echo "Restore verification failed. Source $source_db was not modified."
  exit 1
fi
echo "PostgreSQL backup/restore verification passed. Source $source_db unchanged."
echo "Dump kept at $latest"
