/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource(relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('unified agent panel bottom anchor contract', () => {
  test('should insert a flex spacer before timeline items so idle whitespace stays above the latest card', () => {
    const source = readSource('../../src/pages/oj/views/problem/UnifiedAgentPanel.vue')

    expect(source).toContain('class="message-stream-spacer"')
    expect(source).toMatch(/\.message-stream-spacer\s*\{[\s\S]*flex:\s*1\s+0\s+auto;/)
  })
})
