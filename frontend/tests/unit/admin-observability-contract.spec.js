/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource(relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('admin observability dashboard contracts', () => {
  test('quality trend subtitle does not expose workflow-success fallback copy', () => {
    const source = readSource('../../src/pages/admin/views/general/ObservabilityDashboard.vue')
    expect(source).toContain('平均评分趋势')
    expect(source).toContain('近阶段 LLM-as-Judge 教学质量走势')
    expect(source).not.toContain('暂无离线教学评分时，暂用工作流成功率作为质量趋势')
  })
})
