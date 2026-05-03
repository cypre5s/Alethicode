/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource (relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('L99 Sprint 06 — twin dashboard integration contract', () => {
  test('TwinDashboardPage.vue exists and integrates 5 components', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/TwinDashboardPage.vue')
    expect(src).toContain("name: 'TwinDashboardPage'")
    expect(src).toContain('TwinHero')
    expect(src).toContain('LearningTimeline')
    expect(src).toContain('LearningHealthCard')
    expect(src).toContain('KcGalaxyView')
    expect(src).toContain('ErrorMuseumView')
  })

  test('TwinDashboardPage has 70/30 layout for timeline/health', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/TwinDashboardPage.vue')
    expect(src).toContain('td-col--timeline')
    expect(src).toContain('td-col--health')
    expect(src).toContain('flex: 7')
    expect(src).toContain('flex: 3')
  })

  test('TwinDashboardPage has back-to-top FAB', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/TwinDashboardPage.vue')
    expect(src).toContain('td-back-to-top')
    expect(src).toContain('scrollToTop')
    expect(src).toContain('showBackToTop')
    expect(src).toContain('scrollY > 600')
  })

  test('TwinHero.vue exists with persona card + daily quote', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/TwinHero.vue')
    expect(src).toContain("name: 'TwinHero'")
    expect(src).toContain('TwinPersonaCard')
    expect(src).toContain('dailyQuote')
    expect(src).toContain('greeting')
  })

  test('routes.js registers /twin route with requiresAuth', () => {
    const src = readSource('../../src/pages/oj/router/routes.js')
    expect(src).toContain("path: '/twin'")
    expect(src).toContain("name: 'twin'")
    expect(src).toContain('TwinDashboardPage')
    expect(src).toContain('requiresAuth: true')
  })

  test('views/index.js exports TwinDashboardPage', () => {
    const src = readSource('../../src/pages/oj/views/index.js')
    expect(src).toContain('TwinDashboardPage')
  })

  test('TwinDashboardPage uses l99-tokens', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/TwinDashboardPage.vue')
    expect(src).toContain("@import '~@/styles/l99-tokens.less'")
  })

  test('TwinDashboardPage has mobile responsive layout', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/TwinDashboardPage.vue')
    expect(src).toContain('@media (max-width: 767px)')
  })

  test('All 7 twin components exist in twin directory', () => {
    const twinDir = path.resolve(__dirname, '../../src/pages/oj/views/user/twin')
    const expected = [
      'LearningTimeline.vue',
      'LearningTimelineEvent.vue',
      'KcGalaxyView.vue',
      'KcDetailDrawer.vue',
      'TwinPersonaCard.vue',
      'ErrorMuseumView.vue',
      'ErrorMuseumExhibit.vue',
      'LearningHealthCard.vue',
      'TwinHero.vue',
      'TwinDashboardPage.vue'
    ]
    for (const file of expected) {
      expect(fs.existsSync(path.join(twinDir, file))).toBe(true)
    }
  })
})
