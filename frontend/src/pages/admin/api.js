import router from './router'
import utils from '@/utils/utils'
import { getHttpClient } from '@/api/httpClient'
import { notify } from '@/utils/notifications'

const httpClient = getHttpClient()

export default {
  // 登录
  login(username, password) {
    return ajax('login', 'post', {
      data: {
        username,
        password
      }
    })
  },
  csrf() {
    return ajax('csrf', 'get')
  },
  logout() {
    return ajax('logout', 'get')
  },
  getProfile() {
    return ajax('profile', 'get')
  },
  // 获取公告列表
  getAnnouncementList(offset, limit) {
    return ajax('admin/announcements', 'get', {
      params: {
        paging: true,
        offset,
        limit
      }
    })
  },
  // 删除公告
  deleteAnnouncement(id) {
    return ajax('admin/announcements', 'delete', {
      params: {
        id
      }
    })
  },
  // 修改公告
  updateAnnouncement(data) {
    return ajax('admin/announcements', 'put', {
      data
    })
  },
  // 添加公告
  createAnnouncement(data) {
    return ajax('admin/announcements', 'post', {
      data
    })
  },
  // 获取用户列表
  getUserList(offset, limit, keyword) {
    let params = { paging: true, offset, limit }
    if (keyword) {
      params.keyword = keyword
    }
    return ajax('admin/users', 'get', {
      params: params
    })
  },
  // 获取单个用户信息
  getUser(id) {
    return ajax('admin/users', 'get', {
      params: {
        id
      }
    })
  },
  // 编辑用户
  editUser(data) {
    return ajax('admin/users', 'put', {
      data
    })
  },
  deleteUsers(id) {
    return ajax('admin/users', 'delete', {
      params: {
        id
      }
    })
  },
  importUsers(users) {
    return ajax('admin/users', 'post', {
      data: {
        users
      }
    })
  },
  generateUser(data) {
    return ajax('admin/generate-user', 'post', {
      data
    })
  },
  getLanguages() {
    return ajax('languages', 'get')
  },
  getSMTPConfig() {
    return ajax('admin/smtp', 'get')
  },
  createSMTPConfig(data) {
    return ajax('admin/smtp', 'post', {
      data
    })
  },
  editSMTPConfig(data) {
    return ajax('admin/smtp', 'put', {
      data
    })
  },
  testSMTPConfig(email) {
    return ajax('admin/smtp-test', 'post', {
      data: {
        email
      }
    })
  },
  getWebsiteConfig() {
    return ajax('admin/website', 'get')
  },
  editWebsiteConfig(data) {
    return ajax('admin/website', 'post', {
      data
    })
  },
  getAiProviderConfig() {
    return ajax('admin/super/ai-config', 'get')
  },
  updateAiProviderConfig(data) {
    return ajax('admin/super/ai-config', 'put', { data })
  },
  getEnvSnapshot() {
    return ajax('admin/super/env-snapshot', 'get')
  },
  getSystemPathsConfig() {
    return ajax('admin/super/system-paths', 'get')
  },
  updateSystemPathsConfig(data) {
    return ajax('admin/super/system-paths', 'put', { data })
  },
  getInfraSecrets() {
    return ajax('admin/super/infra-secrets', 'get')
  },
  updateInfraSecrets(data) {
    return ajax('admin/super/infra-secrets', 'put', { data })
  },
  getObservabilityConfig() {
    return ajax('admin/super/observability-config', 'get')
  },
  getAgentsOverview(range = '7d') {
    return ajax('admin/ai/agents/overview', 'get', {
      params: { range }
    })
  },
  getAiTraceTimeline(traceId) {
    return ajax(`admin/ai/traces/${encodeURIComponent(traceId)}/timeline`, 'get')
  },
  getEvaluationsDashboard(range = '7d') {
    return ajax('admin/ai/evaluations/dashboard', 'get', {
      params: { range }
    })
  },
  getBehaviorAnalytics(range = '7d') {
    return ajax('admin/ai/behavior-analytics', 'get', {
      params: { range }
    })
  },
  getAiInfraOverview() {
    return ajax('admin/ai/infra/overview', 'get')
  },
  getPromptVariants(agentKey) {
    return ajax('admin/ai/prompt-variants', 'get', {
      params: { agent_key: agentKey }
    })
  },
  getMasteryHeatmap(classroomId, languagePackId) {
    return ajax('admin/insight/mastery-heatmap', 'get', {
      params: { classroom_id: classroomId, language_pack_id: languagePackId }
    })
  },
  getErrorRanking(classroomId, days = 30) {
    return ajax('admin/insight/error-ranking', 'get', {
      params: { classroom_id: classroomId, days }
    })
  },
  getInterventionEffect(days = 30) {
    return ajax('admin/insight/intervention-effect', 'get', {
      params: { days }
    })
  },
  getClassrooms() {
    return ajax('admin/insight/classrooms', 'get')
  },
  getNfkTrainingReadiness(languagePackId) {
    return ajax('admin/nfk/training-data/readiness', 'get', {
      params: { language_pack_id: languagePackId }
    })
  },
  nfkTrainingDataDownloadUrl(languagePackId) {
    return `/api/admin/nfk/training-data/export?language_pack_id=${encodeURIComponent(languagePackId)}`
  },
  getJudgeServer() {
    return ajax('admin/judge-server', 'get')
  },
  deleteJudgeServer(hostname) {
    return ajax('admin/judge-server', 'delete', {
      params: {
        hostname: hostname
      }
    })
  },
  updateJudgeServer(data) {
    return ajax('admin/judge-server', 'put', {
      data
    })
  },
  getInvalidTestCaseList() {
    return ajax('admin/prune-test-case', 'get')
  },
  pruneTestCase(id) {
    return ajax('admin/prune-test-case', 'delete', {
      params: {
        id
      }
    })
  },
  getProblemTagList(params) {
    return ajax('problems/tags', 'get', {
      params
    })
  },
  createProblem(data) {
    return ajax('admin/problems', 'post', {
      data
    })
  },
  editProblem(data) {
    return ajax('admin/problems', 'put', {
      data
    })
  },
  deleteProblem(id) {
    return ajax('admin/problems', 'delete', {
      params: {
        id
      }
    })
  },
  getProblem(id) {
    return ajax('admin/problems', 'get', {
      params: {
        id
      }
    })
  },
  getInlineTestCases(problemId) {
    return ajax('admin/test-cases/inline', 'get', {
      params: { problem_id: problemId }
    })
  },
  uploadInlineTestCases(cases) {
    return ajax('admin/test-cases/inline', 'post', {
      data: { cases }
    })
  },
  getProblemList(params) {
    params = utils.filterEmptyValue(params)
    return ajax('admin/problems', 'get', {
      params
    })
  },
  getReleaseNotes() {
    return ajax('admin/versions', 'get')
  },
  getDashboardInfo() {
    return ajax('admin/dashboard-info', 'get')
  },
  getClassroomChapterOverview() {
    return ajax('admin/ai/classroom-chapters', 'get')
  },
  getSessions() {
    return ajax('sessions', 'get')
  },
  exportProblems(data) {
    return ajax('admin/export-problems', 'get', {
      params: data
    })
  },
  getPublishedLanguagePacks() {
    return ajax('language-packs', 'get')
  },
  getVisibleLanguagePacks() {
    return ajax('language-packs/visible', 'get')
  },

  // ============ AI 变体题审核 ============
  getAIVariantList(params = {}) {
    params = utils.filterEmptyValue(params)
    return ajax('admin/ai/variant-review', 'get', {
      params
    })
  },
  approveAIVariant(problemId, displayId) {
    return ajax(`admin/ai/variant-review/${problemId}/approve`, 'post', {
      data: displayId ? { display_id: displayId } : {}
    })
  },
  rejectAIVariant(problemId) {
    return ajax(`admin/ai/variant-review/${problemId}/reject`, 'post')
  },
  getReviewPackageStats() {
    return ajax('admin/ai/review-packages/stats', 'get')
  },

  // ============ KC Management ============
  getKCList(params = {}) {
    return ajax('admin/ai/kc-list', 'get', { params })
  },
  updateKC(kcId, data) {
    return ajax(`admin/ai/kc/${kcId}`, 'put', { data })
  },
  getKCProblems(kcId) {
    return ajax(`admin/ai/kc/${kcId}/problems`, 'get')
  },

  // ============ McMining (Phase 9) ============
  getMcMiningPending() {
    return ajax('admin/ai/mcmining/pending', 'get')
  },
  mcMiningApprove(misconceptionId) {
    return ajax('admin/ai/mcmining/approve', 'post', { data: { misconception_id: misconceptionId } })
  },
  mcMiningReject(misconceptionId) {
    return ajax('admin/ai/mcmining/reject', 'post', { data: { misconception_id: misconceptionId } })
  },
  mcMiningMerge(misconceptionId, targetId) {
    return ajax('admin/ai/mcmining/merge', 'post', { data: { misconception_id: misconceptionId, target_id: targetId } })
  },
  mcMiningDiscover() {
    return ajax('admin/ai/mcmining/discover', 'post')
  },
  getPreflightStats() {
    return ajax('admin/ai/preflight/stats', 'get')
  },
  preflightDiagnose(detectorName) {
    return ajax('admin/ai/preflight/diagnose', 'post', { data: { detector_name: detectorName } })
  },

  // ============ Language Pack Init ============
  createLanguagePackInitTask(formData) {
    return httpClient.post('admin/language-packs/init-tasks', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    }).then(res => {
      if (res.data.error !== null) {
        notify.error(res.data.data)
        return Promise.reject(res)
      }
      return res
    })
  },
  getLanguagePackInitTask(taskId, options = {}) {
    return ajax(`admin/language-packs/init-tasks/${taskId}`, 'get', options)
  },
  deleteLanguagePackInitTask(taskId) {
    return ajax(`admin/language-packs/init-tasks/${taskId}`, 'delete')
  },
  listLanguagePackInitTasks(options = {}) {
    return ajax('admin/language-packs/init-tasks', 'get', options)
  },
  startLanguagePackPipelineJob(taskId, options = {}) {
    return ajax(`admin/language-packs/init-tasks/${taskId}/pipeline-jobs`, 'post', options)
  },
  getLanguagePackPipelineJob(taskId, jobId, options = {}) {
    return ajax(`admin/language-packs/init-tasks/${taskId}/pipeline-jobs/${jobId}`, 'get', options)
  },
  cancelLanguagePackPipelineJob(taskId, jobId, options = {}) {
    return ajax(`admin/language-packs/init-tasks/${taskId}/pipeline-jobs/${jobId}/cancel`, 'post', options)
  },
  retryLanguagePackPipelineJob(taskId, jobId, options = {}) {
    return ajax(`admin/language-packs/init-tasks/${taskId}/pipeline-jobs/${jobId}/retry`, 'post', options)
  },
  listLanguagePackKcs(taskId, options = {}) {
    return ajax(`admin/language-packs/init-tasks/${taskId}/kcs`, 'get', options)
  },
  listLanguagePackExamples(taskId, options = {}) {
    return ajax(`admin/language-packs/init-tasks/${taskId}/examples`, 'get', options)
  },
  listLanguagePackCandidates(taskId, options = {}) {
    return ajax(`admin/language-packs/init-tasks/${taskId}/candidates`, 'get', options)
  },
  listLanguagePackStageLogs(taskId, options = {}) {
    return ajax(`admin/language-packs/init-tasks/${taskId}/stage-logs`, 'get', options)
  },
  listLanguagePackDocuments(taskId, options = {}) {
    return ajax(`admin/language-packs/init-tasks/${taskId}/documents`, 'get', options)
  },
  reorderLanguagePackDocuments(taskId, documentIds) {
    return ajax(`admin/language-packs/init-tasks/${taskId}/documents/order`, 'patch', {
      data: { document_ids: documentIds }
    })
  },
  exportLanguagePack(taskId) {
    return httpClient.get(`admin/language-packs/init-tasks/${taskId}/export`, {
      responseType: 'blob'
    })
  },
  importLanguagePack(file) {
    const formData = new FormData()
    formData.append('file', file)
    return httpClient.post('admin/language-packs/init-tasks/import', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    }).then(res => {
      if (res.data.error !== null) {
        notify.error(res.data.data)
        return Promise.reject(res)
      }
      return res
    })
  },

  // ============ Beta Features ============
  getBetaFeatures() {
    return ajax('admin/beta-features', 'get')
  },
  toggleBetaFeature(key, enabled) {
    return ajax('admin/beta-features', 'put', {
      data: { key, enabled }
    })
  },

  // ============ Beta Feedback (公测反馈处理) ============
  getBetaFeedbackList(params) {
    return ajax('admin/beta/feedback-reports', 'get', { params, notifyOnSuccess: false })
  },
  getBetaFeedbackDetail(id) {
    return ajax(`admin/beta/feedback-reports/${id}`, 'get', { notifyOnSuccess: false })
  },
  updateBetaFeedbackStatus(id, status) {
    return ajax(`admin/beta/feedback-reports/${id}`, 'patch', { data: { status } })
  },
  getBetaFeedbackScreenshotUrl(reportId, attachmentId) {
    return `/api/admin/beta/feedback-reports/${reportId}/screenshots/${attachmentId}`
  },

  // ============ Usage Stats (公测使用统计) ============
  getUsageStats(range) {
    return ajax('admin/usage-stats', 'get', {
      params: { range: range || '7d' },
      notifyOnSuccess: false
    })
  }
}

/**
 * @param url
 * @param method get|post|put|delete...
 * @param params like queryString. if a url is index?a=1&b=2, params = {a: '1', b: '2'}
 * @param data post data, use for method put|post
 * @returns {Promise}
 */
function ajax(url, method, options) {
  let params = {}
  let data = {}
  let notifyOnSuccess = method !== 'get'
  let notifyOnError = true
  if (options !== undefined) {
    ({ params = {}, data = {}, notifyOnSuccess = method !== 'get', notifyOnError = true } = options)
  }
  return new Promise((resolve, reject) => {
    httpClient({
      url,
      method,
      params,
      data
    }).then(res => {
      if (res.data.error !== null) {
        if (res.data.error === 'permission-denied' || (res.data.data && String(res.data.data).includes('Permission denied'))) {
          reject(res)
          return
        }
        if (notifyOnError) {
          notify.error(res.data.data)
        }
        reject(res)
        if (res.data.data && String(res.data.data).startsWith('Please login')) {
          router.push({ name: 'login' })
        }
      } else {
        resolve(res)
        if (notifyOnSuccess) {
          notify.success('Succeeded')
        }
      }
    }, err => {
      // API请求异常，一般为Server error 或 network error
      reject(err)
      var msg = (err.response && err.response.data && err.response.data.data) || err.message || 'Network Error'
      if (notifyOnError) {
        notify.error(msg)
      }
    })
  })
}
