/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource(relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('oj shell responsive layout contract', () => {
  test('shared common styles should not enforce a global 900px minimum body width', () => {
    const source = readSource('../../src/styles/common.less')

    expect(source).not.toContain('min-width: 900px;')
  })

  test('oj app shell should provide viewport-based content height with safe width constraints', () => {
    const source = readSource('../../src/pages/oj/App.vue')

    expect(source).toContain('min-height: calc(100dvh - 160px);')
    expect(source).toContain('min-height: calc(100dvh - 64px);')
    expect(source).toContain('min-width: 0;')
    expect(source).toContain('max-width: 100%;')
  })
})
