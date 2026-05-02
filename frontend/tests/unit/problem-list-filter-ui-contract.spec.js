/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource(relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('problem list filter ui contract', () => {
  test('tag panel filter buttons should use larger readable font size', () => {
    const source = readSource('../../src/pages/oj/views/problem/ProblemList.vue')
    const tagPanelButtonBlock = source.match(/:deep\(\.tag-panel \.el-button\)\s*\{[\s\S]*?\}/)

    expect(tagPanelButtonBlock).not.toBeNull()
    expect(tagPanelButtonBlock[0]).toContain('font-size: 14px;')
  })

  test('difficulty column should reserve enough width for the difficulty pill without ellipsis', () => {
    const source = readSource('../../src/pages/oj/views/problem/ProblemList.vue')

    expect(source).toContain(':label="$t(\'m.Level\')"')
    expect(source).not.toContain(':label="$t(\'m.Level\')" width="90"')
  })
})
