/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource(relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('time utils export contract', () => {
  test('time utils expose both named exports and default export for shared consumers', () => {
    const source = readSource('../../src/utils/time.js')

    expect(source).toContain('export { utcToLocal, duration, secondFormat }')
    expect(source).toContain('export default {')
  })
})
