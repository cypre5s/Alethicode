/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource (relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('Beta feedback button contract', () => {
  test('BetaFeedbackButton hides when not authenticated and ignores auth/PDF pages', () => {
    const source = readSource('../../src/pages/oj/components/BetaFeedbackButton.vue')
    expect(source).toContain('isAuthenticated')
    expect(source).toContain('isAuthPage')
    expect(source).toContain('isFullscreenPage')
    expect(source).toContain('mapGetters')
    expect(source).toMatch(/v-if="isAuthenticated\s*&&\s*!isAuthPage\s*&&\s*!isFullscreenPage"/)
  })

  test('BetaFeedbackButton uses position:fixed bottom-right and has aria-label', () => {
    const source = readSource('../../src/pages/oj/components/BetaFeedbackButton.vue')
    expect(source).toContain('position: fixed')
    expect(source).toContain('right: 24px')
    expect(source).toContain('bottom: 24px')
    expect(source).toContain('aria-label')
  })

  test('BetaFeedbackButton records feature_click on mounted and feedback_opened on click', () => {
    const source = readSource('../../src/pages/oj/components/BetaFeedbackButton.vue')
    expect(source).toContain("recordEvent('feature_click', { name: 'feedback_button_view' })")
    expect(source).toContain("recordEvent('feedback_opened'")
  })

  test('BetaFeedbackDialog has 7 type options and 4 severity options matching backend whitelist', () => {
    const source = readSource('../../src/pages/oj/components/BetaFeedbackDialog.vue')
    const TYPES = [
      'cant_open', 'button_dead', 'page_confusing',
      'wrong_problem_or_answer', 'ai_unclear', 'submit_wrong', 'other'
    ]
    for (const t of TYPES) expect(source).toContain(`'${t}'`)
    const SEVS = ['blocker', 'high', 'medium', 'low']
    for (const s of SEVS) expect(source).toContain(`'${s}'`)
  })

  test('BetaFeedbackDialog enforces 5MB / png+jpeg+webp / 3-screenshot caps and has success view', () => {
    const source = readSource('../../src/pages/oj/components/BetaFeedbackDialog.vue')
    expect(source).toContain('MAX_SCREENSHOTS = 3')
    expect(source).toContain('5 * 1024 * 1024')
    expect(source).toContain("'image/png'")
    expect(source).toContain("'image/jpeg'")
    expect(source).toContain("'image/webp'")
    expect(source).toContain("step === 'success'")
    expect(source).toContain('反馈已提交')
  })

  test('BetaFeedbackDialog opens wjx URL in new tab with source/report_id query', () => {
    const source = readSource('../../src/pages/oj/components/BetaFeedbackDialog.vue')
    expect(source).toContain('window.open')
    expect(source).toContain("'source'")
    expect(source).toContain("'alethicode'")
    expect(source).toContain("'report_id'")
    expect(source).toContain('lastReportId')
  })

  test('BetaFeedbackDialog collects browser meta, route, recent telemetry events into payload', () => {
    const source = readSource('../../src/pages/oj/components/BetaFeedbackDialog.vue')
    expect(source).toContain('collectBrowserMeta')
    expect(source).toContain('userAgent')
    expect(source).toContain('nav.connection')
    expect(source).toContain('getRecentEvents(20)')
    expect(source).toContain('privacyNoticeVersion')
  })
})

describe('Beta feedback button mounted in App.vue (only when authenticated)', () => {
  test('App.vue mounts BetaFeedbackButton and BetaPrivacyNotice', () => {
    const source = readSource('../../src/pages/oj/App.vue')
    expect(source).toContain('<BetaFeedbackButton')
    expect(source).toContain('<BetaPrivacyNotice')
    expect(source).toContain("import BetaFeedbackButton from '@oj/components/BetaFeedbackButton.vue'")
    expect(source).toContain("import BetaPrivacyNotice from '@oj/components/BetaPrivacyNotice.vue'")
  })
})

describe('Beta feedback API client contract', () => {
  test('api/beta.js exports the three required methods with correct paths', () => {
    const source = readSource('../../src/pages/oj/api/beta.js')
    expect(source).toContain('createBetaFeedback')
    expect(source).toContain('reportBetaTelemetryBatch')
    expect(source).toContain('reportBetaWebVital')
    expect(source).toContain("'beta/feedback-reports'")
    expect(source).toContain("'beta/telemetry/events'")
    expect(source).toContain("'beta/telemetry/web-vitals'")
  })

  test('api.js merges beta module into default export', () => {
    const source = readSource('../../src/pages/oj/api.js')
    expect(source).toContain("import beta from './api/beta'")
    expect(source).toMatch(/\.\.\.beta/)
  })
})
