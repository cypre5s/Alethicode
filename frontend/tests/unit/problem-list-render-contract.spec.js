/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource(relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('problem list render contract', () => {
  test('problem list table should not use legacy render callbacks with h() component mounting', () => {
    const problemListSource = readSource('../../src/pages/oj/views/problem/ProblemList.vue')
    const problemListComposableSource = readSource('../../src/composables/useProblemList.js')

    expect(problemListSource).not.toContain('h(ViewButton')
    expect(problemListSource).not.toContain('h(ViewTag')

    expect(problemListComposableSource).not.toContain('h(ViewIcon')
  })
})
