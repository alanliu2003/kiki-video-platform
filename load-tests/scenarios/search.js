import http from 'k6/http';
import { check, sleep } from 'k6';
import { apiUrl, defaultThresholds } from '../lib/config.js';

const QUERIES = ['trailer', 'demo', 'video', 'upload', 'kiki'];

export const options = {
  vus: Number(__ENV.VUS || 8),
  duration: __ENV.DURATION || '30s',
  thresholds: {
    http_req_failed: ['rate<0.2'],
    http_req_duration: ['p(95)<1500'],
  },
};

export default function () {
  const q = QUERIES[Math.floor(Math.random() * QUERIES.length)];
  const res = http.get(apiUrl(`/api/search/videos?q=${q}&page=0&size=10`));
  check(res, {
    'search controlled': (r) => r.status === 200 || r.status === 503,
    '503 is fast enough': (r) => r.status !== 503 || r.timings.duration < 1000,
  });

  const recent = http.get(apiUrl('/api/videos/recent?page=0&size=5'));
  check(recent, { 'unrelated recent still works': (r) => r.status === 200 });
  sleep(0.15);
}
