# Milestone 3 — Video Core & MinIO

## Goal

Give an authenticated creator a complete first video vertical slice: enter metadata, upload one MP4 (or WebM), store the object in MinIO, persist metadata in PostgreSQL, list their videos, open a public detail page, and play the file in the browser.

## Scope

- `videos` table via Flyway `V2__create_videos.sql`
- Video domain, MyBatis persistence, and `VideoStorage` abstraction
- MinIO Java SDK integration with idempotent bucket creation
- Authenticated `POST /api/videos` multipart upload
- Public video metadata and streamed playback
- Authenticated current-user video list
- Vue upload, my-videos, and detail/player pages
- Backend Testcontainers coverage (PostgreSQL + MinIO) and lightweight frontend tests

## Non-goals

Chunked or resumable upload, SHA-256 dedupe, FFmpeg, transcoding, HLS/DASH, adaptive bitrate, thumbnails, RocketMQ, media workers, Elasticsearch, comments, likes, favorites, follows, danmaku, WebSockets, view-count analytics, CDN, signed public MinIO URLs, microservices, Gateway, Eureka, Kubernetes, and Jenkins.

## Architecture changes

```text
Vue 3
 │
 │ JWT / multipart upload / video requests
 ▼
Spring Boot
 │
 ├── Auth
 ├── User
 └── Video
      │
      ├── MyBatis ──────► PostgreSQL
      │
      └── MinIO SDK ────► MinIO
```

Redis is still unused by application code.

Raw MP4/WebM playback through the API is temporary. Later milestones will add chunked/resumable upload, async media processing, FFmpeg, HLS, and adaptive streaming. Those are not implemented here.

## Database schema

Flyway migration: `backend/api/src/main/resources/db/migration/V2__create_videos.sql`

`V1__create_users.sql` is unchanged.

```text
videos
------
id                  BIGSERIAL PRIMARY KEY
owner_user_id       BIGINT NOT NULL  → users(id)
title               VARCHAR(120) NOT NULL
description         VARCHAR(2000)
object_key          VARCHAR(512) NOT NULL UNIQUE
original_filename   VARCHAR(255) NOT NULL
content_type        VARCHAR(100) NOT NULL
file_size_bytes     BIGINT NOT NULL
status              VARCHAR(20) NOT NULL
created_at          TIMESTAMPTZ NOT NULL
updated_at          TIMESTAMPTZ NOT NULL
```

Application title rule: 1–120 characters after trim. Description is optional and limited to 2000 characters.

`status` CHECK values: `UPLOADED`, `PROCESSING`, `READY`, `FAILED`. Milestone 3 only writes `UPLOADED`.

Video bytes are never stored in PostgreSQL.

## MinIO storage design

Configuration:

```text
MINIO_ENDPOINT=http://127.0.0.1:9000
MINIO_ROOT_USER / MINIO_ROOT_PASSWORD
MINIO_VIDEO_BUCKET=videos
```

On startup the API ensures the bucket exists. The operation is idempotent. If MinIO is unreachable, startup fails with a clear storage error.

The bucket is not given anonymous public access. The browser never talks to MinIO directly.

Object keys are server-generated:

```text
videos/{userId}/{uuid}.mp4
```

or `.webm` when the uploaded content type is `video/webm`. The original filename is stored only as metadata after path components are stripped. User-supplied names are never used as object keys.

## Upload flow

1. Authenticate the caller from the JWT principal.
2. Validate title, optional description, file presence, content type, and size.
3. Generate an internal object key.
4. Stream the file into MinIO.
5. Insert the `videos` row.
6. If the insert fails, attempt best-effort MinIO object deletion.

That last step is compensation, not a distributed transaction. A crash between put and insert can still leave an orphan object.

Allowed types: `video/mp4` and `video/webm`. Browser playback still depends on the actual codec inside the container. MP4 is not a guarantee that every browser can decode the file.

Default local upload limit: `250MB` (`VIDEO_MAX_UPLOAD_SIZE`).

## Playback flow

```text
Browser <video>
      │
      ▼
GET /api/videos/{id}/content
      │
      ▼
MinIO GetObject (offset + length)
```

The API streams the object. It does not call `readAllBytes()` or otherwise buffer the whole file.

HTTP Range is supported:

- no `Range` → `200` + full stream
- `Range: bytes=start-end` → `206` with `Accept-Ranges`, `Content-Range`, and `Content-Length`
- malformed range → `400 INVALID_RANGE`
- unsatisfiable range → `416` with `Content-Range: bytes */total`

## API endpoints

| Method | Path | Auth | Success |
| --- | --- | --- | --- |
| POST | `/api/videos` | Bearer JWT | `201` video payload |
| GET | `/api/videos/{videoId}` | Public | `200` video payload |
| GET | `/api/videos/{videoId}/content` | Public | `200` or `206` media stream |
| GET | `/api/users/me/videos` | Bearer JWT | `200` `{ items, page, size, total }` |

`GET /api/users/me/videos` uses `?page=0&size=20`. Size is capped at 50. Owner identity comes from the JWT, not from a request user id.

Responses include owner `{ id, username, displayName }` on upload and detail. They do not include `objectKey`, credentials, or filesystem paths.

New error codes: `VIDEO_NOT_FOUND`, `VIDEO_FILE_REQUIRED`, `UNSUPPORTED_VIDEO_TYPE`, `VIDEO_FILE_TOO_LARGE`, `VIDEO_STORAGE_ERROR`, `INVALID_VIDEO_TITLE`, `INVALID_RANGE`.

## Frontend changes

| Route | Auth | View |
| --- | --- | --- |
| `/videos/upload` | Required | `VideoUploadView` |
| `/videos/:id` | Public | `VideoDetailView` |
| `/my/videos` | Required | `MyVideosView` |

The upload form shows the selected filename and size, disables resubmit while in flight, and shows progress when Axios reports it. After success it navigates to `/videos/{id}`.

The player is a native `<video controls>` element whose `src` is `/api/videos/{id}/content`. Playback is public, so the browser requests that URL directly.

API calls live in `frontend/src/api/videos.ts`.

## Failure handling

MinIO upload then PostgreSQL insert. Database failure triggers best-effort object deletion. MinIO failures are mapped to `VIDEO_STORAGE_ERROR` without leaking bucket or SDK details.

## Tests

Backend tests start PostgreSQL and MinIO with Testcontainers.

Covered:

- unauthenticated upload
- valid MP4 upload, ownership, server-generated object key
- empty file, unsupported type, invalid title
- MinIO failure → safe API error
- DB insert failure → compensation delete
- public detail `200` / unknown `404`
- my-videos auth, ownership filter, `created_at DESC`
- full content `200`, `Range: bytes=0-1023` → `206`, malformed and unsatisfiable ranges, unknown content `404`
- `HttpByteRange` parser

Frontend Vitest covers route protection, the videos API helper, upload navigation, detail rendering, and the my-videos list.

## Manual verification

See [docs/development.md](../development.md) for the register → upload → play → my-videos flow, plus MinIO and PostgreSQL inspection commands.

## Known limitations

- Single-request upload only. No chunking, resume, or parallel parts.
- Playback is the original uploaded file, proxied by the API. No HLS, DASH, or transcoding.
- Browser codec support is not validated. An MP4 that uses an uncommon codec may fail to play.
- Range support is single-range only.
- Compensation delete is best-effort. Orphan MinIO objects are possible.
- Videos are public once uploaded. There is no private/unlisted visibility.
- No edit, delete, thumbnails, comments, or view counts.
- Redis remains unused.
- Frontend JWTs still live in `localStorage`.

## Definition of Done

- [x] V2 migration creates `videos`
- [x] Authenticated upload stores MinIO object + PostgreSQL row
- [x] Unauthenticated upload is rejected
- [x] File and title validation work
- [x] Object keys are server-generated
- [x] Video detail, my-videos, and streamed playback work
- [x] HTTP Range is implemented and tested
- [x] Frontend upload / detail / my-videos pages exist
- [x] Backend and frontend tests, plus frontend production build
- [x] Documentation updated
- [x] `.env` was not modified by this milestone
