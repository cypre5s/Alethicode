/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource(relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('classroom analytics layout contract', () => {
  test('analytics view should resize hidden-tab charts and let KC list grow naturally', () => {
    const source = readSource('../../src/pages/oj/views/classroom/ClassroomAnalytics.vue')

    expect(source).toContain('ref="analyticsRoot"')
    expect(source).toContain('initResizeObserver ()')
    expect(source).toContain('this.resizeObserver.observe(this.$refs.analyticsRoot)')
    expect(source).toContain('class="analytics-card kc-top-card"')
    expect(source).toContain('.pulse-row {\n  align-items: flex-start;')
  })
})
