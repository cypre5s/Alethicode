/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource (relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('unified agent panel timeline scroll contract', () => {
  test('should resync the message stream to bottom when timeline items or loading state changes', () => {
    const source = readSource('../../src/pages/oj/views/problem/UnifiedAgentPanel.vue')

    expect(source).toMatch(/syncMessageStreamToBottom\s*\(\)\s*\{/)
    expect(source).toMatch(/timelineItems:\s*\{[\s\S]*handler\s*\(\)\s*\{[\s\S]*this\.syncMessageStreamToBottom\(\)/)
    expect(source).toMatch(/loading\s*\(\)\s*\{[\s\S]*this\.syncMessageStreamToBottom\(\)/)
    expect(source).toMatch(/visible\s*\(val\)\s*\{[\s\S]*this\.syncMessageStreamToBottom\(\)/)
  })
})
