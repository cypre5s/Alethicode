/**
 * Student-side learner profile dashboard API (P1 Persistent Memory layer).
 *
 * Backend: ProfileController, mounted at /api/ai/tutor/profile/*
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
