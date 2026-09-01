import http from 'k6/http';
import { check, sleep } from 'k6';
import { apiUrl, jsonHeaders, defaultThresholds } from '../lib/config.js';

export const options = {
  vus: Number(__ENV.VUS || 4),
  duration: __ENV.DURATION || '30s',
  thresholds: defaultThresholds,
};

function firstPublicVideoId() {
  const recent = http.get(apiUrl('/api/videos/recent?page=0&size=10'));
  if (recent.status !== 200) {
    return Number(__ENV.VIDEO_ID || 0) || null;
  }
  const items = recent.json().items || [];
  return items.length ? items[0].id : (Number(__ENV.VIDEO_ID || 0) || null);
}

export function setup() {
  const suffix = `${Date.now()}`;
  const username = `load12_${suffix}`;
  const password = 'LoadTestPass123';
  const register = http.post(
    apiUrl('/api/auth/register'),
    JSON.stringify({
      username,
      email: `${username}@example.com`,
      password,
      displayName: username,
    }),
    { headers: jsonHeaders() },
  );
  const login = http.post(
    apiUrl('/api/auth/login'),
    JSON.stringify({ identifier: username, password }),
    { headers: jsonHeaders() },
  );
  const token = login.status === 200 ? login.json().accessToken : null;
  return {
    token,
    videoId: firstPublicVideoId(),
    registered: register.status === 201 || register.status === 200,
  };
}

export default function (data) {
  if (!data.token || !data.videoId) {
    sleep(1);
    return;
  }
  const headers = { headers: jsonHeaders(data.token) };
  const like = http.put(apiUrl(`/api/videos/${data.videoId}/like`), null, headers);
  check(like, { 'like 200': (r) => r.status === 200 });
  const unread = http.get(apiUrl('/api/notifications/unread-count'), headers);
  check(unread, { 'unread 200': (r) => r.status === 200 });
  const unlike = http.del(apiUrl(`/api/videos/${data.videoId}/like`), null, headers);
  check(unlike, { 'unlike 200': (r) => r.status === 200 });
  sleep(0.25);
}
