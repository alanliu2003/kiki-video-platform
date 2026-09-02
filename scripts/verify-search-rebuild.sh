#!/usr/bin/env bash
# Compare PostgreSQL video rows with the Elasticsearch alias count.
# Does not delete the production alias or kiki-videos-v1.
set -euo pipefail

repo_root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$repo_root"

es_url="${ELASTICSEARCH_URL:-http://127.0.0.1:9200}"
alias="${ELASTICSEARCH_VIDEO_INDEX:-kiki-videos}"
user="${POSTGRES_USER:-video}"
db="${POSTGRES_DB:-video_platform}"
compose_service="${COMPOSE_POSTGRES_SERVICE:-postgres}"
disposable="${1:-}"

pg_count="$(docker compose exec -T "$compose_service" psql -U "$user" -d "$db" -Atc "SELECT COUNT(*) FROM videos" | tr -d '[:space:]')"
es_health="$(curl -sf "$es_url/_cluster/health")"
es_count="$(curl -sf "$es_url/${alias}/_count")"

echo "Elasticsearch health payload: $es_health"
echo "PostgreSQL videos: $pg_count"
echo "Elasticsearch $alias count payload: $es_count"
echo "Counts can differ while search_index_outbox is pending. Rebuild from PostgreSQL:"
echo "  cd backend && ./mvnw -pl api -am spring-boot:run -Dspring-boot.run.arguments=--app.search.rebuild=true"

if [[ -n "$disposable" ]]; then
  if [[ "$disposable" == "kiki-videos" || "$disposable" == "kiki-videos-v1" || "$disposable" == "$alias" ]]; then
    echo "Refusing to delete production/alias index '$disposable'."
    exit 1
  fi
  case "$disposable" in
    kiki-videos-m15*|kiki-videos-*-it) ;;
    *)
      echo "Disposable index must look like kiki-videos-m15* or *-it."
      exit 1
      ;;
  esac
  curl -sf -X DELETE "$es_url/$disposable"
  echo "Deleted $disposable. Rebuild is still required to refill a live alias."
fi
