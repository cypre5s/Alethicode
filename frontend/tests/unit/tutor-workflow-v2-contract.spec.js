/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource(relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('tutor workflow v2 API contract (LangGraph migration)', () => {
  test('oj/api.js exports the new resource-shaped tutor workflow methods and drops legacy ones', () => {
    const apiEntrySource = readSource('../../src/pages/oj/api.js')
    const aiTutorSource = readSource('../../src/pages/oj/api/aiTutor.js')

    expect(apiEntrySource).toContain('...aiTutor')
    expect(aiTutorSource).toMatch(/tutorWorkflowCreateSession\s*\(/)
    expect(aiTutorSource).toMatch(/tutorWorkflowGetSession\s*\(/)
    expect(aiTutorSource).toMatch(/tutorWorkflowDeleteSession\s*\(/)
    expect(aiTutorSource).toMatch(/tutorWorkflowCreateRun\s*\(/)
    expect(aiTutorSource).toMatch(/tutorWorkflowGetCheckpoints\s*\(/)
    expect(aiTutorSource).toMatch(/tutorWorkflowRestoreCheckpoint\s*\(/)
    expect(aiTutorSource).toMatch(/tutorWorkflowRespondInterrupt\s*\(/)

    expect(aiTutorSource).toMatch(/ai\/tutor-workflow-sessions/)
    expect(aiTutorSource).toMatch(/\/checkpoint-restorations/)
    expect(aiTutorSource).toMatch(/\/interrupt-responses/)

    expect(aiTutorSource).not.toMatch(/workflowGetSession\s*\(/)
    expect(aiTutorSource).not.toMatch(/workflowCreateSession\s*\(/)
    expect(aiTutorSource).not.toMatch(/workflowClearSession\s*\(/)
    expect(aiTutorSource).not.toMatch(/workflowSnapshot\s*\(/)
    expect(aiTutorSource).not.toMatch(/ai\/workflow\/session/)
    expect(aiTutorSource).not.toMatch(/ai\/workflow\/event/)
    expect(aiTutorSource).not.toMatch(/ai\/workflow\/checkpoint/)
    expect(aiTutorSource).not.toMatch(/ai\/workflow\/interrupt/)
  })

  test('workflowStateMachine uses tutor-workflow-sessions endpoints exclusively', () => {
    const source = readSource('../../src/pages/oj/views/problem/workflowStateMachine.js')
    const serverStateSource = readSource('../../src/pages/oj/views/problem/workflowServerState.js')

    expect(source).toMatch(/api\.tutorWorkflowCreateRun\s*\(/)
    expect(source).toMatch(/api\.tutorWorkflowRespondInterrupt\s*\(/)
    expect(source).toMatch(/api\.tutorWorkflowRestoreCheckpoint\s*\(/)
    expect(source).toMatch(/api\.tutorWorkflowDeleteSession\s*\(/)
    expect(source).toContain("from './workflowServerState'")
    expect(source).toMatch(/fetchWorkflowSessionSnapshot\s*\(/)
    expect(source).toMatch(/fetchWorkflowCheckpoints\s*\(/)
    expect(serverStateSource).toMatch(/api\.tutorWorkflowGetSession\s*\(/)
    expect(serverStateSource).toMatch(/api\.tutorWorkflowGetCheckpoints\s*\(/)

    expect(source).not.toMatch(/api\.workflowEvent\s*\(/)
    expect(source).not.toMatch(/api\.workflowGetSession\s*\(/)
    expect(source).not.toMatch(/api\.workflowCreateSession\s*\(/)
    expect(source).not.toMatch(/api\.workflowGetCheckpoints\s*\(/)
    expect(source).not.toMatch(/api\.workflowInterrupt\s*\(/)
    expect(source).not.toMatch(/api\.workflowCheckpointRestore\s*\(/)
    expect(source).not.toMatch(/api\.workflowClearSession\s*\(/)
    expect(source).not.toMatch(/api\.workflowResume\s*\(/)
    expect(source).not.toMatch(/api\.workflowSteer\s*\(/)
  })

  test('workflowStateMachine uses the new tutor WebSocket path', () => {
    const source = readSource('../../src/pages/oj/views/problem/workflowStateMachine.js')

    expect(source).toMatch(/\/ws\/tutor-workflow-sessions\/\$\{sessionId\}/)
    expect(source).not.toMatch(/`\/ws\/workflow\/\$\{sessionId\}`/)
  })

  test('workflowStateMachine handles 202 QUEUED responses as async dispatch', () => {
    const source = readSource('../../src/pages/oj/views/problem/workflowStateMachine.js')

    expect(source).toMatch(/data\.runtime_state === 'QUEUED'/)
    expect(source).toMatch(/this\._scheduleWsResultWatchdog\(normalizedEvent\)/)
  })

  test('workflowStateMachine handles APPROVAL_REQUESTED runtime event and stops loading', () => {
    const source = readSource('../../src/pages/oj/views/problem/workflowStateMachine.js')

    expect(source).toMatch(/SERVER_EVENTS\.APPROVAL_REQUESTED/)
    expect(source).toMatch(/this\.pendingHumanAction = normalized\.data\.pending_human_action/)
  })

  test('runtimeContract exposes runId and threadId on normalized events', () => {
    const source = readSource('../../src/utils/runtimeContract.js')

    expect(source).toMatch(/runId:\s*raw\.run_id/)
    expect(source).toMatch(/threadId:\s*raw\.thread_id/)
  })
})
