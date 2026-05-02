/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource(relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('OJ beginner supplement planner contract', () => {
  test('OJ api exposes supplement planner endpoint and enriched review package payload', () => {
    const apiIndex = readSource('../../src/pages/oj/api.js')
    const aiTutorApi = readSource('../../src/pages/oj/api/aiTutor.js')

    expect(apiIndex).toContain("import aiTutor from './api/aiTutor'")
    expect(apiIndex).toContain('...aiTutor')
    expect(aiTutorApi).toContain('getSupplementPlan')
    expect(aiTutorApi).toContain("ajax('ai/tutor/supplement-plan', 'post'")
    expect(aiTutorApi).toContain('createReviewPackage(data)')
    expect(aiTutorApi).toContain('createReviewPackages(data)')
    expect(aiTutorApi).toContain("ajax('ai/review-packages/batches', 'post'")
  })

  test('home dashboard should load unified supplement plan for warmup guidance', () => {
    const source = readSource('../../src/pages/oj/views/general/HomeDashboard.vue')

    expect(source).toContain('api.getSupplementPlan')
    expect(source).toContain("trigger: 'warmup'")
    expect(source).toContain('next-step-section')
    expect(source).toContain('supplementPlan')
  })

  test('problem page should load unified supplement plan when learner gets stuck', () => {
    const source = readSource('../../src/pages/oj/views/problem/Problem.vue')

    expect(source).toContain('api.getSupplementPlan')
    expect(source).toContain("trigger: 'stuck'")
    expect(source).toContain('showSupplementCards')
    expect(source).toContain('why_this_now')
  })

  test('problem page should render beginner supplement cards with Chinese readable labels', () => {
    const source = readSource('../../src/pages/oj/views/problem/Problem.vue')

    expect(source).toContain('formatStepLabel')
    expect(source).toContain("coding_problem: '编程练习'")
    expect(source).toContain('formatSupplementCardType(card.card_type)')
    expect(source).toContain('课件例题：')
    expect(source).toContain('知识点：')
    expect(source).not.toContain('Step {{ idx + 1 }}')
    expect(source).not.toContain('{{ card.card_type }}')
  })

  test('learner notebook should create review package with language pack aware supplement trigger', () => {
    const notebook = readSource('../../src/pages/oj/views/user/LearnerNotebook.vue')
    const actions = readSource('../../src/pages/oj/views/user/notebook/notebookActions.js')

    expect(notebook).toContain('buildReviewPackageGroups(this.entries, this.selectedCategory)')
    expect(notebook).toContain('createReviewPackages({ groups })')
    expect(actions).toContain('language_pack_id')
    expect(actions).toContain("trigger: 'wrong_answer'")
    expect(actions).toContain('api.createReviewPackages')
    expect(actions).toContain('buildReviewPackageGroups')
  })

  test('review package page should render ladder metadata from unified supplement planner', () => {
    const source = readSource('../../src/pages/oj/views/review/components/ReviewProblemCard.vue')

    expect(source).toContain('problem.education_goal')
    expect(source).toContain('problem.why_this_now')
    expect(source).toContain('problem.card_type')
  })

  test('review problem card should hide raw planner enum labels from students', () => {
    const source = readSource('../../src/pages/oj/views/review/components/ReviewProblemCard.vue')

    expect(source).toContain("understand: '理解'")
    expect(source).toContain("recall: '回忆'")
    expect(source).toContain("apply: '应用'")
    expect(source).toContain("transfer: '迁移'")
    expect(source).toContain("coding_problem: '编程题'")
  })
})
