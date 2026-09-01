import http from 'k6/http';
import { check, sleep } from 'k6';
import { apiUrl, jsonHeaders, defaultThresholds } from '../lib/config.js';

export const options = {
  vus: Number(__ENV.VUS || 8),
  duration: __ENV.DURATION || '30s',
  thresholds: defaultThresholds,
};

const SHARED_CLIENT_VIEW_ID = __ENV.CLIENT_VIEW_ID || '11111111-1111-4111-8111-111111111111';

function firstPublicVideoId() {
  const recent = http.get(apiUrl('/api/videos/recent?page=0&size=10'));
  if (recent.status !== 200) {
    return Number(__ENV.VIDEO_ID || 0) || null;
  }
  const items = recent.json().items || [];
  return items.length ? items[0].id : (Number(__ENV.VIDEO_ID || 0) || null);
}

export default function () {
  const videoId = firstPublicVideoId();
  if (!videoId) {
    sleep(1);
    return;
  }
  const payload = JSON.stringify({
    clientViewId: SHARED_CLIENT_VIEW_ID,
    watchedMs: 15000,
    durationMs: 40000,
  });
  const res = http.post(apiUrl(`/api/videos/${videoId}/views/qualify`), payload, {
    headers: jsonHeaders(),
  });
  check(res, {
    'qualify 200': (r) => r.status === 200,
    'retry does not fail': (r) => r.status === 200 && r.json().alreadyCounted !== undefined,
  });
  sleep(0.1);
}
