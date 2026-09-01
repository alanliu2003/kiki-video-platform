import http from 'k6/http';
import { check, sleep } from 'k6';
import { apiUrl, jsonHeaders, defaultThresholds } from '../lib/config.js';

export const options = {
  vus: Number(__ENV.VUS || 8),
  duration: __ENV.DURATION || '30s',
  thresholds: {
    ...defaultThresholds,
    http_req_failed: ['rate<0.1'],
  },
};

function uuid() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = Math.random() * 16 | 0;
    const v = c === 'x' ? r : (r & 0x3 | 0x8);
    return v.toString(16);
  });
}

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
    clientViewId: uuid(),
    watchedMs: 15000,
    durationMs: 40000,
  });
  const res = http.post(apiUrl(`/api/videos/${videoId}/views/qualify`), payload, {
    headers: jsonHeaders(),
  });
  check(res, {
    'qualify 200': (r) => r.status === 200,
    'counted or already counted': (r) => {
      if (r.status !== 200) {
        return false;
      }
      const body = r.json();
      return body.counted === true || body.alreadyCounted === true;
    },
  });
  sleep(0.15);
}
