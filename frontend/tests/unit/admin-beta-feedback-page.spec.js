/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource (relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('Admin beta feedback page contract', () => {
  test('BetaFeedback.vue exists with required filter / table columns / detail drawer', () => {
    const source = readSource('../../src/pages/admin/views/general/BetaFeedback.vue')
    // Vue 3 模板同时支持 kebab 和 PascalCase；项目统一使用 PascalCase。
    expect(source).toMatch(/<ElSelect|<el-select/)
    expect(source).toMatch(/<ElTable|<el-table/)
    expect(source).toMatch(/<ElDrawer|<el-drawer/)
    expect(source).toContain('filters.status')
    expect(source).toContain('filters.severity')
    expect(source).toContain('filters.type')
    expect(source).toContain('attachment_count')
    expect(source).toContain('mail_status')
  })

  test('Status options match backend whitelist (pending → wontfix)', () => {
    const source = readSource('../../src/pages/admin/views/general/BetaFeedback.vue')
    for (const status of ['pending', 'triaging', 'fixing', 'resolved', 'wontfix']) {
      expect(source).toContain(`'${status}'`)
    }
  })

  test('Status select PATCHes via api.updateBetaFeedbackStatus and rolls back on failure', () => {
    const source = readSource('../../src/pages/admin/views/general/BetaFeedback.vue')
    expect(source).toContain('updateBetaFeedbackStatus')
    expect(source).toContain('row.status = oldStatus')
  })

  test('screenshot URL goes to admin endpoint', () => {
    const source = readSource('../../src/pages/admin/views/general/BetaFeedback.vue')
    expect(source).toContain('getBetaFeedbackScreenshotUrl')
  })

  test('table cellStyle uses object binding, not a raw style string', () => {
    const source = readSource('../../src/pages/admin/views/general/BetaFeedback.vue')
    expect(source).toContain(':cell-style')
    expect(source).not.toContain('cell-style="vertical-align: middle"')
  })

  test('renders feedback summary metrics above the table', () => {
    const source = readSource('../../src/pages/admin/views/general/BetaFeedback.vue')
    expect(source).toContain('feedback-summary-grid')
    expect(source).toContain('summaryTotal')
    expect(source).toContain('currentPagePendingCount')
    expect(source).toContain('currentPageHighPriorityCount')
    expect(source).toContain('currentPageScreenshotCount')
  })
})

describe('Admin api.js contract', () => {
  test('api.js exposes the four beta feedback methods at correct paths', () => {
    const source = readSource('../../src/pages/admin/api.js')
    expect(source).toContain('getBetaFeedbackList')
    expect(source).toContain('getBetaFeedbackDetail')
    expect(source).toContain('updateBetaFeedbackStatus')
    expect(source).toContain('getBetaFeedbackScreenshotUrl')
    expect(source).toContain("'admin/beta/feedback-reports'")
    expect(source).toContain("`admin/beta/feedback-reports/${id}`")
    expect(source).toContain('/api/admin/beta/feedback-reports/')
  })
})

describe('Admin router + menu wiring', () => {
  test('router.js registers /beta-feedback route and denies Teacher access', () => {
    const source = readSource('../../src/pages/admin/router.js')
    expect(source).toContain("path: '/beta-feedback'")
    expect(source).toContain("name: 'beta-feedback'")
    expect(source).toContain("'beta-feedback'")
  })

  test('SideMenu.vue has the 公测反馈 menu item under system admin', () => {
    const source = readSource('../../src/pages/admin/components/SideMenu.vue')
    expect(source).toContain('/beta-feedback')
    expect(source).toContain('公测反馈')
  })

  test('admin views/index.js exports BetaFeedback', () => {
    const source = readSource('../../src/pages/admin/views/index.js')
    expect(source).toContain("import BetaFeedback from './general/BetaFeedback.vue'")
    expect(source).toContain('BetaFeedback')
  })
})

describe('Beta privacy notice contract', () => {
  test('BetaPrivacyNotice.vue uses localStorage betaPrivacyVersion key and reads server version', () => {
    const source = readSource('../../src/pages/oj/components/BetaPrivacyNotice.vue')
    expect(source).toContain("'betaPrivacyVersion'")
    expect(source).toContain('beta_privacy_version')
    expect(source).toContain('localStorage')
  })

  test('BetaPrivacyNotice has accept/decline buttons and shows on version mismatch', () => {
    const source = readSource('../../src/pages/oj/components/BetaPrivacyNotice.vue')
    expect(source).toContain('同意并继续使用')
    expect(source).toContain('不同意并退出')
    expect(source).toContain('stored !== this.serverVersion')
  })
})
