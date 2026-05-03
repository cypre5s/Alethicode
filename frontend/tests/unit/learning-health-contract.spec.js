/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource (relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('L99 Sprint 05 — learning health dashboard contract', () => {
  test('LearningHealthCard.vue exists and calls getTwinHealth', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/LearningHealthCard.vue')
    expect(src).toContain('getTwinHealth')
    expect(src).toContain("name: 'LearningHealthCard'")
  })

  test('LearningHealthCard has 4 cells: mastery / freq / diff / due', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/LearningHealthCard.vue')
    expect(src).toContain('lh-cell--mastery')
    expect(src).toContain('lh-cell--freq')
    expect(src).toContain('lh-cell--diff')
    expect(src).toContain('lh-cell--due')
  })

  test('LearningHealthCard shows mastery ring with percentage', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/LearningHealthCard.vue')
    expect(src).toContain('lh-mastery-ring')
    expect(src).toContain('lh-mastery-percent')
    expect(src).toContain('ringDash')
  })

  test('LearningHealthCard shows 3 frequency stats', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/LearningHealthCard.vue')
    expect(src).toContain('submits_30d')
    expect(src).toContain('active_days')
    expect(src).toContain('streak_days')
  })

  test('LearningHealthCard has sparkline for difficulty curve', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/LearningHealthCard.vue')
    expect(src).toContain('lh-sparkline')
    expect(src).toContain('sparkPoints')
    expect(src).toContain('polyline')
  })

  test('LearningHealthCard links due reviews to review-package route', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/LearningHealthCard.vue')
    expect(src).toContain("name: 'error-review-package'")
    expect(src).toContain('isOverdue')
    expect(src).toContain('lh-due-tag--overdue')
  })

  test('twin API has getTwinHealth method', () => {
    const src = readSource('../../src/pages/oj/api/twin.js')
    expect(src).toContain('getTwinHealth')
    expect(src).toContain("'twin/health'")
  })

  test('LearningHealthCard uses l99-tokens', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/LearningHealthCard.vue')
    expect(src).toContain("@import '~@/styles/l99-tokens.less'")
  })

  test('LearningHealthCard has responsive mobile layout', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/LearningHealthCard.vue')
    expect(src).toContain('@media (max-width: 767px)')
  })

  test('LearningHealthCard has a11y region role', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/LearningHealthCard.vue')
    expect(src).toContain('role="region"')
    expect(src).toContain('aria-label="学习健康度仪表盘"')
  })
})
