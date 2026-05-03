/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource (relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('L99 Sprint 03 — twin persona card contract', () => {
  test('TwinPersonaCard.vue exists and calls getTwinPersona', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/TwinPersonaCard.vue')
    expect(src).toContain('getTwinPersona')
    expect(src).toContain("name: 'TwinPersonaCard'")
  })

  test('TwinPersonaCard has loading / empty / disabled / normal states', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/TwinPersonaCard.vue')
    expect(src).toContain('v-if="loading"')
    expect(src).toContain('v-else-if="disabled"')
    expect(src).toContain('v-else-if="!summaryText"')
    expect(src).toContain('孪生还在了解你，再做几道题吧')
  })

  test('TwinPersonaCard supports edit mode with textarea', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/TwinPersonaCard.vue')
    expect(src).toContain('v-if="!editing"')
    expect(src).toContain('v-else')
    expect(src).toContain('tp-textarea')
    expect(src).toContain('maxlength="500"')
    expect(src).toContain('overrideTwinPersona')
  })

  test('TwinPersonaCard has accurate / inaccurate / edit buttons', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/TwinPersonaCard.vue')
    expect(src).toContain('精确')
    expect(src).toContain('不精确')
    expect(src).toContain('编辑')
    expect(src).toContain('暂时关闭个性化')
  })

  test('TwinPersonaCard calls feedbackTwinPersona API', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/TwinPersonaCard.vue')
    expect(src).toContain('feedbackTwinPersona')
    expect(src).toContain('is_accurate')
  })

  test('twin API has all 4 persona methods', () => {
    const src = readSource('../../src/pages/oj/api/twin.js')
    expect(src).toContain('getTwinPersona')
    expect(src).toContain('overrideTwinPersona')
    expect(src).toContain('refreshTwinPersona')
    expect(src).toContain('feedbackTwinPersona')
  })

  test('TwinPersonaCard uses l99-tokens', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/TwinPersonaCard.vue')
    expect(src).toContain("@import '~@/styles/l99-tokens.less'")
  })

  test('TwinPersonaCard has a11y region role', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/TwinPersonaCard.vue')
    expect(src).toContain('role="region"')
    expect(src).toContain('aria-label="孪生人格摘要"')
  })
})
