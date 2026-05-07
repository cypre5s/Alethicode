/**
 * AI 导学接口集中维护传统导学、LangGraph 工作流、复习、校准和学习画像能力。
 *
 * `getAIGuidance` 与 `getAITaskStatus` 依赖最终 API 对象的 spread 合并语义：
 * 单参查导学任务，双参转到 classroom 模块查 AI 生成题任务。
 */

import { ajax } from './shared'

export default {
  requestAIGuidance(data) {
    return ajax('ai/tutor/inference', 'post', {
      data
    })
  },
  getAIGuidanceResult(taskId) {
    return ajax('ai/tutor/task', 'get', {
      params: { task_id: taskId }
    })
  },
  getAIGuidance(data) {
    return this.requestAIGuidance(data)
  },
  getAITutorTaskStatus(taskId) {
    return this.getAIGuidanceResult(taskId)
  },
  getAITaskStatus(arg1, arg2) {
    if (typeof arg2 === 'undefined') {
      return this.getAITutorTaskStatus(arg1)
    }
    return this.getAIGeneratedTaskStatus(arg1, arg2)
  },
  getAISession(sessionId) {
    return ajax('ai/tutor/session', 'get', {
      params: { session_id: sessionId }
    })
  },

  analyzeAntiPatterns(data) {
    return ajax('analytics/anti-patterns/analyze', 'post', { data })
  },
  requestErrorAttribution(data) {
    return ajax('ai/tutor/error-attribution', 'post', { data })
  },

  reportEvalFeedback(data) {
    return ajax('ai/tutor/eval-feedback', 'post', { data })
  },
  submitEvalFeedback(data) {
    return this.reportEvalFeedback(data)
  },
  submitSafetyFeedback(data) {
    return ajax('ai/tutor/safety-feedback', 'post', { data })
  },

  analyzeFrustration(data) {
    return ajax('ai/frustration/analyze/', 'post', { data })
  },
  recordFrustrationEvent(data) {
    return ajax('ai/frustration/event/', 'post', { data })
  },
  sendFrustrationAlert(data) {
    return ajax('ai/frustration/alert/', 'post', { data })
  },

  tutorWorkflowCreateSession(data) {
    return ajax('ai/tutor-workflow-sessions', 'post', { data })
  },
  tutorWorkflowGetSession(sessionId, options = {}) {
    return ajax(`ai/tutor-workflow-sessions/${sessionId}`, 'get', options)
  },
  tutorWorkflowGetSessionUsage(sessionId, options = {}) {
    return ajax(`ai/tutor-workflow-sessions/${sessionId}/usage`, 'get', options)
  },
  tutorWorkflowDeleteSession(sessionId) {
    return ajax(`ai/tutor-workflow-sessions/${sessionId}`, 'delete')
  },
  tutorWorkflowCreateRun(sessionId, data, { signal } = {}) {
    return ajax(`ai/tutor-workflow-sessions/${sessionId}/runs`, 'post', { data, signal })
  },
  tutorWorkflowGetCheckpoints(sessionId) {
    return ajax(`ai/tutor-workflow-sessions/${sessionId}/checkpoints`, 'get')
  },
  tutorWorkflowRestoreCheckpoint(sessionId, data) {
    return ajax(`ai/tutor-workflow-sessions/${sessionId}/checkpoint-restorations`, 'post', { data })
  },
  tutorWorkflowRespondInterrupt(sessionId, data) {
    return ajax(`ai/tutor-workflow-sessions/${sessionId}/interrupt-responses`, 'post', { data })
  },
  tutorWorkflowCompactSession(sessionId) {
    return ajax(`ai/tutor-workflow-sessions/${sessionId}/compact`, 'post')
  },
  tutorWorkflowForkSession(sessionId, data) {
    return ajax(`ai/tutor-workflow-sessions/${sessionId}/fork`, 'post', { data })
  },

  submitCodeSnapshot(data) {
    return ajax('ai/code-snapshot', 'post', { data })
  },
  submitLearningEventsBatch(events) {
    return ajax('ai/learning-events/batch', 'post', { data: { events } })
  },
  getReviewDue(limit = 10, languagePackId) {
    const p = { limit }
    if (languagePackId) p.language_pack_id = languagePackId
    return ajax('ai/review/due', 'get', { params: p })
  },
  getSupplementPlan(data) {
    return ajax('ai/tutor/supplement-plan', 'post', { data })
  },
  createReviewPackage(data) {
    return ajax('ai/review-packages', 'post', { data })
  },
  createReviewPackages(data) {
    return ajax('ai/review-packages/batches', 'post', { data })
  },
  getReviewPackages() {
    return ajax('ai/review-packages', 'get')
  },
  getReviewPackage(packageId) {
    return ajax(`ai/review-packages/${packageId}`, 'get')
  },
  rateReviewProblem(packageId, problemId, rating) {
    return ajax(`ai/review-packages/${packageId}/problems/${problemId}/rating`, 'post', { data: { rating } })
  },
  rateReviewPackage(packageId, rating) {
    return ajax(`ai/review-packages/${packageId}/reviews`, 'post', { data: { rating } })
  },
  getMyMisconceptions() {
    return ajax('ai/misconceptions/mine', 'get')
  },

  preflightCheck(data) {
    return ajax('ai/preflight/check', 'post', { data, timeout: 5000 })
  },

  calibrationStatus() {
    return ajax('ai/calibration/status', 'get')
  },
  calibrationAnswer(data) {
    return ajax('ai/calibration/answer', 'post', { data })
  },
  calibrationSkip(data) {
    return ajax('ai/calibration/skip', 'post', { data })
  },

  getKnowledgeGraph(userId, languagePackId) {
    return ajax('ai/knowledge-graph', 'get', { params: { user_id: userId, language_pack_id: languagePackId } })
  },
  getKnowledgeGraphSnapshot(userId, beforeDate, languagePackId) {
    return ajax('ai/knowledge-graph/snapshot', 'get', { params: { user_id: userId, before_date: beforeDate, language_pack_id: languagePackId } })
  },
  getKCDetail(kcId, userId, languagePackId) {
    return ajax('ai/knowledge-graph/kc/' + kcId + '/detail', 'get', { params: { user_id: userId, language_pack_id: languagePackId } })
  },

  getLearningTwin(languagePackId, problemId) {
    return ajax('ai/learning-twins/current', 'get', { params: { language_pack_id: languagePackId, problem_id: problemId } })
  },
  getTutorWelcome(problemId) {
    return ajax('ai/tutor/welcome', 'get', { params: { problem_id: problemId } })
  },
  submitStrategyFeedback(strategyType, rating) {
    return ajax('ai/tutor/strategy-feedback', 'post', { data: { strategy_type: strategyType, rating } })
  },

  getSubmissionRiver(problemId, userId) {
    return ajax('ai/submission-river/' + problemId, 'get', { params: { user_id: userId } })
  }
}
