/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource (relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('L99 Phase E — Customization contract', () => {
  test('S23: WorldSettingPanel.vue exists with name + narrative + theme picker', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/WorldSettingPanel.vue')
    expect(src).toContain("name: 'WorldSettingPanel'")
    expect(src).toContain('worldName')
    expect(src).toContain('worldNarrative')
    expect(src).toContain('selectedTheme')
    expect(src).toContain('getWorldSetting')
    expect(src).toContain('updateWorldSetting')
  })

  test('S23: WorldSettingPanel has 6 theme options with warm names', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/WorldSettingPanel.vue')
    expect(src).toContain("label: '学院蓝'")
    expect(src).toContain("label: '森林绿'")
    expect(src).toContain("label: '日落橙'")
    expect(src).toContain("label: '星空紫'")
    expect(src).toContain("label: '海洋青'")
    expect(src).toContain("label: '樱花粉'")
  })

  test('S23: WorldSettingPanel has warm, creative UI copy', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/WorldSettingPanel.vue')
    expect(src).toContain('定义你的学习世界')
    expect(src).toContain('给你的学习旅程起个名字')
    expect(src).toContain('代码花园')
  })

  test('twin API has all Phase E methods', () => {
    const src = readSource('../../src/pages/oj/api/twin.js')
    expect(src).toContain('getWorldSetting')
    expect(src).toContain('updateWorldSetting')
    expect(src).toContain('getAnnualReport')
    expect(src).toContain('generateAnnualReport')
    expect(src).toContain('generateShareCard')
  })

  test('WorldSettingPanel uses l99-tokens', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/WorldSettingPanel.vue')
    expect(src).toContain("@import '~@/styles/l99-tokens.less'")
  })

  test('WorldSettingPanel theme picker has active state styling', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/WorldSettingPanel.vue')
    expect(src).toContain('ws-theme-btn')
    expect(src).toContain('is-active')
    expect(src).toContain('ws-theme-swatch')
  })

  test('Total twin API method count is comprehensive', () => {
    const src = readSource('../../src/pages/oj/api/twin.js')
    const methodCount = (src.match(/\w+\s*\(/g) || []).length
    expect(methodCount).toBeGreaterThanOrEqual(30)
  })
})
