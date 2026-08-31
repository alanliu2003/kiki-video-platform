# Milestone 8 — Elasticsearch Video Search

## Goal

Add Elasticsearch as a rebuildable search projection of public video metadata. Users can search by title, description, and creator name, filter and paginate results, see safe highlights, and open existing video-detail pages. PostgreSQL remains the source of truth.

## Scope

- local Elasticsearch 8.17.10 in Docker Compose (`elasticsearch:8.17.10`, security disabled)
- versioned video index with an explicit mapping
- dedicated search outbox and asynchronous projector
- indexing on every logical video creation path
- processing READY/FAILED reindex via worker-written outbox rows
- explicit rebuild/backfill command
- public `GET /api/search/videos`
- Vue `/search` page and header search box
- Testcontainers coverage for indexing, query, rebuild, and unavailability

## Non-goals

Personalized recommendations, collaborative filtering, ML/vector/semantic search, OpenSearch, danmaku/comment/user search pages, Spring Cloud Gateway, Eureka, microservice extraction, Kubernetes, CDN, distributed tracing, production clustering, and Milestone 9.

## Architecture

```text
Vue
 ├── REST
 └── WebSocket
       │
       ▼
Spring Boot API
 ├── Auth/User
 ├── Video/Upload
 ├── Social
 ├── Danmaku
 ├── Search
 │     └── Elasticsearch
 ├── PostgreSQL
 └── Redis

PostgreSQL
   ↓
search_index_outbox
   ↓
SearchIndexOutboxPublisher
   ↓
Elasticsearch (kiki-videos → kiki-videos-v1)

RocketMQ
   ↓
media-worker
   ↓
FFmpeg
   ↓
MinIO
```

## Authority model

PostgreSQL is authoritative for users and videos. Elasticsearch is a derived projection. If the cluster is deleted, application data is unchanged and search can be rebuilt from PostgreSQL. Upload completion never waits on Elasticsearch.

## Index mapping

Alias `kiki-videos` points at versioned index `kiki-videos-v1` (or a rebuild index such as `kiki-videos-v1-<epoch>` after a successful rebuild).

Analyzer `kiki_text` is standard + lowercase + asciifolding. This is English-oriented. Chinese titles are not specially tokenized; do not expect IK/smartcn quality.

Fields:

| Field | Type |
| --- | --- |
| videoId | long, also used as `_id` |
| title | text (`kiki_text`) + `keyword` |
| description | text (`kiki_text`) |
| ownerId | long |
| ownerUsername | text + keyword |
| ownerDisplayName | text + keyword |
| status | keyword |
| processingStatus | keyword |
| createdAt | date |
| durationSeconds | double |
| thumbnailAvailable | boolean |

Dynamic mapping is `strict`. Categories/tags are not indexed because they do not exist in PostgreSQL.

## Search document

The projector reloads PostgreSQL before indexing. Event payloads are only `{ eventVersion, videoId }`.

## Indexing pipeline

1. API or worker writes PostgreSQL.
2. Same transaction (API) or immediately after READY/FAILED (worker) inserts `VIDEO_SEARCH_UPSERT` into `search_index_outbox`.
3. `SearchIndexOutboxPublisher` claims due rows with `FOR UPDATE SKIP LOCKED`.
4. Projector loads the logical video + owner + media fields and upserts `_id = videoId`.

Decision: dedicated `search_index_outbox`, not an extension of `media_processing_outbox`. The media outbox is keyed by `media_object_id` and publishes to RocketMQ. Search events are keyed by logical `videoId` and write to Elasticsearch. Mixing destinations would break the existing unique active-media constraint.

## Outbox / retry semantics

Statuses: `PENDING` → `PUBLISHING` → `PUBLISHED`. Failures return to `PENDING` with bounded exponential backoff (5s … 5m). Stale `PUBLISHING` rows are reclaimed. Duplicate in-flight UPSERTs per video are collapsed by a partial unique index.

## Eligibility rules

Every existing logical `videos` row is searchable:

- public catalog only (there is no private/hidden video state)
- legacy / `NOT_REQUESTED` / `PENDING` / `PROCESSING` / `READY` / `FAILED` media are included
- `FAILED` remains searchable because raw playback still exists
- search identity is the logical video, not the physical SHA-256

There is no video-edit or unpublish API. M8 therefore indexes at creation, processing READY/FAILED, and rebuild. Title/description changes would require a future edit API.

## Query/relevance model

`GET /api/search/videos` requires a non-blank `q`. Empty `q` is `400 INVALID_SEARCH_QUERY`. There is no browse/home feed.

Multi-match fields and boosts:

- `title^4`
- `ownerUsername^2`
- `ownerDisplayName^2`
- `description^1`

An extra `should` clause boosts exact `title.keyword` matches (`boost` 6). Default size 20, max 50. `from`/`size` pagination; pages whose `from` is ≥ 10000 are rejected. Deep pagination may later need `search_after`.

`tookMs` is Elasticsearch query time, not full API latency.

## Filtering/sorting

Sort: `RELEVANCE` (default), `NEWEST`, `OLDEST`.

Filters: `ownerId`, `processingStatus`, `createdAfter`, `createdBefore`.

`MOST_LIKED` is omitted because social counters are not in the search document.

## Highlighting

Elasticsearch uses custom markers `[[HIGHLIGHT]]…[[/HIGHLIGHT]]`. The API parses those into `{ text, highlighted }` spans. The Vue page never uses `v-html`.

## Rebuild/backfill

Not automatic on startup.

```powershell
cd backend
.\mvnw.cmd -pl api -am spring-boot:run "-Dspring-boot.run.arguments=--app.search.rebuild=true"
```

The runner creates a new versioned index, pages PostgreSQL in batches (`SEARCH_REBUILD_BATCH_SIZE`, default 250), bulk indexes, switches alias `kiki-videos`, and deletes the previous index only after success. Partial failure is thrown and logged with real indexed/failed counts.

## Failure behavior

| Situation | Behavior |
| --- | --- |
| Elasticsearch down during upload | PostgreSQL commit succeeds; outbox retries |
| Search API while ES down/disabled | `503 SEARCH_UNAVAILABLE` — no SQL LIKE fallback |
| Mapping missing | created on first successful ensure, never destructively recreated on startup |
| Local security | Compose disables xpack security. LOCAL DEVELOPMENT ONLY |

Eventual consistency window: typically one outbox poll (`SEARCH_OUTBOX_POLL_INTERVAL`, default 5s) plus Elasticsearch refresh (about 1s). Frontend may open `/videos/{id}` before search catches up.

## Frontend search flow

Header `SearchBar` submits to `/search?q=…&page=0`. `SearchView` reads query-string state, supports sort/processing filters, cancels in-flight requests, and links cards to `/videos/{id}`. States: LOADING, RESULTS, EMPTY, ERROR.

## Tests

- outbox created on legacy and resumable/dedupe completion
- duplicate UPSERT → one document
- two logical videos sharing physical media → two documents
- title/description/creator/unrelated/pagination/sort/filter/highlight/typo
- FAILED videos remain searchable
- rebuild is idempotent and restores eligible rows
- disabled Elasticsearch → 503; blank `q` → 400
- existing M1–M7 tests keep `ELASTICSEARCH_ENABLED=false`

## Manual verification

1. `docker compose up -d` and `curl.exe http://127.0.0.1:9200`
2. Start API with Elasticsearch enabled
3. Rebuild if videos already exist
4. Search title, description, and creator
5. Upload a new video: complete returns before search; it appears shortly afterward
6. Dedupe upload: both logical titles appear
7. `docker compose stop elasticsearch`: detail/upload/social/danmaku still work; search returns 503
8. Start Elasticsearch again: pending outbox rows index; search recovers
9. Delete the alias/index only in a controlled local test, rebuild, confirm counts

```powershell
curl.exe http://127.0.0.1:9200/_cat/indices?v
curl.exe http://127.0.0.1:9200/kiki-videos/_mapping
curl.exe "http://127.0.0.1:9200/kiki-videos/_search?q=title:trailer"
```

## Known limitations

- Standard analyzer; no Chinese plugin
- No video metadata edit API, so title/description changes are not reindexed until one is added
- `from`/`size` is not suitable for very deep pages
- Local Elasticsearch has security disabled
- Search is eventually consistent
- No recommendations or most-liked sort

## Definition of Done

Elasticsearch runs locally, mapping is explicit and versioned, PostgreSQL stays authoritative, new logical videos are indexed asynchronously, rebuild works, search API and Vue page work, Elasticsearch outage does not break creation, and existing M1–M7 tests remain green.
