/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource(relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('submission list ui contract', () => {
  test('refresh button should use primary style to match rejudge action color', () => {
    const source = readSource('../../src/pages/oj/views/submission/SubmissionList.vue')
    expect(source).toContain('<el-button type="primary" class="submission-refresh-btn" @click="getSubmissions">')
  })

  test('option column should reserve enough width and center content for full rejudge text', () => {
    const source = readSource('../../src/pages/oj/views/submission/SubmissionList.vue')
    expect(source).toContain('class-name="submission-option-column"')
    expect(source).toContain('width="132"')
    expect(source).toContain(':deep(.submission-list-panel .submission-option-column .cell)')
    expect(source).toContain('justify-content: center;')
  })

  test('header typography should be compact and aligned like problem list header', () => {
    const source = readSource('../../src/pages/oj/views/submission/SubmissionList.vue')
    expect(source).toContain(':deep(.submission-list-panel .el-card__header)')
    expect(source).toContain('padding: 8px 18px;')
    expect(source).toContain(':deep(.submission-list-panel .panel-title)')
    expect(source).toContain('font-size: 14px;')
  })
})
