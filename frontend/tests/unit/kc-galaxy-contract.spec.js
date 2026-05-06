/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource (relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('L99 Sprint 02 — KC galaxy contract', () => {
  test('KcGalaxyView.vue exists and uses ECharts graph', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/KcGalaxyView.vue')
    expect(src).toContain("import('echarts/core')")
    expect(src).toContain('GraphChart')
    expect(src).toContain("type: 'graph'")
    expect(src).toContain("layout: 'force'")
  })

  test('KcGalaxyView calls getTwinKcGalaxy API', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/KcGalaxyView.vue')
    expect(src).toContain('getTwinKcGalaxy')
  })

  test('KcGalaxyView handles loading / error / empty states', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/KcGalaxyView.vue')
    expect(src).toContain('v-if="loading"')
    expect(src).toContain('v-else-if="error"')
    expect(src).toContain('v-else-if="nodes.length === 0"')
    expect(src).toContain('还没有知识点数据')
  })

  test('KcGalaxyView maps mastery to 4 color tiers', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/KcGalaxyView.vue')
    expect(src).toContain('> 0.85')
    expect(src).toContain('> 0.6')
    expect(src).toContain('> 0.3')
  })

  test('KcGalaxyView supports 3 edge line styles', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/KcGalaxyView.vue')
    expect(src).toContain("prerequisite: { type: 'solid' }")
    expect(src).toContain("related: { type: 'dashed' }")
    expect(src).toContain("applies_to: { type: 'dotted' }")
  })

  test('KcGalaxyView auto-switches renderer for > 100 nodes', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/KcGalaxyView.vue')
    expect(src).toContain('this.nodes.length > 100')
    expect(src).toContain('CanvasRenderer')
    expect(src).toContain('SVGRenderer')
  })

  test('KcDetailDrawer.vue exists and shows mastery bar', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/KcDetailDrawer.vue')
    expect(src).toContain('kc-drawer__mastery-bar')
    expect(src).toContain('kc-drawer__mastery-fill')
    expect(src).toContain("role=\"complementary\"")
  })

  test('KcDetailDrawer shows related nodes from edges', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/KcDetailDrawer.vue')
    expect(src).toContain('relatedNodes')
    expect(src).toContain('from_kc_id')
    expect(src).toContain('to_kc_id')
  })

  test('twin API has getTwinKcGalaxy method', () => {
    const src = readSource('../../src/pages/oj/api/twin.js')
    expect(src).toContain('getTwinKcGalaxy')
    expect(src).toContain("'twin/kc-galaxy'")
  })

  test('KcDetailDrawer has responsive mobile layout', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/KcDetailDrawer.vue')
    expect(src).toContain('@media (max-width: 767px)')
  })

  test('KcGalaxyView uses l99-tokens', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/KcGalaxyView.vue')
    expect(src).toContain("@import '~@/styles/l99-tokens.less'")
  })

  test('KcGalaxyView has a11y attributes', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/KcGalaxyView.vue')
    expect(src).toContain('role="region"')
    expect(src).toContain('aria-label="KC 知识星系图"')
    expect(src).toContain('role="img"')
  })
})
