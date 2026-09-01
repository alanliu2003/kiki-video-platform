# Idempotent MinIO CORS helper for local development.
# Community MinIO does not implement S3 PutBucketCors (AIStor-only).
# This sets the global api.cors_allow_origin list and keeps the bucket private.

$ErrorActionPreference = "Stop"

$endpoint = if ($env:MINIO_ENDPOINT) { $env:MINIO_ENDPOINT } else { "http://127.0.0.1:9000" }
$access = if ($env:MINIO_ROOT_USER) { $env:MINIO_ROOT_USER } else { "minioadmin" }
$secret = if ($env:MINIO_ROOT_PASSWORD) { $env:MINIO_ROOT_PASSWORD } else { "minioadmin" }
$bucket = if ($env:MINIO_VIDEO_BUCKET) { $env:MINIO_VIDEO_BUCKET } else { "videos" }
$origins = if ($env:MINIO_API_CORS_ALLOW_ORIGIN) {
    $env:MINIO_API_CORS_ALLOW_ORIGIN
} elseif ($env:MEDIA_DELIVERY_CORS_ORIGINS) {
    $env:MEDIA_DELIVERY_CORS_ORIGINS
} else {
    "*"
}

Write-Host "Configuring MinIO alias local -> $endpoint"
docker compose exec -T minio mc alias set local http://127.0.0.1:9000 $access $secret | Out-Null
docker compose exec -T minio mc anonymous set none "local/$bucket" | Out-Null
docker compose exec -T minio mc admin config set local api "cors_allow_origin=$origins"
Write-Host "Bucket '$bucket' remains private (anonymous GET denied)."
Write-Host "Global MinIO CORS origins: $origins"
Write-Host "A MinIO process restart may be required for the new cors_allow_origin value."
Write-Host "Do not run docker compose down -v."
