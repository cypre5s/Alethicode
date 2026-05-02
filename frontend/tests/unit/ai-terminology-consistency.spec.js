const fs = require('fs')
const path = require('path')

function readSource (relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('AI terminology consistency (L1/L2/L3)', () => {
  test('submission details keeps L2/L3 labels', () => {
    const source = readSource('../../src/pages/oj/views/submission/SubmissionDetails.vue')
    expect(source).toContain('AI 纠错（提交后）')
    expect(source).toContain('AI 优化（AC 后）')
  })

  test('problem page user-facing strings avoid legacy AI Hack wording', () => {
    const source = readSource('../../src/pages/oj/views/problem/Problem.vue')
    expect(source).toContain('AI 优化（AC 后）对抗分析')
    expect(source).not.toContain('AI Hack generation failed')
  })
})
