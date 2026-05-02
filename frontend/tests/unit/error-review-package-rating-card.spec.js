/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource(relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('error review package rating card contract (Phase 3)', () => {
  test('aiTutor api exposes the new per-problem and per-package rating endpoints', () => {
    const api = readSource('../../src/pages/oj/api/aiTutor.js')
    expect(api).toContain('rateReviewProblem(packageId, problemId, rating)')
    expect(api).toContain("ajax(`ai/review-packages/${packageId}/problems/${problemId}/rating`")
    expect(api).toContain('rateReviewPackage(packageId, rating)')
    expect(api).toContain("ajax(`ai/review-packages/${packageId}/reviews`")
  })

  test('ReviewProblemCard wires three states: pending / rate-pair / mastered+similar badges', () => {
    const source = readSource('../../src/pages/oj/views/review/components/ReviewProblemCard.vue')
    expect(source).toContain('v-if="!problem.submitted"')
    expect(source).toContain('<span>我会了</span>')
    expect(source).toContain('<span>再练一题</span>')
    expect(source).toContain('<Check />')
    expect(source).toContain('<RefreshRight />')
    expect(source).toContain('已掌握')
    expect(source).toContain('已生成相似题')
    expect(source).toContain("@click=\"rate('good')\"")
    expect(source).toContain("@click=\"rate('again')\"")
    expect(source).toContain("$emit('rate'")
    expect(source).toContain("$emit('open-problem'")
  })

  test('ReviewProblemCard exposes the Parsons quick entry alongside rating buttons', () => {
    const source = readSource('../../src/pages/oj/views/review/components/ReviewProblemCard.vue')

    expect(source).toContain('试试拼装版')
    expect(source).toContain("$emit('open-parsons', problem)")
    expect(source).toContain("'open-parsons'")
  })

  test('ReviewProblemCard localizes planner enum labels instead of exposing raw keys', () => {
    const source = readSource('../../src/pages/oj/views/review/components/ReviewProblemCard.vue')
    expect(source).toContain("apply: '应用'")
    expect(source).toContain("coding_problem: '编程题'")
    expect(source).toContain('educationGoalLabel')
    expect(source).toContain('cardTypeLabel')
  })

  test('ReviewProblemCard exposes loading + just-added overlays for the again flow', () => {
    const source = readSource('../../src/pages/oj/views/review/components/ReviewProblemCard.vue')
    expect(source).toContain("'is-just-added'")
    expect(source).toContain('class="rpc-loading-overlay"')
    expect(source).toContain('正在为你生成相似题')
    expect(source).toContain('@keyframes rpc-flash')
  })

  test('ReviewMasteryDialog is gated behind explicit "all good" + new-state choice', () => {
    const dialog = readSource('../../src/pages/oj/views/review/components/ReviewMasteryDialog.vue')
    expect(dialog).toContain("$emit('choose'")
    expect(dialog).toContain("emitChoice('good')")
    expect(dialog).toContain("emitChoice('again')")
  })
})
