#!/usr/bin/env bash
# Mirror MinIO video prefixes to backups/minio/. Does not delete objects.
set -euo pipefail

repo_root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$repo_root"

output_dir="${1:-${repo_root}/backups/minio}"
bucket="${MINIO_VIDEO_BUCKET:-videos}"
compose_service="${COMPOSE_MINIO_SERVICE:-minio}"

mkdir -p "$output_dir"
stamp="$(date +%Y%m%d-%H%M%S)"
dest="${output_dir}/${stamp}"
mkdir -p "$dest"
container_dir="/tmp/kiki-minio-${stamp}"

docker compose exec -T "$compose_service" sh -c "mc alias set local http://127.0.0.1:9000 \"\$MINIO_ROOT_USER\" \"\$MINIO_ROOT_PASSWORD\" >/dev/null && mkdir -p ${container_dir} && mc mirror --overwrite local/${bucket}/raw ${container_dir}/raw && mc mirror --overwrite local/${bucket}/processed ${container_dir}/processed && if mc ls --json local/${bucket}/videos >/dev/null 2>&1; then mc mirror --overwrite local/${bucket}/videos ${container_dir}/videos; fi && if mc ls --json local/${bucket}/thumbnails >/dev/null 2>&1; then mc mirror --overwrite local/${bucket}/thumbnails ${container_dir}/thumbnails; fi"
docker compose cp "${compose_service}:${container_dir}/." "$dest"
docker compose exec -T "$compose_service" rm -rf "$container_dir"

echo "Wrote MinIO mirror: $dest"
echo "This is not transactional with PostgreSQL. See docs/operations/backup-restore.md"
echo "Restore with: ./scripts/restore-minio.sh $dest videos-restore-test"
