/**
 * 学生端学习者画像接口，对齐 ProfileController 的个人画像路径。
 */

import { ajax } from './shared'

export default {
  getMyProfile() {
    return ajax('ai/tutor/profile/me', 'get')
  },
  updateProfilePreferences(personalizationEnabled) {
    return ajax('ai/tutor/profile/me/preferences', 'patch', {
      data: { personalization_enabled: personalizationEnabled }
    })
  },
  refreshProfileSummary() {
    return ajax('ai/tutor/profile/me/refresh', 'post')
  },
  overrideProfileSummary(summaryText) {
    return ajax('ai/tutor/profile/me/summary/override', 'post', {
      data: { summary_text: summaryText }
    })
  }
}
