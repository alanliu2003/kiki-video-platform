# Load tests

k6 scripts for bounded local Milestone 12 validation. These are **not** production capacity numbers.

## Prerequisites

- API on `http://127.0.0.1:8080`
- Docker infra already running (`docker compose up -d`)
- At least one public video for view/social/HLS scenarios
- [k6](https://k6.io/docs/get-started/installation/)

Install k6 on Windows with the official installer, or run the image (used for the M12 measurements):

```powershell
docker run --rm `
  -e BASE_URL=http://host.docker.internal:8080 `
  -e VUS=10 -e DURATION=45s `
  -e K6_SUMMARY_TREND_STATS=avg,min,med,p(90),p(95),p(99),max `
  -v ${PWD}/load-tests:/scripts `
  grafana/k6:0.54.0 run /scripts/scenarios/read-heavy.js
```

## Commands

From the repository root, if `k6` is on PATH:

```powershell
k6 run load-tests/scenarios/read-heavy.js
k6 run load-tests/scenarios/view-qualify.js
k6 run -e VUS=8 -e DURATION=30s load-tests/scenarios/view-idempotency.js
k6 run load-tests/scenarios/social.js
k6 run load-tests/scenarios/search.js
k6 run load-tests/scenarios/hls-range.js
```

The unique-`clientViewId` qualify scenario writes durable `video_view_idempotency` rows. Keep VUs/duration modest on a shared local database.

Optional environment:

| Variable | Default | Purpose |
| --- | --- | --- |
| `BASE_URL` | `http://127.0.0.1:8080` | API origin |
| `VUS` | scenario default | concurrent virtual users |
| `DURATION` | 20–45s | modest local duration |
| `VIDEO_ID` | first recent video | pin a known video |

Social uses one dedicated `load12_*` user per run and like/unlike only. It does not flood comments.

## Async recovery (manual)

Media worker down:

1. Stop `media-worker`.
2. Complete a small upload. Confirm `200` and `processingStatus=PENDING`.
3. `curl.exe http://127.0.0.1:8080/actuator/metrics/kiki.outbox.pending`
4. Start the worker. Confirm PENDING → PROCESSING → READY without re-upload.
5. Confirm pending gauges return toward 0.

Search outbox:

1. Stop Elasticsearch.
2. Create a video. Search returns `503` quickly. Other APIs still work.
3. Confirm `search` outbox pending/retry metrics.
4. Start Elasticsearch. Pending rows retry and the document appears.

## Metrics while testing

```powershell
curl.exe http://127.0.0.1:8080/actuator/health
curl.exe http://127.0.0.1:8080/actuator/prometheus
curl.exe http://127.0.0.1:8081/actuator/prometheus
```
