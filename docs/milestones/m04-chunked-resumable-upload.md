# Milestone 4 — Chunked & Resumable Upload

## Goal

Replace the fragile single-request upload path with a chunked, resumable, SHA-256-deduplicated ingestion protocol. Large files can be hashed incrementally, uploaded in independent chunks, resumed after interruption, and finalized into one physical MinIO object without loading the whole file into JVM or browser memory.

Playback stays API-proxied and unchanged:

```text
GET /api/videos/{id}
GET /api/videos/{id}/content
```

## Scope

- upload session persistence
- client-side incremental SHA-256
- configurable chunking
- init / status / chunk / complete APIs
- resume of an active session for the same user + hash + size
- physical SHA-256 deduplication
- idempotent completion
- scheduled cleanup of abandoned temporary chunks
- frontend resumable upload UX
- backend and frontend tests
- documentation

## Non-goals

FFmpeg, transcoding, HLS/DASH, thumbnails, adaptive bitrate, RocketMQ, media workers, Elasticsearch, comments, likes, favorites, follows, danmaku, WebSockets, CDN, signed playback URLs, microservices, Gateway, Eureka, Jenkins, Kubernetes, Redis locks, background upload after tab close, and multi-device resume guarantees.

## Upload protocol

```text
Browser selects file
      ↓
compute SHA-256 in pieces
      ↓
POST /api/uploads/init
      ↓
server checks media_objects + active session
      ↓
browser uploads missing chunks
      ↓
GET /api/uploads/{uploadId} to resume
      ↓
POST /api/uploads/{uploadId}/complete
      ↓
assemble/finalize raw/{sha256} while hashing
      ↓
persist media_objects + videos
```

`POST /api/videos` remains as a legacy multipart endpoint. The Vue upload page no longer uses it.

## Database schema

Flyway migration: `V3__create_upload_sessions.sql`. `V1` and `V2` are unchanged.

```text
media_objects
-------------
id, sha256 UNIQUE, object_key UNIQUE, file_size_bytes, content_type, created_at

videos (additive)
-----------------
media_object_id nullable FK
file_sha256 nullable
UNIQUE(object_key) dropped so logical videos can share one physical key

upload_sessions
---------------
id UUID
user_id
file_name
file_size_bytes
file_sha256
content_type
chunk_size_bytes
total_chunks
status
deduplicated
final_video_id
created_at, updated_at, expires_at

upload_chunks
-------------
(upload_session_id, chunk_index) PK
chunk_size_bytes
chunk_sha256
created_at
```

Statuses: `INITIATED`, `UPLOADING`, `COMPLETING`, `COMPLETED`, `FAILED`, `EXPIRED`.

A partial unique index prevents two active sessions for the same user and SHA-256.

## Chunk storage layout

Temporary:

```text
uploads/{uploadId}/chunks/{chunkIndex}
```

Final physical objects for new uploads:

```text
raw/{sha256}
```

Milestone 3 objects stay at `videos/{userId}/{uuid}.ext`. Playback still uses `videos.object_key`.

## Resume semantics

Init for the same authenticated user, SHA-256, and file size returns the existing non-expired session and its uploaded chunk indexes.

After a page refresh the browser cannot keep the original `File`. The user re-selects the same file, the client hashes it again, and init resumes the server session. localStorage only stores `{uploadId, fileSha256, fileName, fileSize}` as a hint.

This is not automatic background resume after the tab is closed.

## Deduplication model

SHA-256 identifies a physical file. If `media_objects` already has that digest, init returns `deduplicated=true` and `uploadRequired=false`. The client skips chunk upload and still calls complete with title/description.

## Physical vs logical video model

```text
physical media dedupe  !=  logical video dedupe
```

Two users (or two uploads) of the same bytes get:

- one `media_objects` row
- one MinIO object at `raw/{sha256}`
- two `videos` rows with their own title, description, owner, and created_at

Legacy Milestone 3 rows may have null `media_object_id` / `file_sha256` and still play through `object_key`.

## Completion flow

1. Authenticate the session owner.
2. Reject expired, failed, or incomplete sessions.
3. If a media object already exists, reuse it.
4. Otherwise stream chunks in order into `raw/{sha256}` while computing SHA-256. The backend does not buffer the whole file.
5. Compare assembled size and digest to the session.
6. Insert `media_objects` (unique SHA-256 handles races).
7. Insert a logical `videos` row.
8. Mark the session `COMPLETED`.
9. Delete temporary chunk objects and rows.

Repeating complete on a finished session returns the same video.

## Failure handling

| Code | Meaning |
| --- | --- |
| `UPLOAD_NOT_FOUND` | Unknown id or another user's session |
| `UPLOAD_EXPIRED` | TTL elapsed |
| `UPLOAD_ALREADY_COMPLETED` | Reserved; completed sessions return the video instead |
| `UPLOAD_INVALID_STATE` | Failed/completing session cannot accept chunks |
| `UPLOAD_CHUNK_OUT_OF_RANGE` | Bad index |
| `UPLOAD_CHUNK_SIZE_INVALID` | Chunk length mismatch |
| `UPLOAD_INCOMPLETE` | Complete called before every chunk exists |
| `UPLOAD_HASH_MISMATCH` | Assembled digest or size is wrong |
| `UPLOAD_FILE_TOO_LARGE` | Above `VIDEO_MAX_FILE_SIZE` |
| `UPLOAD_STORAGE_ERROR` | MinIO failure without internals |

Re-uploading an already stored chunk is a no-content success. Permanent 4xx errors are not retried by the client.

## Cleanup

`UploadCleanupJob` runs on `VIDEO_UPLOAD_CLEANUP_INTERVAL` (default 15 minutes). It finds non-completed sessions with `expires_at` in the past, deletes `uploads/{uploadId}/chunks/*`, deletes chunk rows, and marks the session `EXPIRED`.

Accessing an expired session also expires it lazily.

## Frontend behavior

- `frontend/src/services/sha256.ts` hashes with `@noble/hashes` in 8 MiB pieces. The selected file is never fully buffered.
- `frontend/src/services/uploadManager.ts` inits, uploads missing chunks with concurrency 4, retries transient failures three times, and completes.
- Progress is `uploadedBytes / totalBytes` plus phase text: Hashing, Checking, Uploading, Resuming, Finalizing, Complete.
- Dedupe shows: `File already exists on the server. Upload skipped.`
- The user can still enter title and description for a new logical video.

## Tests

Backend Testcontainers coverage includes init, resume, ownership, chunk validation, idempotent chunks, incomplete complete, successful assemble + playback, hash mismatch, physical dedupe with two logical videos, and expired cleanup.

Frontend tests cover slicing, missing-chunk math, retries, incremental hashing, resume/dedupe manager paths, and navigation after upload.

## Manual verification

1. Normal upload: select MP4 → hash → chunks → complete → play.
2. Interrupted upload: stop after some chunks, refresh, re-select the same file, resume, complete, play.
3. Dedupe: upload once, upload the same file again, second time skips chunks, a new logical video is created.

## Known limitations

- Resume after refresh requires re-selecting the same local file.
- There is no multi-device or post-tab-close background upload.
- Whole-file integrity is checked while assembling, which reads each chunk once more after upload.
- Legacy multipart upload is still size-limited by `VIDEO_MAX_UPLOAD_SIZE`.
- Redis is still unused.
- Playback is still the original uploaded bytes through the API.

## Definition of Done

See the Milestone 4 checklist: session migration, incremental hashing, init/resume/chunks/complete, streamed assembly, SHA-256 validation, physical vs logical dedupe, legacy playback, cleanup, frontend chunked UX, tests, build, Docker health, and no `.env` edits.
