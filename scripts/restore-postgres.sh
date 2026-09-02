#!/usr/bin/env bash
# Restore a PostgreSQL dump into a separate database. Does not drop the current DB.
# Default target: video_platform_restore_test
set -euo pipefail

if [[ "${1:-}" == "" ]]; then
  echo "Usage: $0 <backup.dump> [target_database]"
  echo "Refuses to restore into the current POSTGRES_DB."
  exit 1
fi

repo_root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$repo_root"

backup_file="$1"
target_db="${2:-video_platform_restore_test}"
source_db="${POSTGRES_DB:-video_platform}"
user="${POSTGRES_USER:-video}"
compose_service="${COMPOSE_POSTGRES_SERVICE:-postgres}"

if [[ ! -f "$backup_file" ]]; then
  echo "Backup file not found: $backup_file"
  exit 1
fi

if [[ "$target_db" == "$source_db" ]]; then
  echo "Refusing to restore into the current database '$source_db'."
  echo "Use: $0 $backup_file video_platform_restore_test"
  exit 1
fi

echo "This will restore:"
echo "  file:   $backup_file"
echo "  target: $target_db"
echo "  source database '$source_db' will not be dropped."
if [[ "${CONFIRM_RESTORE:-}" != "RESTORE" ]]; then
  printf "Type RESTORE to continue: "
  read -r answer
  if [[ "$answer" != "RESTORE" ]]; then
    echo "Restore cancelled"
    exit 1
  fi
fi

exists="$(docker compose exec -T "$compose_service" psql -U "$user" -d postgres -Atc "SELECT 1 FROM pg_database WHERE datname = '${target_db}'")"
if [[ "$exists" == "1" ]]; then
  echo "Target database '$target_db' already exists. Drop it manually after review."
  exit 1
fi

docker compose exec -T "$compose_service" psql -U "$user" -d postgres -c "CREATE DATABASE ${target_db} OWNER ${user}"
container_dump="/tmp/kiki-pg-restore.dump"
docker compose cp "$backup_file" "${compose_service}:${container_dump}"
docker compose exec -T "$compose_service" pg_restore -U "$user" -d "$target_db" --no-owner --no-privileges "$container_dump"
docker compose exec -T "$compose_service" rm -f "$container_dump"

echo "Restored into $target_db"
echo "Drop only the restore database after validation:"
echo "  docker compose exec -T $compose_service psql -U $user -d postgres -c \"DROP DATABASE ${target_db};\""
