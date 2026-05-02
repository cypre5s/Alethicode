/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource (relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('admin problem permission redirect contract', () => {
  test('permission denied should not force jump to problem list', () => {
    const source = readSource('../../src/pages/admin/api.js')

    expect(source).toContain("res.data.error === 'permission-denied'")
    expect(source).not.toContain("router.push({ name: 'problem-list' })")
  })
})
