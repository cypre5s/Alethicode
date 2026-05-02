/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource (relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('workflow runtime phase2 contract', () => {
  test('workflowStateMachine should no longer depend on xstate runtime service', () => {
    const source = readSource('../../src/pages/oj/views/problem/workflowStateMachine.js')

    expect(source).not.toContain("from './problemWorkflowMachine'")
    expect(source).not.toContain('_ensureWorkflowMachine')
    expect(source).not.toContain('_workflowMachineService')
  })

  test('workflowStateMachine should derive loading and plan state from runtime snapshot', () => {
    const source = readSource('../../src/pages/oj/views/problem/workflowStateMachine.js')

    expect(source).toContain('_sendWorkflowMachineEvent(type, payload = {})')
    expect(source).toContain("eventType === 'RESET' || eventType === 'CLEAR'")
    expect(source).toContain('planState === \'plan_paused\'')
    expect(source).toContain('planState === \'plan_completed\'')
    expect(source).toContain('this.agentLoading = lifecycleState === \'running\' || lifecycleState === \'restoring\' || lifecycleState === \'ws_connecting\'')
  })

  test('problem page should consume tutor workflow runtime via composable instead of mixin', () => {
    const source = readSource('../../src/pages/oj/views/problem/Problem.vue')

    expect(source).toContain('import { useTutorWorkflowRuntime }')
    expect(source).toContain('const tutorWorkflowRuntime = useTutorWorkflowRuntime()')
    expect(source).toContain('...tutorWorkflowRuntime.data.call(this)')
    expect(source).toContain('...tutorWorkflowRuntime.computed')
    expect(source).toContain('...tutorWorkflowRuntime.methods')
    expect(source).not.toContain('mixins: [workflowStateMachine]')
  })
})
