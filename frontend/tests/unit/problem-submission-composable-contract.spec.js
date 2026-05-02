/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource(relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('problem submission composable contract', () => {
  test('should not expose Vue reserved underscore submit helpers from setup return', () => {
    const source = readSource('../../src/composables/problem/useSubmission.js')

    expect(source).toContain('function doRealSubmit ()')
    expect(source).not.toContain('function _doRealSubmit ()')
    expect(source).not.toContain('handlePreflightGoEdit, handlePreflightForceSubmit, _doRealSubmit,')
  })
})
