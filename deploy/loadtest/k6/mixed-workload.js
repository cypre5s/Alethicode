// k6 压测脚本 3 - 综合混合负载 (长时稳态)
//
// 同时运行：
//   * 20% 流量：学生创建 session + READING
//   * 50% 流量：学生 IDEATING / CODING / ERROR_FEEDBACK 之间切换
//   * 20% 流量：学生 AC_REVIEW
//   * 10% 流量：WebSocket 订阅 runtime_event
//
// 目标：观察系统在稳态 30 分钟下是否出现内存泄漏、连接泄漏、CircuitBreaker 抖动。

import http from 'k6/http';
import ws from 'k6/ws';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const readingLatency = new Trend('reading_latency', true);
const ideatingLatency = new Trend('ideating_latency', true);
const errorFeedbackLatency = new Trend('error_feedback_latency', true);
const acReviewLatency = new Trend('ac_review_latency', true);
const failureRate = new Rate('failure_rate');

export const options = {
  scenarios: {
    mixed: {
      executor: 'constant-vus',
      vus: 80,
      duration: '30m',
    },
  },
  thresholds: {
    failure_rate: ['rate<0.02'],
    reading_latency: ['p(95)<1500'],
    ideating_latency: ['p(95)<2500'],
    error_feedback_latency: ['p(95)<3500'],
    ac_review_latency: ['p(95)<3500'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const WS_URL = __ENV.WS_URL || 'ws://localhost:8080';
const CSRF_TOKEN = __ENV.CSRF_TOKEN || '';
const SESSION_COOKIE = __ENV.SESSION_COOKIE || '';

function headers() {
  return {
    'Content-Type': 'application/json',
    'X-CSRFToken': CSRF_TOKEN,
    Cookie: `sessionid=${SESSION_COOKIE}`,
  };
}

function createSession(problemId) {
  const res = http.post(
    `${BASE_URL}/api/ai/tutor-workflow-sessions`,
    JSON.stringify({ problem_id: problemId, language: 'Python3' }),
    { headers: headers() },
  );
  return res.status === 201 ? res.json('data.session_id') : null;
}

function triggerEvent(sessionId, event, extra = {}, metric) {
  const start = Date.now();
  const res = http.post(
    `${BASE_URL}/api/ai/tutor-workflow-sessions/${sessionId}/runs`,
    JSON.stringify({
      event,
      event_data: { language: 'Python3', ...extra },
    }),
    { headers: headers() },
  );
  if (metric) metric.add(Date.now() - start);
  failureRate.add(res.status !== 202);
  return res;
}

function subscribeWs(sessionId) {
  const url = `${WS_URL}/ws/tutor-workflow-sessions/${sessionId}`;
  const res = ws.connect(url, { headers: { Cookie: `sessionid=${SESSION_COOKIE}` } }, function (socket) {
    socket.on('open', () => socket.setTimeout(() => socket.close(), 10_000));
  });
  check(res, { 'ws connected 101': (r) => r && r.status === 101 });
}

export default function () {
  const roll = Math.random();
  const problemId = 1000 + Math.floor(Math.random() * 20);

  if (roll < 0.2) {
    const sid = createSession(problemId);
    if (sid) triggerEvent(sid, 'READING', {}, readingLatency);
  } else if (roll < 0.7) {
    const sid = createSession(problemId);
    if (!sid) return;
    triggerEvent(sid, 'READING', {}, readingLatency);
    sleep(1);
    triggerEvent(sid, 'IDEATING', { thought_text: '我打算用两重循环枚举所有可能' }, ideatingLatency);
    sleep(1);
    triggerEvent(
      sid,
      'ERROR_FEEDBACK',
      { submission_id: `stub_${Math.floor(Math.random() * 1_000_000)}` },
      errorFeedbackLatency,
    );
  } else if (roll < 0.9) {
    const sid = createSession(problemId);
    if (!sid) return;
    triggerEvent(
      sid,
      'AC_REVIEW',
      { submission_id: `stub_ac_${Math.floor(Math.random() * 1_000_000)}`, code: 'print(1)' },
      acReviewLatency,
    );
  } else {
    const sid = createSession(problemId);
    if (sid) subscribeWs(sid);
  }
  sleep(Math.random() * 2);
}
