/**
 * Career Bridging API — 对应 CareerController (/api/career/*)
 */

import { ajax } from './shared'

export default {
  getCareerProfile () {
    return ajax('career/profile', 'get')
  },
  updateCareerProfile (majorCode, careerIntent, autoGenerate = true) {
    return ajax('career/profile', 'put', {
      data: { major_code: majorCode, career_intent: careerIntent, auto_generate: autoGenerate }
    })
  },
  getCareerMajors () {
    return ajax('career/majors', 'get')
  },
  generateCareerReport (milestoneId) {
    return ajax(`career/milestones/${milestoneId}/reports`, 'post')
  },
  getCareerReports (limit = 5) {
    return ajax('career/reports', 'get', { params: { limit } })
  },
  getCareerPath (majorCode) {
    return ajax('career/path', 'get', { params: { major: majorCode } })
  },
  getCodingLensVariant (problemId, majorCode) {
    return ajax(`coding-lens/problems/${problemId}`, 'get', {
      params: { major: majorCode }
    })
  },
  lockCodingLensVariant (variantId) {
    return ajax(`coding-lens/variants/${variantId}/lock`, 'post')
  },
  getStudioRecommendations () {
    return ajax('career/studio/recommendations', 'get')
  },
  generateStudioProject (majorCode, kcCodes) {
    return ajax('career/studio/projects', 'post', {
      data: { major_code: majorCode, kc_codes: kcCodes }
    })
  },
  listStudioProjects (limit = 10) {
    return ajax('career/studio/projects', 'get', { params: { limit } })
  },
  getStudioProject (projectId) {
    return ajax(`career/studio/projects/${projectId}`, 'get')
  },
  getCareerPreferences () {
    return ajax('career/preferences', 'get')
  },
  updateCareerPreferences (prefs) {
    return ajax('career/preferences', 'put', { data: prefs })
  }
}
