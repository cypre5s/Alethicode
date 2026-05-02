/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource (relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('problem frustration card contract', () => {
  test('moderate frustration should not push encouragement chat card', () => {
    const source = readSource('../../src/composables/problem/useFrustration.js')
    expect(source).toContain("sendFrustrationAlert('warning')")
    expect(source).not.toContain("encouragement: '别着急，试试换个思路或者重新审题看看。'")
  })

  test('unified agent panel should hide encouragement cards in timeline', () => {
    const source = readSource('../../src/pages/oj/views/problem/UnifiedAgentPanel.vue')
    expect(source).toContain(".filter(msg => msg && msg.type !== 'encouragement')")
  })
})
