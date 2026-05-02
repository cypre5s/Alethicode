/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource(relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('problem list responsive layout contract', () => {
  test('problem list should define dedicated main and sticky sidebar layout containers', () => {
    const source = readSource('../../src/pages/oj/views/problem/ProblemList.vue')

    expect(source).toContain('class="problem-list-layout"')
    expect(source).toContain('class="problem-list-main"')
    expect(source).toContain('class="problem-list-sidebar"')
    expect(source).toContain('class="problem-list-sidebar-stack"')
    expect(source).toContain('class="tag-panel-body"')
  })

  test('problem list styles should support sticky desktop sidebar and single-column narrow screens', () => {
    const source = readSource('../../src/pages/oj/views/problem/ProblemList.vue')

    expect(source).toContain('position: sticky;')
    expect(source).toContain('top: var(--oj-content-top-offset, 64px);')
    expect(source).toContain('min-height: calc(100dvh - var(--oj-content-top-offset, 64px));')
    expect(source).toContain('max-height: calc(100dvh - var(--oj-content-top-offset, 64px));')
    expect(source).toContain('@media screen and (max-width: 1279px)')
    expect(source).toContain('grid-template-columns: 1fr;')
  })
})
