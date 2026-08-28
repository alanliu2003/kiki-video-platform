# Milestone 5 — Async Media Processing & HLS

## Goal

Turn a completed raw upload into asynchronously processed HLS renditions, a master playlist, and a thumbnail, then play the result in the browser without blocking `POST /api/uploads/{id}/complete` on FFmpeg.

## Scope

- processing state on the physical `media_objects` row
- transactional outbox for `MEDIA_PROCESSING_REQUESTED`
- local RocketMQ NameServer + Broker
- separate `backend/media-worker` process
- FFprobe metadata + FFmpeg HLS (H.264/AAC, MPEG-TS)
- API-proxied HLS/thumbnail playback
- frontend processing-state UI, polling, and hls.js playback
- legacy raw `/content` fallback

## Non-goals

Elasticsearch, search, recommendations, comments, likes, favorites, follows, danmaku, WebSockets, notifications, Spring Cloud Gateway, Eureka, microservice extraction, Kubernetes, CDN, DRM, signed URLs, subtitle pipelines, live streaming, AV1, GPU transcoding, and production orchestration.

## Architecture

```text
Upload complete
   ↓
PostgreSQL commit (video + PENDING media + outbox)
   ↓
API returns immediately
   ↓
scheduled outbox publisher → RocketMQ
   ↓
media-worker claims media_object
   ↓
stream raw/{sha256} → temp workspace → ffprobe → ffmpeg
   ↓
upload processed/{id}/staging → copy to processed/{id}/
   ↓
READY + HLS playback
```

The API and worker are two Spring Boot processes in one Maven repository. They are not microservices.

## Messaging model

Event payload:

```json
{
  "eventVersion": 1,
  "mediaObjectId": 42,
  "sha256": "...",
  "objectKey": "raw/...",
  "requestedAt": "2026-08-28T05:00:00Z"
}
```

The worker reloads authoritative media metadata from PostgreSQL. Credentials and full video records are never placed on the bus.

`MediaProcessingPublisher` is the API-side abstraction. The RocketMQ implementation is isolated. Tests use a no-op publisher.

## Outbox design

`media_processing_outbox` is written in the same transaction as video completion.

The API scheduled publisher:

1. claims due `PENDING` (or stale `PUBLISHING`) rows with `FOR UPDATE SKIP LOCKED`
2. publishes to RocketMQ
3. marks `PUBLISHED`, or returns the row to `PENDING` with backoff

Assumption: one API instance is enough for local development. Row claiming still avoids double-publish if two instances appear later.

If RocketMQ is down, upload completion still succeeds and the outbox retries.

## Processing state machine

Processing belongs to the physical media object, not each logical video.

```text
NOT_REQUESTED → PENDING → PROCESSING → READY
                              ↘ FAILED → PENDING (bounded retry)
```

Existing Milestone 3/4 `media_objects` rows stay `NOT_REQUESTED` and are not auto-processed.

## Worker architecture

`backend/media-worker` is a second executable. It exposes only Actuator on port `8081`.

Local run expects system `ffmpeg` / `ffprobe`. The worker image installs FFmpeg.

## FFmpeg pipeline

1. Stream `raw/{sha256}` to a unique temp directory
2. ffprobe duration/width/height/codecs
3. FFmpeg HLS renditions (no shell strings; `ProcessBuilder` argument arrays)
4. JPEG thumbnail
5. master playlist
6. upload staging prefix, copy to final prefix, delete staging
7. mark `READY`
8. always delete the temp workspace

Timeouts destroy hung processes. Default processing timeout is `30m`.

## HLS rendition ladder

Development defaults, no upscaling:

| Source | Renditions | Video bitrate |
| --- | --- | --- |
| &lt;360p | native only | 800 kbps |
| 480p | 360p + 480p | 800 / 2500 kbps |
| 720p | 360p + 720p | 800 / 2500 kbps |
| 1080p+ | 360p + 720p + 1080p | 800 / 2500 / 5000 kbps |

Audio is 128 kbps AAC when the source has audio. Segments are 6-second VOD MPEG-TS.

## MinIO output layout

```text
raw/{sha256}                         immutable source
processed/{mediaObjectId}/
  master.m3u8
  360p/index.m3u8
  360p/segment000.ts
  ...
  thumbnail.jpg
```

MinIO has no directory rename. Assets are uploaded to `processed/{id}/staging/`, copied to the final prefix, then staging is deleted. `READY` is set only after those uploads succeed.

## Thumbnail generation

One JPEG around 10% into the timeline, or 10% of duration for videos shorter than 2 seconds. Thumbnail upload is required for `READY`.

## Playback flow

- `GET /api/videos/{id}` includes `processingStatus`
- `GET /api/videos/{id}/playback` returns HLS or original metadata
- `GET /api/videos/{id}/hls/**` and `/thumbnail` proxy private MinIO objects
- `GET /api/videos/{id}/content` remains the raw/legacy Range endpoint

HLS paths are allow-listed. `..` and arbitrary object-key traversal are rejected.

Frontend polling (4s) continues while `PENDING`/`PROCESSING`. READY uses native HLS or hls.js.

## Retry and failure handling

- Max 3 processing attempts
- Claim increments `processing_attempts`
- Failure stores a truncated safe diagnostic (not raw FFmpeg stderr)
- Failed jobs under the attempt cap re-insert an outbox row with backoff
- Duplicate RocketMQ deliveries are safe because READY/PROCESSING cannot be blindly reclaimed
- Stale `PROCESSING` rows older than the stale window can be reclaimed

## Deduplication behavior

User B uploading bytes that already exist:

- `READY` → new logical video, no new job, HLS available immediately
- `PENDING`/`PROCESSING` → no second outbox/job
- `FAILED` under the attempt cap → a new request may be scheduled

## Legacy compatibility

- Milestone 3 videos with null `media_object_id` play through `/content`
- Historical Milestone 4 media stays `NOT_REQUESTED` and plays raw
- New `POST /api/videos` multipart uploads now hash, store/link `raw/{sha256}`, create a media object, and request processing

## Tests

Backend: outbox creation/dedupe, publisher success/retry, claim idempotency, HLS serving and path traversal, legacy Range playback, FFprobe parsing, mocked pipeline success/failure, optional real FFmpeg integration when binaries exist.

Frontend: PENDING/PROCESSING/FAILED UI, READY HLS init, polling stop, HLS destroy on unmount, legacy raw fallback.

## Manual verification

1. Upload an MP4, confirm the complete call returns immediately, watch PENDING→PROCESSING→READY, play HLS, seek, load the thumbnail.
2. Upload the same file again: no second FFmpeg job, new video is immediately READY.
3. Stop the worker, upload, confirm the API stays up and media stays PENDING, start the worker, confirm READY.

## Known limitations

- RocketMQ broker advertises `127.0.0.1` so host-run API/worker can connect. A Dockerized worker is not the default local path.
- The Compose broker runs as root so the named store volume is writable on Docker Desktop. This is a local-dev convenience, not a production setting.
- HLS is API-proxied, not CDN-backed.
- Ladder and bitrates are development defaults.
- Not every uploaded codec will decode; FFmpeg failure becomes `FAILED`.
- Stuck `PROCESSING` relies on the stale window, not a lease heartbeat.
- No signed URLs, DRM, or adaptive ABR beyond the generated ladder.

## Definition of Done

See the Milestone 5 request checklist: async complete, one physical transcode, HLS playback, legacy raw fallback, tests, and no `.env` edits.
