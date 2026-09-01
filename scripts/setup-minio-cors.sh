#!/usr/bin/env bash
# Idempotent MinIO CORS helper. Community MinIO has no bucket PutBucketCors.
# Sets global api.cors_allow_origin and keeps the videos bucket private.
set -euo pipefail

ACCESS="${MINIO_ROOT_USER:-minioadmin}"
SECRET="${MINIO_ROOT_PASSWORD:-minioadmin}"
BUCKET="${MINIO_VIDEO_BUCKET:-videos}"
ORIGINS="${MINIO_API_CORS_ALLOW_ORIGIN:-${MEDIA_DELIVERY_CORS_ORIGINS:-*}}"

docker compose exec -T minio mc alias set local http://127.0.0.1:9000 "$ACCESS" "$SECRET" >/dev/null
docker compose exec -T minio mc anonymous set none "local/${BUCKET}" >/dev/null || true
docker compose exec -T minio mc admin config set local api "cors_allow_origin=${ORIGINS}"
echo "Bucket '${BUCKET}' remains private (anonymous GET denied)."
echo "Global MinIO CORS origins: ${ORIGINS}"
echo "A MinIO process restart may be required. Do not run docker compose down -v."
