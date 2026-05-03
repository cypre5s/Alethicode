/**
 * L99 Open Learner Twin：学习者孪生相关接口。
 */

import { ajax } from './shared'

export default {
  getLearningTimeline(params) {
    return ajax('twin/timeline', 'get', { params })
  },
  getTwinKcGalaxy(params) {
    return ajax('twin/kc-galaxy', 'get', { params })
  },
  getTwinPersona() {
    return ajax('twin/persona', 'get')
  },
  overrideTwinPersona(data) {
    return ajax('twin/persona', 'post', { data })
  },
  refreshTwinPersona() {
    return ajax('twin/persona/refresh', 'post')
  },
  feedbackTwinPersona(data) {
    return ajax('twin/persona/feedback', 'post', { data })
  },
  getMuseumPins() {
    return ajax('twin/museum/pins', 'get')
  },
  pinMuseumMemory(data) {
    return ajax('twin/museum/pins', 'post', { data })
  },
  updateMuseumPin(pinId, data) {
    return ajax(`twin/museum/pins/${pinId}`, 'patch', { data })
  },
  unpinMuseumMemory(pinId) {
    return ajax(`twin/museum/pins/${pinId}`, 'delete')
  },
  getTwinHealth() {
    return ajax('twin/health', 'get')
  },
  submitMetacogPrediction(data) {
    return ajax('twin/metacog/predict', 'post', { data })
  },
  getMetacogMap() {
    return ajax('twin/metacog/map', 'get')
  },
  askTwin(data) {
    return ajax('twin/chat', 'post', { data })
  },
  getTwinQuickQuestions() {
    return ajax('twin/chat/quick-questions', 'get')
  },
  overrideMastery(data) {
    return ajax('twin/edit/mastery-override', 'post', { data })
  },
  getMasteryOverrides() {
    return ajax('twin/edit/mastery-overrides', 'get')
  },
  getCodeReplayEvents(params) {
    return ajax('twin/replay/events', 'get', { params })
  },
  getWhatIfBranch(data) {
    return ajax('twin/what-if', 'post', { data })
  },
  getTwinWeekly() {
    return ajax('twin/weekly', 'get')
  },
  submitSundayReflection(data) {
    return ajax('twin/weekly/reflection', 'post', { data })
  },
  startTeachAiSession(data) {
    return ajax('twin/teach-ai/start', 'post', { data })
  },
  submitTeachAiExplanation(sessionId, data) {
    return ajax(`twin/teach-ai/${sessionId}/explain`, 'post', { data })
  },
  getTeachAiSessions() {
    return ajax('twin/teach-ai/sessions', 'get')
  },
  getKcDecayQueue() {
    return ajax('twin/kc-decay/queue', 'get')
  },
  reviewDecayKc(kcId) {
    return ajax(`twin/kc-decay/${kcId}/review`, 'post')
  },
  startArenaMatch(data) {
    return ajax('twin/arena/start', 'post', { data })
  },
  judgeArenaAi(matchId, data) {
    return ajax(`twin/arena/${matchId}/judge-ai`, 'post', { data })
  },
  getPublicProfile(handle) {
    return ajax(`twin/public/${handle}`, 'get')
  },
  updateTwinPrivacy(data) {
    return ajax('twin/profile/privacy', 'patch', { data })
  },
  generateSemesterReport(data) {
    return ajax('twin/semester-report', 'post', { data })
  },
  downloadSemesterReportPdf(reportId) {
    return ajax(`twin/semester-report/${reportId}/pdf`, 'get')
  },
  getCredentials() {
    return ajax('twin/credential', 'get')
  },
  generateCredential(data) {
    return ajax('twin/credential', 'post', { data })
  },
  exportTwinDump() {
    return ajax('twin/export', 'get')
  }
}
