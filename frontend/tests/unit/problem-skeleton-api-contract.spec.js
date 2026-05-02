/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource (relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('problem skeleton api contract', () => {
  test('should request skeleton code through workflow SKELETON event instead of legacy ideate api', () => {
    const problemSource = readSource('../../src/pages/oj/views/problem/Problem.vue')
    const apiSource = readSource('../../src/pages/oj/api/aiTutor.js')

    expect(problemSource).toContain("this.dispatchWorkflowEvent('SKELETON'")
    expect(problemSource).not.toContain('api.ideateGetSkeleton({')
    expect(apiSource).not.toContain('ideateGetSkeleton(data)')
    expect(apiSource).not.toContain("ajax('ai/ideate/skeleton'")
    expect(apiSource).not.toContain('ideateMarkInserted(data)')
    expect(apiSource).not.toContain("ajax('ai/ideate/inserted'")
  })
})
