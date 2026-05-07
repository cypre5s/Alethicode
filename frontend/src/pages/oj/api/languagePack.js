/**
 * 语言包接口集中维护课件问答、文档预览、课程结构和学习路径数据。
 */

import { ajax } from './shared'

export default {
  getLanguagePackList() {
    return ajax('language-packs', 'get')
  },
  getVisibleLanguagePackList() {
    return ajax('language-packs/visible', 'get')
  },
  getLanguagePackDetail(id) {
    return ajax(`language-packs/${id}`, 'get')
  },
  getLanguagePackDocuments(id) {
    return ajax(`language-packs/${id}/documents`, 'get')
  },
  getLanguagePackChapters(id) {
    return ajax(`language-packs/${id}/chapters`, 'get')
  },
  getLanguagePackPagePreview(languagePackId, documentId, pageNo) {
    return ajax(`language-packs/${languagePackId}/documents/${documentId}/pages/${pageNo}`, 'get')
  },
  getLanguagePackQaPacks() {
    return ajax('language-pack-qa/packs', 'get')
  },
  createLanguagePackQaSession(data) {
    return ajax('language-pack-qa/sessions', 'post', { data })
  },
  getLanguagePackQaSessions(params) {
    return ajax('language-pack-qa/sessions', 'get', { params })
  },
  deleteLanguagePackQaSession(sessionId) {
    return ajax(`language-pack-qa/sessions/${sessionId}`, 'delete')
  },
  toggleLanguagePackQaSessionStarred(sessionId) {
    return ajax(`language-pack-qa/sessions/${sessionId}/starred`, 'patch')
  },
  getLanguagePackQaMessages(sessionId) {
    return ajax(`language-pack-qa/sessions/${sessionId}/messages`, 'get')
  },
  getLanguagePackQaSessionUsage(sessionId, options = {}) {
    return ajax(`language-pack-qa/sessions/${sessionId}/usage`, 'get', options)
  },
  compactLanguagePackQaSession(sessionId) {
    return ajax(`language-pack-qa/sessions/${sessionId}/compact`, 'post')
  },
  forkLanguagePackQaSession(sessionId, data) {
    return ajax(`language-pack-qa/sessions/${sessionId}/fork`, 'post', { data })
  },
  sendLanguagePackQaMessage(sessionId, data, options = {}) {
    const params = options.async ? { async: true } : {}
    return ajax(`language-pack-qa/sessions/${sessionId}/messages`, 'post', { data, params })
  },
  submitLanguagePackQaFeedback(messageId, data) {
    return ajax(`language-pack-qa/messages/${messageId}/feedback`, 'post', { data })
  },
  getLanguagePackQaCitationPage(languagePackId, documentId, pageNo) {
    return ajax(`language-pack-qa/packs/${languagePackId}/documents/${documentId}/pages/${pageNo}`, 'get')
  },
  getLanguagePackQaPreviewUrl(languagePackId, documentId, pageNo = null) {
    const base = `/api/language-pack-qa/packs/${languagePackId}/documents/${documentId}/preview`
    return pageNo ? `${base}#page=${pageNo}` : base
  },
  createLanguagePackQaVideoJob(messageId) {
    return ajax(`language-pack-qa/messages/${messageId}/video-jobs`, 'post')
  },
  getLanguagePackQaVideoJob(jobId) {
    return ajax(`language-pack-qa/video-jobs/${jobId}`, 'get')
  },
  getCourseStructure(languagePackId) {
    return ajax(`language-pack/${languagePackId}/course-structure`, 'get')
  },
  getKcGraph(languagePackId) {
    return ajax(`language-pack/${languagePackId}/kc-graph`, 'get')
  },
  getCourseProgress(languagePackId) {
    return ajax(`course-progress/${languagePackId}`, 'get')
  },
  getLearningPath(languagePackId) {
    return ajax(`learning-path`, 'get', { params: { language_pack_id: languagePackId } })
  },
  getNextProblemRecommendation(languagePackId) {
    return ajax(`recommend/next-problem`, 'get', { params: { language_pack_id: languagePackId } })
  }
}
