/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource(relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

function buildGetACRate() {
  const source = readSource('../../src/utils/utils.js')
  const match = source.match(/function getACRate\s*\(([^)]*)\)\s*\{([\s\S]*?)\n\}\n\n\/\/ 去掉值为空的项/)
  if (!match) {
    throw new Error('cannot find getACRate function in utils.js')
  }
  const args = match[1]
  const body = match[2]
  // eslint-disable-next-line no-new-func
  return new Function(args, body)
}

describe('problem list ac rate and layout contract', () => {
  test('AC rate should show 0% when accepted count is 0 and total is greater than 0', () => {
    const getACRate = buildGetACRate()
    expect(getACRate(0, 7)).toBe('0%')
    expect(getACRate('0', '7')).toBe('0%')
  })

  test('problem list columns should define dedicated classes for difficulty and tags layout control', () => {
    const source = readSource('../../src/pages/oj/views/problem/ProblemList.vue')
    expect(source).toContain('class-name="problem-difficulty-column"')
    expect(source).toContain('class-name="problem-tags-column"')
    expect(source).toContain('difficultyText(scope.row.difficulty)')
    expect(source).not.toContain("$t('m.' + scope.row.difficulty)")
  })

  test('problem list tags should render first tag and expose remaining tags via click popover', () => {
    const source = readSource('../../src/pages/oj/views/problem/ProblemList.vue')
    expect(source).toContain('scope.row.tags[0]')
    expect(source).toContain('v-if="scope.row.tags.length > 1"')
    expect(source).toContain('problem-list-tag-more')
    expect(source).toContain('scope.row.tags.slice(1)')
    expect(source).toContain('.problem-tags-popover .problem-list-tag-chip')
  })

  test('problem list tags column should stay single-line without clipping', () => {
    const source = readSource('../../src/pages/oj/views/problem/ProblemList.vue')
    expect(source).toContain(':deep(.problem-tags-column .cell)')
    expect(source).toContain('white-space: nowrap;')
    expect(source).toContain('text-overflow: clip;')
  })

  test('problem list header should use compact and unified typography', () => {
    const source = readSource('../../src/pages/oj/views/problem/ProblemList.vue')
    expect(source).toContain(':deep(.problem-list-panel .el-card__header)')
    expect(source).toContain('padding: 8px 18px;')
    expect(source).toContain('.filter-trigger')
    expect(source).toContain('font-size: 14px;')
  })
})
