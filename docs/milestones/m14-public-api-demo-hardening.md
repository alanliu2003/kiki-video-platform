# Milestone 14 — Public API & Demo Hardening

## Goal

Make kiki-video-platform easier to evaluate externally: a coherent public read API, OpenAPI documentation, public creator profiles, consistent pagination/error semantics, and a polished demo UX. This is not a new core infrastructure milestone.

## Endpoint inventory (M13 baseline)

| Class | Endpoints |
| --- | --- |
| Public read | `GET /api/health`, video detail/playback/HLS/thumbnail/content, interactions, comments, danmaku history, search, trending, recent, creator relationship |
| Authenticated read | `GET /api/users/me`, `GET /api/users/me/videos`, recommendations, notification inbox / unread-count |
| Authenticated write | register/login (public write), upload, likes/favorites/follows, comments, qualify view (optional auth), notification mark-read |
| Internal / actuator | `/actuator/health`, `/info`, `/metrics`, `/prometheus` (not product API) |
| WebSocket | `GET /ws/videos/{id}/danmaku` |

Observed inconsistencies (documented, not mass-renamed):

- Pagination is zero-based with `items/page/size/total`. Defaults are usually 20. Max size is 50 for most lists; discovery/trending may clamp to `TRENDING_MAX_PAGE_SIZE` (default 50, tests use 500). Negative page and oversized size are **clamped**, not rejected.
- Danmaku history is a bare array (windowed by `fromMs`/`toMs`), not a page envelope.
- Search returns extra `tookMs`. Recommendations add `coldStart`.
- Register returns email/role; public profile does not.
- `/api/users/me` remains private. `/api/users/{id}` is the new public profile.

## Public API decision

Existing public GETs stay at their current paths. No `/public/...` duplicates. No `/api/v1` prefix.

New public reads:

- `GET /api/users/{userId}` — public profile
- `GET /api/users/{userId}/videos` — newest-first video cards

Not exposed: email, roles, notification inbox, recommendation internals, actuator, storage keys, MinIO credentials, outbox rows.

The API is **unversioned / pre-v1**. Backward compatibility is best-effort until a stable version is declared. OpenAPI is documentation, not a compatibility contract.

## Auth model

JWT bearer access tokens from `POST /api/auth/login`. Public reads work anonymously. Follow state (`followedByCurrentUser`) appears on the public profile only when a token is presented. `/api/users/me` still requires auth and still returns email/role.

## OpenAPI setup

- `org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.0` (Spring Boot 4.1.x)
- `GET /v3/api-docs`
- Swagger UI at `/swagger-ui.html` → `/swagger-ui/index.html`
- JWT scheme `bearer-jwt`; authenticated operations marked; public GETs are not
- Tags: Auth, Videos, Discovery, Search, Users, Social, Comments, Danmaku, Recommendations, Notifications, Playback
- Actuator excluded (`springdoc.show-actuator=false`, packages-to-scan, customizer)
- Examples for video detail, search, trending/recent, recommendations, notifications, playback, qualified view, public profile
- Vite and Caddy proxy `/v3` and `/swagger-ui` to the API

## Profile model

`GET /api/users/{userId}` returns:

`id`, `username`, `displayName`, `createdAt`, `followerCount`, `followingCount`, `publicVideoCount`, `totalViews`, and optional `followedByCurrentUser`.

Frontend route: `/users/:id`. Follow/unfollow reuse M11 persistence and notifications. Self profile hides the follow button. Anonymous visitors see counts only.

`GET /api/users/{userId}/videos` reuses `VideoCardResponse` (thumbnail, duration, view count, owner). Newest first. Page default 20, max 50. One joined query, no N+1.

## Pagination / errors

| Convention | Behavior |
| --- | --- |
| `page` | Zero-based; negative values clamp to 0 |
| `size` | Default 20; clamped to the endpoint max |
| Envelope | `items`, `page`, `size`, `total` (exceptions: danmaku list, search `tookMs`, recommendations `coldStart`) |
| Errors | `{ "code", "message", "timestamp", "requestId" }` plus `X-Request-ID` |

Invalid search `sort` / `processingStatus` remain 400. Search unavailable remains 503. Stack traces are not returned.

## Demo seed / cleanup

- `scripts/demo-seed.ps1` / `.sh` — opt-in `demo_alice`, `demo_bob`, `demo_cara` plus follows; optional like/comment on an existing catalog video. No media in git. No volume reset.
- `scripts/demo-seed-cleanup.sql` / `.ps1` / `.sh` — `demo_%` only, confirm `DELETE-DEMO`
- M13 `load12_*` cleanup is unchanged and separate

## UX polish

- Public profile page with loading/empty/error
- Creator names are links on home cards, search, video detail, comments, and follow notifications
- Home hides duplicate recommended IDs from Trending / New uploads (presentation only)
- Search empty-query vs zero-results copy
- Upload rejects non-MP4/WebM with a human-readable error
- Lightweight account menu (public profile / account / log out)
- Notification bell `aria-label` includes unread count; unread is not color-only

## Accessibility / responsive

- Main nav `aria-label`
- Search input labeled
- Follow / danmaku / notification controls labeled
- Thumbnails have `alt`
- `:focus-visible` outline
- Nav wraps on narrow widths; card grids shrink at 640px

## Security review

- Swagger does not prefill JWTs (`persist-authorization: false`)
- Public profile never includes email
- `/api/users/me` stays authenticated
- Notifications and recommendations stay protected
- Actuator is not in the product OpenAPI document
- CORS unchanged (explicit origins, no `*` with credentials)

## Automated verification

- Frontend: `npm test` — 112 tests passed; `npm run build` succeeded
- Backend: `PublicProfileIntegrationTest` (7), `OpenApiIntegrationTest` (2), `RequestIdTest`/`RequestIdIntegrationTest`, `AuthIntegrationTest` passed
- Full `api` suite: 186 tests; one pre-existing MinIO presigned GET flake in `MediaDeliveryIntegrationTest` (403 then 200 on isolated retry)
- API smoke against a running host API was not executed (nothing listening on `:8080`)

## Manual E2E

Interactive browser clicking was not run in the implementing session. HTTP smoke + frontend tests cover the public/API paths. Production-like Caddy routes for `/users/:id`, `/v3`, and `/swagger-ui` were added in config.

## Known limitations

- Unversioned API; no stability/version guarantee
- No rate-limit gateway, OAuth, or developer portal
- No private/unlisted profiles or videos
- No moderation, avatars, email verification, password reset, or refresh tokens
- No browser automation
- Local Compose remains production-like only
- Demo seed does not add copyrighted sample media
- OpenAPI is documentation, not a compatibility guarantee
