/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource(relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('admin beta features layout contract', () => {
  test('beta features page should not render the redundant panel title header or beta banner', () => {
    const source = readSource('../../src/pages/admin/views/general/BetaFeatures.vue')

    expect(source).toContain('<Panel')
    expect(source).not.toContain('<Panel title="Beta 功能管理">')
    expect(source).not.toContain('class="beta-banner"')
    expect(source).not.toContain('实验性功能')
  })
})
