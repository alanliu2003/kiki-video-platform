import http from 'k6/http';
import { check, sleep } from 'k6';
import { apiUrl } from '../lib/config.js';

export const options = {
  vus: Number(__ENV.VUS || 4),
  duration: __ENV.DURATION || '20s',
  thresholds: {
    http_req_failed: ['rate<0.2'],
  },
};

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
  const range = http.get(apiUrl(`/api/videos/${videoId}/content`), {
    headers: { Range: 'bytes=0-1023' },
  });
  check(range, {
    'range 206 or 404/409': (r) => [206, 200, 404, 409, 415].includes(r.status),
  });
  sleep(0.3);
}
