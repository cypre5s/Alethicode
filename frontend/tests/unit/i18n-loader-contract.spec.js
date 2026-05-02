/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource(relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('i18n loader contract', () => {
  test('uses static ESM locale imports instead of dynamic require', () => {
    const source = readSource('../../src/i18n/index.js')

    expect(source).not.toMatch(/\brequire\s*\(/)
    expect(source).toMatch(/import\s+\{\s*m\s+as\s+ojEnUS\s*\}\s+from\s+'\.\/oj\/en-US'/)
    expect(source).toMatch(/import\s+\{\s*m\s+as\s+ojZhCN\s*\}\s+from\s+'\.\/oj\/zh-CN'/)
    expect(source).toMatch(/import\s+\{\s*m\s+as\s+ojZhTW\s*\}\s+from\s+'\.\/oj\/zh-TW'/)
    expect(source).toMatch(/import\s+\{\s*m\s+as\s+adminEnUS\s*\}\s+from\s+'\.\/admin\/en-US'/)
    expect(source).toMatch(/import\s+\{\s*m\s+as\s+adminZhCN\s*\}\s+from\s+'\.\/admin\/zh-CN'/)
    expect(source).toMatch(/import\s+\{\s*m\s+as\s+adminZhTW\s*\}\s+from\s+'\.\/admin\/zh-TW'/)
  })
})
