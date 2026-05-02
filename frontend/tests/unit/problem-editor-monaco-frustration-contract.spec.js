/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource (relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('problem editor CM6 frustration contract', () => {
  test('frustration mixin detects CM6 core via onChangeSubscribe and falls back to legacy CM5', () => {
    const source = readSource('../../src/composables/problem/useFrustration.js')

    expect(source).toContain('onChangeSubscribe')
    expect(source).toContain('update.changes.iterChanges')
    expect(source).toContain("if (typeof cm.on === 'function')")
  })
})
