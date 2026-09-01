import http from 'k6/http';
import { check, sleep } from 'k6';
import { apiUrl, defaultThresholds } from '../lib/config.js';

export const options = {
  vus: Number(__ENV.VUS || 10),
  duration: __ENV.DURATION || '45s',
  thresholds: defaultThresholds,
};

function firstPublicVideoId() {
  const recent = http.get(apiUrl('/api/videos/recent?page=0&size=10'));
  if (recent.status !== 200) {
    return null;
  }
  const body = recent.json();
  const items = body.items || [];
  return items.length ? items[0].id : null;
}

export default function () {
  const recent = http.get(apiUrl('/api/videos/recent?page=0&size=20'));
  check(recent, { 'recent 200': (r) => r.status === 200 });

  const trending = http.get(apiUrl('/api/videos/trending?page=0&size=20'));
  check(trending, { 'trending 200': (r) => r.status === 200 });

  const videoId = firstPublicVideoId();
  if (videoId) {
    const detail = http.get(apiUrl(`/api/videos/${videoId}`));
    check(detail, { 'video detail 200': (r) => r.status === 200 });
  }

  const search = http.get(apiUrl('/api/search/videos?q=trailer&page=0&size=10'));
  check(search, { 'search 200 or 503': (r) => r.status === 200 || r.status === 503 });

  sleep(0.2);
}
