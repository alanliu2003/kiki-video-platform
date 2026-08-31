# Milestone 10 — Personalized Recommendations

## Goal

Add a **deterministic, explainable** personalized recommendation feed for authenticated users, built from signals the platform already stores. This is **not** machine learning, embeddings, collaborative filtering, or an “AI algorithm.”

Anonymous users keep the existing global trending and newest-uploads experience. They do not receive fake personalization.

## Scope

- `GET /api/recommendations/videos` for JWT users
- authenticated qualified-view history (`user_video_qualified_views`)
- candidate generation from follows, creator affinity, trending, and recent uploads
- explicit weighted scoring with documented reasons
- short Redis page cache with PostgreSQL fallback
- home “Recommended for you” section that does not block trending/recent

## Non-goals

Collaborative filtering, matrix factorization, embeddings, vector databases, deep learning, external recommendation APIs, Kafka, a recommendation microservice, watch-history UI, CDN, notifications, production deployment, and large-scale performance claims.

## Architecture

```text
Authenticated home
  └─ GET /api/recommendations/videos
        ├─ Redis page cache (fail-open, short TTL)
        └─ PostgreSQL
             ├─ profile: follows, likes, favorites, comments, qualified views
             ├─ candidates: followed / affinity / trending / recent (each bounded)
             └─ deterministic score + reason → page slice

Anonymous home
  ├─ GET /api/videos/trending
  └─ GET /api/videos/recent
```

PostgreSQL remains authoritative. Redis never holds the only copy of preference or ranking state. Elasticsearch is not used for recommendation correctness.

Likes, favorites, comments, and follows stay in their existing tables. This milestone does **not** duplicate those flags into an aggregate profile row. The only new durable table is authenticated qualified-view history, because Milestone 9’s `video_view_idempotency` has no `user_id`.

## Durable behavioral data

| Signal | Source | Role |
| --- | --- | --- |
| Followed creators | `user_follows` | candidate source + score boost + reason |
| Likes | `video_likes` | creator affinity |
| Favorites | `video_favorites` | creator affinity |
| Comments | `comments` (`ACTIVE`) | creator affinity |
| Qualified views | `user_video_qualified_views` | affinity, seen penalty, heavy-seen exclusion |
| Global popularity | `videos.view_count` + engagement counts | trending candidates + score |
| Recency | `videos.created_at` | recent candidates + freshness boost |

Search queries are not recorded and are not used.

`user_video_qualified_views`:

```text
PRIMARY KEY (user_id, video_id)
qualified_view_count  >= 1
last_qualified_at
```

A successful authenticated qualify (`counted=true`) upserts the row in the same transaction as the logical `view_count` increment. Anonymous qualifies do not write this table. Retrying the same `clientViewId` does not increment either counter.

## Profile signals

At request time, bounded by `RECOMMENDATION_HISTORY_LIMIT` (default 200) per signal:

- followed creator ids
- creator affinity points from recent likes / favorites / comments / qualified views on other people’s videos
- recent qualified-view counts for the current user

Affinity points for one creator:

```text
likes * 2 + favorites * 3 + comments * 2 + qualifiedViews * 1
```

`creatorAffinity` used in scoring is `log1p(affinityPoints)`.

## Candidate sources

Each source is SQL-bounded (`RECOMMENDATION_SOURCE_LIMIT`, default 50). The merged pool is capped at `RECOMMENDATION_CANDIDATE_LIMIT` (default 200). Dedupe is by logical `video.id`.

| Source | Query |
| --- | --- |
| A. Followed creators | recent videos owned by followed users |
| B. Affinity creators | recent videos owned by top affinity creators (default 20) |
| C. Trending | existing deterministic trending query |
| D. Recent uploads | existing newest-first query |

Similar-audience / collaborative filtering is not implemented.

The catalog is not loaded into Java. Engagement counts for the candidate id set are fetched in one query.

## Scoring formula

Exact defaults (`application.yml` / `.env.example`):

```text
score =
    log1p(affinityPoints) * 4
  + followedCreatorBoost * 3          # 1 if the user follows the owner, else 0
  + log1p(viewCount) * 1.5
  + log1p(likeCount) * 1.2
  + log1p(favoriteCount) * 1.5
  + log1p(commentCount) * 0.8
  + max(0, 48 - ageHours) * 0.05
  - alreadySeenPenalty
```

`alreadySeenPenalty`:

- 0 if the user has no authenticated qualified view
- 4 if `qualified_view_count == 1`
- 10 if `qualified_view_count >= 2`

Secondary order is `videoId DESC`. The formula is implemented in `RecommendationScore` and unit-tested. Weights are configurable; they are not learned.

## Recommendation reasons

Reasons are derived from actual signals, not raw scores:

| Condition (first match) | Reason |
| --- | --- |
| User follows the creator | Because you follow this creator |
| Affinity points > 0 and age ≤ 48h | New from a creator you engage with |
| Affinity points > 0 | From a creator you engage with |
| Candidate came from the trending source | Trending now |
| Otherwise | New upload |

## Cold start

`coldStart` is true when the user has no follows, no affinity creators, and no qualified-view rows.

The endpoint still returns a blended trending + recent pool with those reasons. It does not return an empty page merely because personalization data is sparse.

Anonymous callers receive `401 UNAUTHORIZED`. The frontend never asks for recommendations unless signed in.

## Seen / exclusion policy

| Rule | Behavior |
| --- | --- |
| Own videos | always excluded |
| One qualified view | penalty only; still eligible |
| `qualified_view_count >= 3` (default) | excluded **if** the remaining pool still has at least one page of items; otherwise kept with the heavy penalty so the feed is not emptied |
| Like / favorite / comment without a qualify | affinity only; not treated as “seen” |
| Invalid / missing rows | skipped |

A page load or a short watch below the M9 threshold is not “seen.”

## Redis / cache

Key: `kiki:recommendations:user:{userId}:page:{page}:size:{size}`

TTL default `2m`. Miss or Redis error → compute from PostgreSQL. Write failures are logged and ignored.

There is no event-driven invalidation. A follow/like/qualify may take up to the TTL to appear. That is intentional.

Durable qualified-view writes do not depend on Redis succeeding.

## Pagination

Page size is bounded (`RECOMMENDATION_MAX_PAGE_SIZE`, default 50; request default 20).

Ranking happens on the capped candidate pool, then the page is sliced in memory. `total` is the ranked pool size, not `COUNT(*)` of all videos. Deep pages past the pool return empty `items`.

## API

| Method | Path | Auth | Result |
| --- | --- | --- | --- |
| GET | `/api/recommendations/videos?page=0&size=20` | JWT required | `{ items, page, size, total, coldStart }` |

Each item matches the existing video card shape plus `recommendationReason`:

```json
{
  "id": 12,
  "title": "Clip",
  "owner": { "id": 3, "username": "alice", "displayName": "Alice" },
  "createdAt": "2026-08-31T01:00:00Z",
  "durationSeconds": 90,
  "thumbnailUrl": "/api/videos/12/thumbnail",
  "processingStatus": "READY",
  "viewCount": 40,
  "likeCount": 2,
  "recommendationReason": "Because you follow this creator"
}
```

Existing `GET /api/videos/trending` and `GET /api/videos/recent` are unchanged.

## Frontend

Authenticated home:

1. Recommended for you
2. Trending
3. New uploads

Anonymous home: trending and new uploads only. No recommendation request.

The recommendation section has its own loading, empty, error, and cold-start copy. A recommendation failure does not hide trending or recent. Cards still navigate to `/videos/{id}`. Reasons render as a small line when present.

Copy says **deterministic personalized ranking**, not AI/ML.

## Failure behavior

| Failure | Result |
| --- | --- |
| Anonymous recommendation request | 401 |
| Redis down | live PostgreSQL compute; qualify still writes durable rows |
| Recommendation API error on home | that section shows an error; trending/recent continue |
| Sparse personalization data | cold-start blend, not an empty page |
| Empty catalog | empty items, `total = 0` |

## Test coverage

Backend:

- cold-start fallback
- follow boost and affinity ranking
- own-video exclusion
- candidate dedupe by `videoId`
- seen penalty and heavy-seen exclusion
- deterministic order and bounded pagination
- Redis cache hit (stale until flush)
- Redis-down fallback
- anonymous 401 without breaking trending/recent
- logical videos that share `media_object_id` stay independent
- qualified-view upsert uniqueness
- Flyway table/primary key present

Frontend:

- anonymous home does not request recommendations
- authenticated home requests them and shows reasons
- recommendation failure leaves trending/recent visible
- cold-start copy
- existing loading/empty/error behavior
- card links to video detail

## Known limitations

- Ranking is heuristic. It will not match a trained recommender.
- The candidate pool is capped. Videos outside the four bounded sources cannot appear.
- Short-TTL cache can serve a stale page for a couple of minutes after a new follow or qualify.
- Anonymous watch history is not merged on login.
- Search behavior is unused.
- `videos.status` is still an upload-pipeline field; discovery/recommendation do not add a visibility model.
- Clients can still lie about `watchedMs` (unchanged from M9).

## Why this is deterministic, not ML

Every score term is an explicit constant or `log1p` of a stored count. There is no training set, no gradient, no embedding, and no hidden model. The same inputs always produce the same order (`score DESC`, then `id DESC`).
