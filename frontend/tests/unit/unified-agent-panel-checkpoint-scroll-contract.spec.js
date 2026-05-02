/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource (relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('unified agent panel checkpoint scroll contract', () => {
  test('should keep each timeline shell as a non-shrinking flex item so late cards do not collapse', () => {
    const source = readSource('../../src/pages/oj/views/problem/UnifiedAgentPanel.vue')

    expect(source).toMatch(/\.timeline-item-shell\s*\{[\s\S]*flex-shrink:\s*0;/)
    expect(source).not.toMatch(/\.timeline-item-shell\s*\{[\s\S]*display:\s*contents;/)
  })
})
