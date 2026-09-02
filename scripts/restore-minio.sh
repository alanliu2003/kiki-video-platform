#!/usr/bin/env bash
# Copy a MinIO backup into an explicit bucket. Never uses --remove.
set -euo pipefail

if [[ "${1:-}" == "" ]]; then
  echo "Usage: $0 <backup-dir> [target_bucket]"
  echo "Refuses to restore into the live MINIO_VIDEO_BUCKET."
  exit 1
fi

repo_root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$repo_root"

source_dir="$1"
target_bucket="${2:-videos-restore-test}"
live_bucket="${MINIO_VIDEO_BUCKET:-videos}"
compose_service="${COMPOSE_MINIO_SERVICE:-minio}"

if [[ ! -d "$source_dir" ]]; then
  echo "Source not found: $source_dir"
  exit 1
fi

if [[ "$target_bucket" == "$live_bucket" ]]; then
  echo "Refusing to restore into the live bucket '$live_bucket'."
  echo "Use: $0 $source_dir videos-restore-test"
  exit 1
fi

echo "This will copy objects:"
echo "  source: $source_dir"
echo "  target: $target_bucket"
echo "  live bucket '$live_bucket' will not be deleted or synced with --remove."
if [[ "${CONFIRM_RESTORE:-}" != "RESTORE" ]]; then
  printf "Type RESTORE to continue: "
  read -r answer
  if [[ "$answer" != "RESTORE" ]]; then
    echo "Restore cancelled"
    exit 1
  fi
fi

stamp="$(date +%Y%m%d-%H%M%S)"
container_dir="/tmp/kiki-minio-restore-${stamp}"
docker compose exec -T "$compose_service" mkdir -p "$container_dir"
docker compose cp "${source_dir}/." "${compose_service}:${container_dir}"
docker compose exec -T "$compose_service" sh -c "mc alias set local http://127.0.0.1:9000 \"\$MINIO_ROOT_USER\" \"\$MINIO_ROOT_PASSWORD\" >/dev/null && mc mb --ignore-existing local/${target_bucket} && mc mirror --overwrite ${container_dir}/ local/${target_bucket}"
docker compose exec -T "$compose_service" rm -rf "$container_dir"

echo "Copied backup into bucket $target_bucket (no --remove)."
echo "Remove the test bucket after review:"
echo "  docker compose exec -T $compose_service mc rb --force local/${target_bucket}"
