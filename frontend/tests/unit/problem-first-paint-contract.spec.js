/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource (relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('problem first paint contract', () => {
  test('problem view defers non-critical hydration until after the core problem payload is applied', () => {
    const source = readSource('../../src/pages/oj/views/problem/Problem.vue')

    expect(source).toContain('scheduleNonCriticalProblemHydration (problem, requestToken)')
    expect(source).toContain('this.scheduleNonCriticalProblemHydration(problem, requestToken)')
    expect(source).toContain('api.submissionExists(problem.id).then(res => {')
    expect(source).toContain('this.initWorkflowSession(problem.id)')
    expect(source).toContain('this.$loadingFinish()')
  })

  test('problem view lazy loads non-critical assistant panels instead of bundling them into first paint', () => {
    const source = readSource('../../src/pages/oj/views/problem/Problem.vue')

    expect(source).toContain("import { defineAsyncComponent } from 'vue'")
    expect(source).toContain("const UnifiedAgentPanel = defineAsyncComponent(() => import('./UnifiedAgentPanel.vue'))")
    expect(source).toContain("const PreflightDialog = defineAsyncComponent(() => import('./PreflightDialog.vue'))")
    expect(source).toContain("const SubmissionRiver = defineAsyncComponent(() => import('@oj/components/SubmissionRiver'))")
    expect(source).not.toContain("import ProblemDescription from './ProblemDescription.vue'")
    expect(source).not.toContain("import CodeAnalysisPanel from './CodeAnalysisPanel.vue'")
    expect(source).not.toContain("import SubmissionPanel from './SubmissionPanel.vue'")
  })
})
