/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource(relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('error review package page orchestration contract (Phase 3)', () => {
  test('Page splits into orchestrator + 5 sub-components, each ≤300 lines', () => {
    const orchestrator = readSource('../../src/pages/oj/views/review/ErrorReviewPackagePage.vue')
    expect(orchestrator.split('\n').length).toBeLessThanOrEqual(300)

    const subfiles = [
      'ReviewPackageHeader.vue',
      'ReviewPackageEvidence.vue',
      'ReviewProblemCard.vue',
      'ReviewProblemUnavailableCard.vue',
      'ReviewMasteryDialog.vue'
    ]
    for (const file of subfiles) {
      const p = path.resolve(__dirname, '../../src/pages/oj/views/review/components/', file)
      expect(fs.existsSync(p)).toBe(true)
      const lines = fs.readFileSync(p, 'utf8').split('\n').length
      expect(lines).toBeLessThanOrEqual(300)
    }
  })

  test('Orchestrator routes is_unavailable rows to ReviewProblemUnavailableCard placeholder', () => {
    const source = readSource('../../src/pages/oj/views/review/ErrorReviewPackagePage.vue')
    expect(source).toContain('ReviewProblemUnavailableCard')
    expect(source).toContain('v-if="problem.is_unavailable"')
    expect(source).toContain('复习包内的题目已全部下架或被移除')
  })

  test('ReviewProblemUnavailableCard renders the offline placeholder without action buttons', () => {
    const source = readSource('../../src/pages/oj/views/review/components/ReviewProblemUnavailableCard.vue')
    expect(source).toContain('该题目已下架')
    expect(source).toContain('不可练习')
    expect(source).toContain('rpc-status-disabled')
    expect(source).not.toMatch(/<ElButton[\s>]/)
  })

  test('Orchestrator renders FSRS header, evidence, both review and AI sections, and mastery dialog', () => {
    const source = readSource('../../src/pages/oj/views/review/ErrorReviewPackagePage.vue')
    expect(source).toContain('ReviewPackageHeader')
    expect(source).toContain('ReviewPackageEvidence')
    expect(source).toContain('ReviewProblemCard')
    expect(source).toContain('ReviewMasteryDialog')
    expect(source).toContain('reviewProblems')
    expect(source).toContain('aiProblems')
    expect(source).toContain('@rate="handleRate"')
    expect(source).toContain('@open-problem="goToProblem"')
  })

  test('Orchestrator routes Parsons FSRS entry through ?parsons=1&fsrs_origin', () => {
    const source = readSource('../../src/pages/oj/views/review/ErrorReviewPackagePage.vue')

    expect(source).toContain('@open-parsons="goToProblemAsParsons"')
    expect(source).toContain('goToProblemAsParsons (problem)')
    expect(source).toContain("parsons: '1'")
    expect(source).toContain('fsrs_origin: (this.pkg && this.pkg.id) || \'\'')
  })

  test('handleRate calls rateReviewProblem, refreshes pkg, and flashes the new just-added problem on again', () => {
    const source = readSource('../../src/pages/oj/views/review/ErrorReviewPackagePage.vue')
    expect(source).toContain('api.rateReviewProblem(this.pkg.id, problem.id, rating)')
    expect(source).toContain('previousIds')
    expect(source).toContain('flashJustAdded')
    expect(source).toContain('scrollIntoView')
    expect(source).toContain('justAddedProblemId')
  })

  test('Mastery dialog is auto-opened only when every problem.user_rating === good', () => {
    const source = readSource('../../src/pages/oj/views/review/ErrorReviewPackagePage.vue')
    expect(source).toContain('allProblemsRatedGood')
    expect(source).toContain("p.submitted && p.user_rating === 'good'")
    expect(source).toContain('maybeShowMasteryDialog')
    expect(source).toContain('finishMastery')
    expect(source).toContain('api.rateReviewPackage(this.pkg.id, rating)')
  })

  test('Page uses encoded route ctx for the package id (no plaintext pkg id in URL)', () => {
    const source = readSource('../../src/pages/oj/views/review/ErrorReviewPackagePage.vue')
    expect(source).toContain("decodeRouteCtx(this.$route.query.ctx).pkg")
  })

  test('Page loads available packages and renders a selector for switching review sheets', () => {
    const source = readSource('../../src/pages/oj/views/review/ErrorReviewPackagePage.vue')
    expect(source).toContain('api.getReviewPackages')
    expect(source).toContain('packageOptions')
    expect(source).toContain('selectedPackageId')
    expect(source).toContain('ElSelect')
    expect(source).toContain('switchPackage')
    expect(source).toContain('encodeRouteCtx({ pkg: packageId')
  })
})
