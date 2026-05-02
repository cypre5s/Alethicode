/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource(relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('notebook day drawer contract (Phase 2)', () => {
  test('NotebookDayDrawer wires modelValue + escape + overlay click to close', () => {
    const source = readSource('../../src/pages/oj/views/user/notebook/NotebookDayDrawer.vue')
    expect(source).toContain('modelValue')
    expect(source).toContain('@click.self="close"')
    expect(source).toContain('this.$emit')
    expect(source).toContain("e.key === 'Escape'")
    expect(source).toContain("$emit('update:modelValue', false)")
  })

  test('Drawer surfaces both review and entry items with the right CTA wiring', () => {
    const source = readSource('../../src/pages/oj/views/user/notebook/NotebookDayDrawer.vue')
    expect(source).toContain("v-if=\"item.kind === 'review' && item.active_package_id\"")
    expect(source).toContain("v-else-if=\"item.kind === 'entry' && item.problem_id\"")
    expect(source).toContain("$emit('open-review-package', item)")
    expect(source).toContain("$emit('open-problem', item)")
  })

  test('LearnerNotebook open-review-package handler routes through buildReviewPackageRoute', () => {
    const orchestrator = readSource('../../src/pages/oj/views/user/LearnerNotebook.vue')
    expect(orchestrator).toContain('buildReviewPackageRoute')
    expect(orchestrator).toContain('@open-review-package="goReviewPackage"')
    expect(orchestrator).toContain('@open-problem="goProblem"')
    expect(orchestrator).toContain('this.$router.push(buildReviewPackageRoute(item.active_package_id))')
  })

  test('buildReviewPackageRoute encodes the package id through urlCipher', () => {
    const actions = readSource('../../src/pages/oj/views/user/notebook/notebookActions.js')
    expect(actions).toContain('buildReviewPackageRoute')
    expect(actions).toContain('encodeRouteCtx')
    expect(actions).toContain("ctx: encodeRouteCtx({ pkg: packageId })")
  })
})
