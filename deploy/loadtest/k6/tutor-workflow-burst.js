// k6 压测脚本 1 - AI 导学会话突发峰值
//
// 模拟单个班级 50 名学生几乎同时点击"开始导学"创建 session + 触发第一次 READING。
// 关注指标：
//   * createSession p95 < 600ms
//   * createRun -> 第一次 runtime_event(TASK_STARTED) 延迟 p95 < 1.5s
//   * 5xx / circuit breaker 打开次数
//
// 运行：
//   k6 run \
//     -e BASE_URL=https://alethicode.example.cn \
//     -e CSRF_TOKEN=<token> \
//     -e SESSION_COOKIE=<cookie> \
//     deploy/loadtest/k6/tutor-workflow-burst.js

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const createSessionLatency = new Trend('create_session_latency', true);
const createRunLatency = new Trend('create_run_latency', true);
const failureRate = new Rate('failure_rate');

export const options = {
  scenarios: {
    classroom_start: {
      executor: 'ramping-arrival-rate',
      startRate: 0,
      timeUnit: '1s',
      preAllocatedVUs: 30,
      maxVUs: 200,
      stages: [
        { target: 5, duration: '10s' },
        { target: 50, duration: '20s' }, // 50 学生同时开始
        { target: 50, duration: '60s' }, // 保持 60s 平顶
        { target: 0, duration: '10s' },
      ],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.02'],
    create_session_latency: ['p(95)<600'],
    create_run_latency: ['p(95)<1500'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const CSRF_TOKEN = __ENV.CSRF_TOKEN || '';
const SESSION_COOKIE = __ENV.SESSION_COOKIE || '';
const PROBLEM_IDS = (__ENV.PROBLEM_IDS || '1001,1002,1003').split(',');

function headers() {
  return {
    'Content-Type': 'application/json',
    'X-CSRFToken': CSRF_TOKEN,
    Cookie: `sessionid=${SESSION_COOKIE}`,
  };
}

export default function () {
  const problemId = Number(PROBLEM_IDS[Math.floor(Math.random() * PROBLEM_IDS.length)]);

  // 1. createSession
  const sessionStart = Date.now();
  const sessionRes = http.post(
    `${BASE_URL}/api/ai/tutor-workflow-sessions`,
    JSON.stringify({ problem_id: problemId, language: 'Python3' }),
    { headers: headers() },
  );
  createSessionLatency.add(Date.now() - sessionStart);
  const sessionOk = check(sessionRes, {
    'createSession 201': (r) => r.status === 201,
  });
  failureRate.add(!sessionOk);
  if (!sessionOk) return;

  const sessionId = sessionRes.json('data.session_id');
  if (!sessionId) {
    failureRate.add(1);
    return;
  }

  // 2. 触发首次 READING run
  const runStart = Date.now();
  const runRes = http.post(
    `${BASE_URL}/api/ai/tutor-workflow-sessions/${sessionId}/runs`,
    JSON.stringify({ event: 'READING', event_data: { language: 'Python3' } }),
    { headers: headers() },
  );
  createRunLatency.add(Date.now() - runStart);
  const runOk = check(runRes, {
    'createRun 202': (r) => r.status === 202,
  });
  failureRate.add(!runOk);

  sleep(0.5);
}
