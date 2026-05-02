/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource(relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('skill radar refresh contract', () => {
  const source = readSource('../../src/pages/oj/components/skillProfile/SkillRadar.vue')

  test('radar chart dom should persist during loading to avoid refresh blank screen', () => {
    expect(source).toContain('v-show="loading" class="radar-skeleton"')
    expect(source).toContain(':class="{ \'radar-chart-hidden\': loading }"')
  })

  test('radar payload should be sanitized before rendering', () => {
    expect(source).toContain('sanitizeRadarPayload ()')
    expect(source).toContain('clamp01 (value)')
  })

  test('loading watcher should rerender chart after skeleton is hidden', () => {
    expect(source).toContain('loading (next)')
    expect(source).toContain('if (!next)')
    expect(source).toContain('this.renderChart()')
  })
})

