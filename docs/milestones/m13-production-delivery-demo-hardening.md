# Milestone 13 — Production Delivery & Demo Hardening

## Goal

Move kiki-video-platform from a development-oriented API-proxied media architecture toward a production-style delivery model, while keeping local Maven + Vite development simple. This is not a cloud, Kubernetes, CDN, or DRM milestone.

## Previous architecture (M5–M12)

```text
Browser
  → Spring API
      → MinIO
  Spring streamed HLS manifests, TS segments, thumbnails, and Range MP4
```

Every media byte request went through Spring:

| Asset | Path | Why it went through Spring |
| --- | --- | --- |
| HLS master | `GET /api/videos/{id}/hls/master.m3u8` | Private bucket; path allow-list |
| Variant playlist | `GET /api/videos/{id}/hls/{rendition}/index.m3u8` | Same |
| TS segments | `GET /api/videos/{id}/hls/{rendition}/segmentNNN.ts` | Same; large bytes |
| Thumbnail | `GET /api/videos/{id}/thumbnail` | Same |
| Legacy / raw | `GET /api/videos/{id}/content` | HTTP Range via API |

`GET /api/videos/{id}/playback` already existed and returned API-relative URLs. Videos are public (`permitAll` on metadata, playback, HLS, thumbnail, and content). There is no private/unlisted product model.

## New architecture

```text
Metadata / control:
  Browser → Spring API → PostgreSQL

Media bytes (MEDIA_DELIVERY_MODE=presigned):
  Browser → short-lived MinIO presigned URL → object bytes

Tiny HLS playlists (still rewritten by Spring):
  Browser → Spring → read m3u8 from MinIO → rewrite child URIs → return text
```

Storage and delivery stay separate:

- `VideoStorage` — persist and read objects
- `MediaDeliveryService` — decide client URLs and rewrite playlists
- `ObjectUrlSigner` — HMAC-sign a **trusted** object key already resolved from PostgreSQL

There is no `/api/media/presign?key=` endpoint. Object keys are never taken from user query parameters.

## Presigned URL flow

1. Client asks `GET /api/videos/{id}/playback`.
2. API loads the video and `media_objects` row.
3. If `presigned`, it signs the raw object and thumbnail keys with `MEDIA_DELIVERY_URL_TTL` (default `15m`).
4. HLS `url` / `manifestUrl` remain `/api/videos/{id}/hls/master.m3u8` so Spring can rewrite playlists.
5. When the player requests a variant playlist, Spring rewrites **segment** URIs to presigned MinIO URLs.
6. The browser fetches TS / MP4 / (playback poster) bytes from MinIO. Range is handled by MinIO.

`MINIO_PUBLIC_ENDPOINT` is the host the **browser** uses (`http://127.0.0.1:9000` locally). `MINIO_ENDPOINT` remains the API/worker SDK endpoint (`http://minio:9000` inside Compose).

Signing uses a dedicated MinIO client pointed at the public endpoint so the signature host matches what the browser calls.

## HLS strategy

Variant playlists stored in MinIO still contain relative names such as `segment000.ts`. Serving those objects directly would drop the signature query string.

Therefore:

- Master and variant **playlists stay on the API** (small text).
- Child `.m3u8` URIs stay relative so the player comes back to Spring for the next rewrite.
- Child `.ts` URIs become fully qualified presigned GET URLs.
- Path traversal and unknown names are still rejected by `HlsAssetPaths`.
- URLs are signed only when that playlist is requested — not eagerly for every segment of every video.

If a VOD session outlives `MEDIA_DELIVERY_URL_TTL`, already-issued segment URLs expire. The player refetches the playback descriptor **once** and remounts HLS (new playlist, new signatures). Raise the TTL for long-form videos.

## TTL

| Setting | Default |
| --- | --- |
| `MEDIA_DELIVERY_URL_TTL` | `15m` |

Short enough not to behave like a permanent public object. Long enough for ordinary demo clips. Spring does not refresh URLs per frame or per segment request.

## Legacy MP4 / Range

The API `GET /api/videos/{id}/content` Range path is unchanged and remains the fallback.

In `presigned` mode the playback descriptor `url` / `contentUrl` is a presigned GET. MinIO honors `Range`. The frontend uses the descriptor URL, not a hardcoded `/content` helper.

## Thumbnails

- **Cards / search / notifications:** still `/api/videos/{id}/thumbnail` (small JPEG, one stable URL, no N+1 signing).
- **Playback poster:** presigned when `MEDIA_DELIVERY_MODE=presigned`.
- Broken-image fallback in `VideoCard` is unchanged.

## Playback descriptor

`GET /api/videos/{id}/playback` is extended, not replaced:

```json
{
  "status": "READY",
  "type": "HLS",
  "mode": "HLS",
  "url": "/api/videos/12/hls/master.m3u8",
  "expiresAt": "2026-09-01T06:15:00Z",
  "fallbackUrl": "http://127.0.0.1:9000/videos/raw/...?X-Amz-...",
  "processingStatus": "READY",
  "deliveryMode": "presigned",
  "manifestUrl": "/api/videos/12/hls/master.m3u8",
  "contentUrl": "http://127.0.0.1:9000/videos/raw/...?X-Amz-...",
  "thumbnailUrl": "http://127.0.0.1:9000/videos/processed/1/thumbnail.jpg?X-Amz-..."
}
```

Legacy:

```json
{
  "status": "NOT_REQUESTED",
  "type": "ORIGINAL",
  "mode": "LEGACY",
  "url": "http://127.0.0.1:9000/videos/raw/...?X-Amz-...",
  "deliveryMode": "presigned"
}
```

The frontend never sees bucket names or object keys.

## Security assumptions

- Videos are **public**. Presigned URLs are bearer URLs with a bounded TTL.
- No private / unlisted / DRM model (out of scope).
- MinIO access/secret keys stay on the API/worker.
- Actuator `/env`, `/configprops`, and heap dumps stay disabled.
- Production CORS is an explicit origin list, never `*` with credentials.
- Reverse-proxy access logs delete query strings so presigned parameters are not stored.

## CORS

Community MinIO (the Compose image) does **not** implement S3 `PutBucketCors` — that API is AIStor-only. The Java client still tries bucket CORS and fails soft.

Local/dev uses MinIO's **global** `MINIO_API_CORS_ALLOW_ORIGIN` (Compose default `*`, same as MinIO's built-in default). Presigned object GETs do not send cookies or `Authorization` headers.

Verified against the running Compose MinIO: an `OPTIONS` preflight from `http://127.0.0.1:5173` returned `204` with `Access-Control-Allow-Origin` echoing that origin and `Access-Control-Allow-Headers: Range`.

To restrict origins without resetting volumes:

```powershell
$env:MINIO_API_CORS_ALLOW_ORIGIN = "http://localhost:5173,http://127.0.0.1:5173,http://localhost:8088,http://127.0.0.1:8088"
.\scripts\setup-minio-cors.ps1
```

The bucket stays private (`mc anonymous set none`). A MinIO process restart may be required after changing the global list. Do not use `docker compose down -v`.

## Proxy fallback

`MEDIA_DELIVERY_MODE=proxy` restores M12 behavior: every media URL is an API path and Spring streams bytes. Playlist rewriting is skipped.

Recommended production-like setting: `presigned`.

## Docker packaging

Multi-stage images:

| Image | Dockerfile |
| --- | --- |
| `kiki-api:local` (~1.03 GB) | `backend/api/Dockerfile` |
| `kiki-media-worker:local` (~1.44 GB, includes FFmpeg) | `backend/media-worker/Dockerfile` |
| `kiki-frontend:local` (~89.5 MB) | `frontend/Dockerfile` (Caddy + Vue dist) |

Non-root `kiki` user on API/worker. Worker image includes FFmpeg.

API/worker images copy the already-packaged Spring Boot jars (runtime `eclipse-temurin:21-jre-jammy`). Package on the host first:

```powershell
cd backend
.\mvnw.cmd -pl api,media-worker -am package -DskipTests
```

In-container Maven multi-stage builds were avoided because pulling `maven` / JDK images from Docker Hub was unreliable on this machine. The frontend image remains multi-stage (`node:22-bookworm-slim` → `caddy:2-alpine`).

## Production-like Compose

Development Compose is unchanged.

```powershell
docker compose -f docker-compose.yml -f docker-compose.prod.yml up --build
```

Adds packaged `api`, `worker`, and `proxy` (Caddy on `PROXY_PORT`, default `8088`) next to the existing named volumes. Do not use `-v`.

Copy new keys from `.env.example` into `.env` first (`MEDIA_DELIVERY_*`, `MINIO_PUBLIC_ENDPOINT`, `FRONTEND_ORIGINS`, `JWT_SECRET`).

## Reverse proxy

Caddy:

- serves the SPA (`try_files` → `index.html`)
- proxies `/api` and `/ws` to the API (WebSocket upgrade preserved)
- forwards `X-Request-ID`
- `request_body` max `CADDY_MAX_BODY` (default `32MB`) — enough for the default `8MB` chunk plus headers
- gzip
- hashed `/assets/*` → `Cache-Control: public, max-age=31536000, immutable`
- `index.html` → `no-cache`
- access logs strip query strings

TLS would terminate on this proxy (or a managed load balancer) in a real deployment. This milestone does not automate certificates.

## Health / readiness / graceful shutdown

- API: `/actuator/health/liveness` and `/readiness` (readiness still `db` + `minio` only)
- Worker: same on `:8081` (readiness `db` + `minio` + `ffmpeg`)
- Caddy: `GET /healthz`
- `server.shutdown=graceful` and a 30s shutdown phase
- Worker `stopAcceptingJobs()` before the RocketMQ consumer shuts down; in-flight FFmpeg is not killed immediately and is still bounded by `VIDEO_PROCESSING_TIMEOUT`

Redis / Elasticsearch / RocketMQ may be `DEGRADED` without taking the API down.

## Demo cleanup

Opt-in only: `scripts/demo-cleanup.sql` / `scripts/demo-cleanup.ps1`.

Removes `load12_*` users and rows they own. Does **not** reset volumes, wipe the database, or delete the M12 ~1410-view benchmark fixture on ordinary catalog videos.

## E2E verification

Run against existing Compose volumes plus host Maven API/worker (`MEDIA_DELIVERY_MODE` default `presigned`). Browser playback was **not** interactively clicked (no browser automation in this session). HTTP + automated frontend tests were used instead.

| Check | Result |
| --- | --- |
| Readiness | `GET /actuator/health/readiness` → `UP` (db + minio) |
| HLS playback descriptor (video 21) | `mode=HLS`, `url=/api/videos/21/hls/master.m3u8`, `deliveryMode=presigned`, `contentUrl`/`thumbnailUrl` are MinIO URLs with `X-Amz-` |
| Master playlist | Child variant URIs stay relative (`360p/index.m3u8`) |
| Variant playlist | Segment URIs are `http://127.0.0.1:9000/videos/processed/.../segmentNNN.ts?X-Amz-...` |
| Unsigned object GET | HTTP 403 |
| Presigned segment GET | HTTP 200 (174840 bytes for an existing 360p segment) |
| OPTIONS CORS from `http://127.0.0.1:5173` | 204, `Access-Control-Allow-Origin` echoes origin, `Range` allowed |
| Legacy descriptor (video 4) | `mode=LEGACY`, presigned raw URL |
| Presigned Range | HTTP 206, `Content-Range: bytes 0-15/9437184` |
| API proxy Range fallback | HTTP 206 |
| Card thumbnail | `/api/videos/{id}/thumbnail` (search/recs), JPEG 200 |
| No `/api/media/presign?key=` | 401 |
| Register / chunked upload / worker | New user `m13e2e_user1`, video 22 PENDING→PROCESSING→READY HLS |
| New upload playlist | `240p/index.m3u8` rewritten to a presigned `.ts` |
| Qualified view | `POST /views/qualify` on video 22 → `counted=true`, `viewCount=1` |
| Video 21 M12 fixture | Still 1410 views (not treated as a bug, not cleaned) |
| Search `q=GTA` | Hits videos 2 and 21 |
| Recent / trending / recommendations | Intact; card `thumbnailUrl` remains `/api/videos/{id}/thumbnail` |
| Notifications inbox | Empty list 200 for the new user |
| Danmaku history | `GET /api/videos/21/danmaku` 200 |

API-down decoupling (documented, not process-killed in this run): already-issued presigned URLs remain valid until TTL while MinIO is up.

## Known limitations

- MinIO is local S3-compatible storage, not a CDN
- Presigned URLs are not a CDN
- No autoscaling, Kubernetes, Terraform, Helm, or multi-region
- No private/unlisted video product model
- No DRM
- Long HLS sessions may outlive URL TTL
- `docker-compose.prod.yml` is production-**like**, not a production deployment
- Card thumbnails still use the small API proxy by design

## Scope exclusions (intentionally not started)

Kubernetes, Terraform, AWS/GCP/Azure deployment, paid CDN, DRM, transcoding autoscaling, live streaming, recommendation redesign, notification push/email, moderation, AI, M14.
