/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource (relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('L99 Phase B (S09-S12) — twin negotiable & editable contract', () => {
  test('S09: TwinEditMasteryPanel.vue exists and calls getMasteryOverrides', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/TwinEditMasteryPanel.vue')
    expect(src).toContain("name: 'TwinEditMasteryPanel'")
    expect(src).toContain('getMasteryOverrides')
    expect(src).toContain('te-override-row')
  })

  test('S12: TwinWeeklyReflection.vue exists with stats and reflection textarea', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/TwinWeeklyReflection.vue')
    expect(src).toContain("name: 'TwinWeeklyReflection'")
    expect(src).toContain('getTwinWeekly')
    expect(src).toContain('submitSundayReflection')
    expect(src).toContain('tw-reflection__textarea')
    expect(src).toContain('冥想已保存')
  })

  test('twin API has all Phase B methods', () => {
    const src = readSource('../../src/pages/oj/api/twin.js')
    expect(src).toContain('overrideMastery')
    expect(src).toContain('getMasteryOverrides')
    expect(src).toContain('getCodeReplayEvents')
    expect(src).toContain('getWhatIfBranch')
    expect(src).toContain('getTwinWeekly')
    expect(src).toContain('submitSundayReflection')
  })

  test('All Phase B twin components exist', () => {
    const twinDir = path.resolve(__dirname, '../../src/pages/oj/views/user/twin')
    const expected = [
      'TwinChatPanel.vue',
      'MetacognitiveMapView.vue',
      'TwinEditMasteryPanel.vue',
      'TwinWeeklyReflection.vue'
    ]
    for (const file of expected) {
      expect(fs.existsSync(path.join(twinDir, file))).toBe(true)
    }
  })

  test('PredictBeforeCodeCard.vue exists in problem directory', () => {
    const p = path.resolve(__dirname, '../../src/pages/oj/views/problem/PredictBeforeCodeCard.vue')
    expect(fs.existsSync(p)).toBe(true)
  })

  test('Phase B components use l99-tokens', () => {
    const files = [
      '../../src/pages/oj/views/user/twin/TwinEditMasteryPanel.vue',
      '../../src/pages/oj/views/user/twin/TwinWeeklyReflection.vue',
      '../../src/pages/oj/views/user/twin/TwinChatPanel.vue',
      '../../src/pages/oj/views/user/twin/MetacognitiveMapView.vue'
    ]
    for (const f of files) {
      const src = readSource(f)
      expect(src).toContain("@import '~@/styles/l99-tokens.less'")
    }
  })
})
