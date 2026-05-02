/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource (relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('unified agent panel empty state contract', () => {
  test('should not render checkpoint-only timeline as non-empty message stream', () => {
    const source = readSource('../../src/pages/oj/views/problem/UnifiedAgentPanel.vue')

    expect(source).toMatch(/const messageItems = this\.messages\.map/)
    expect(source).toMatch(/if \(messageItems\.length === 0\) return \[\]/)
  })
})
