/**
 * Career Bridging API — 对应 CareerController (/api/career/*)
 */

import { ajax } from './shared'

export default {
  getCareerProfile () {
    return ajax('career/profile', 'get')
  },
  updateCareerProfile (majorCode, careerIntent, autoGenerate = true) {
    return ajax('career/profile', 'post', {
      data: { major_code: majorCode, career_intent: careerIntent, auto_generate: autoGenerate }
    })
  },
  getCareerMajors () {
    return ajax('career/majors', 'get')
  },
  generateCareerReport (milestoneId) {
    return ajax(`career/milestones/${milestoneId}/generate`, 'post')
  },
  getCareerReports (limit = 5) {
    return ajax('career/reports', 'get', { params: { limit } })
  },
  getCodingLensVariant (problemId, majorCode) {
    return ajax(`coding-lens/problems/${problemId}`, 'get', {
      params: { major: majorCode }
    })
  },
  lockCodingLensVariant (variantId) {
    return ajax(`coding-lens/variants/${variantId}/lock`, 'post')
  }
}
