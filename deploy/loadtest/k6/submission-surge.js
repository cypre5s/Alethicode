// k6 压测脚本 2 - 提交高峰 (WA 密集)
//
// 模拟学生反复提交错误代码触发 ERROR_FEEDBACK 导学；这是 ERROR_FEEDBACK event
// 热路径，最能压出：
//   * pgvector 相似错误检索
//   * LearnerProfileProjector 的多次 SQL
//   * Spring AI embedding + chat 调用延迟
//
// 关注指标：
//   * getSimilarErrors 内部耗时 (对应 InternalAITutorToolServiceImpl)
//   * LLM circuit breaker 状态
//   * 429 Too Many Requests 发生率（说明 tutorWorkflow 限流起作用）

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const runLatency = new Trend('error_feedback_latency', true);
const limitedRate = new Rate('rate_limited_rate');

export const options = {
  scenarios: {
    wa_surge: {
      executor: 'constant-arrival-rate',
      rate: 30,                // 30 WA 请求/秒
      timeUnit: '1s',
      duration: '3m',
      preAllocatedVUs: 50,
      maxVUs: 100,
    },
  },
  thresholds: {
    error_feedback_latency: ['p(95)<3000'],
    rate_limited_rate: ['rate<0.05'],   // 少于 5% 被限流
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const CSRF_TOKEN = __ENV.CSRF_TOKEN || '';
const SESSION_COOKIE = __ENV.SESSION_COOKIE || '';
const SESSION_IDS = (__ENV.SESSION_IDS || '').split(',').filter(Boolean);
const SUBMISSION_IDS = (__ENV.SUBMISSION_IDS || '').split(',').filter(Boolean);

function headers() {
  return {
    'Content-Type': 'application/json',
    'X-CSRFToken': CSRF_TOKEN,
    Cookie: `sessionid=${SESSION_COOKIE}`,
  };
}

export default function () {
  if (!SESSION_IDS.length || !SUBMISSION_IDS.length) {
    return; // 需要预先种好 session + submission
  }
  const sessionId = SESSION_IDS[Math.floor(Math.random() * SESSION_IDS.length)];
  const submissionId = SUBMISSION_IDS[Math.floor(Math.random() * SUBMISSION_IDS.length)];

  const start = Date.now();
  const res = http.post(
    `${BASE_URL}/api/ai/tutor-workflow-sessions/${sessionId}/runs`,
    JSON.stringify({
      event: 'ERROR_FEEDBACK',
      event_data: { language: 'Python3', submission_id: submissionId },
    }),
    { headers: headers() },
  );
  runLatency.add(Date.now() - start);
  limitedRate.add(res.status === 429);
  check(res, {
    'createRun 202|429': (r) => r.status === 202 || r.status === 429,
  });
  sleep(1);
}
