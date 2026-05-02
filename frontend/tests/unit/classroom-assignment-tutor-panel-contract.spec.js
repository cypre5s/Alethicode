/* eslint-env jest */

const fs = require('fs')
const path = require('path')

const repoRoot = path.resolve(__dirname, '../../..')

function readSource(relativePath) {
  return fs.readFileSync(path.resolve(repoRoot, relativePath), 'utf-8')
}

describe('Classroom assignment tutor panel contract', () => {
  it('AssignmentDetail.vue forwards from=assignment + anti_cheating in problem URL', () => {
    const view = readSource('frontend/src/pages/oj/views/classroom/AssignmentDetail.vue')
    expect(view).toContain('from=assignment')
    expect(view).toContain('anti_cheating=')
    expect(view).toContain('classroom_id=')
    expect(view).toContain('assignment_id=')
  })

  it('workflowStateMachine injects classroom_assignment context into session creation', () => {
    const machine = readSource('frontend/src/pages/oj/views/problem/workflowStateMachine.js')
    expect(machine).toContain('_resolveTutorSessionContext')
    expect(machine).toContain('classroom_assignment')
    expect(machine).toContain('anti_cheating')
    expect(machine).toContain("sessionPayload.context = tutorContext")
  })

  it('Problem.vue still respects ai_tutor_allowed query and hides UnifiedAgentPanel when disabled', () => {
    const problem = readSource('frontend/src/pages/oj/views/problem/Problem.vue')
    expect(problem).toContain('isAITutorEnabledForCurrentProblem')
    expect(problem).toContain('isAITutorAvailableInAssignment')
    expect(problem).toContain('ai_tutor_allowed')
    // panel rendering guarded by isAITutorEnabledForCurrentProblem
    expect(problem).toMatch(/v-if=\"isAITutorEnabledForCurrentProblem\"/)
  })
})

describe('Phase C backend bridge contract', () => {
  it('TutorWorkflowController validates classroom_assignment context anti_cheating', () => {
    const ctrl = readSource('backend/src/main/java/com/alethicode/controller/TutorWorkflowController.java')
    expect(ctrl).toContain('classroom_assignment')
    expect(ctrl).toContain('anti_cheating')
    expect(ctrl).toContain('extractContext')
    expect(ctrl).toContain('createThread(sessionId, userId, problemId, language, context)')
  })

  it('ClassroomAssignmentController exposes tutor-context endpoint', () => {
    const ctrl = readSource('backend/src/main/java/com/alethicode/controller/classroom/ClassroomAssignmentController.java')
    expect(ctrl).toContain('/tutor-context')
    expect(ctrl).toContain('assignmentProblemEnter')
  })

  it('TutorGraphClient supports context overload', () => {
    const client = readSource('backend/src/main/java/com/alethicode/service/aitutor/graph/TutorGraphClient.java')
    expect(client).toContain('createThread(String sessionId, long userId, long problemId, String language,\n                                                  Map<String, Object> context)')
    expect(client).toContain('"context"')
  })
})

describe('Phase C tutor-graph node contract', () => {
  it('reading.py wires anti_cheating guard prompt branch', () => {
    const reading = readSource('services/tutor-graph/app/nodes/reading.py')
    expect(reading).toContain('ANTI_CHEATING_GUARD')
    expect(reading).toContain('classroom_assignment')
    expect(reading).toContain('anti_cheating')
  })

  it('diagnosis.py wires anti_cheating guard prompt branch', () => {
    const diagnosis = readSource('services/tutor-graph/app/nodes/diagnosis.py')
    expect(diagnosis).toContain('ANTI_CHEATING_GUARD')
    expect(diagnosis).toContain('classroom_assignment')
    expect(diagnosis).toContain('anti_cheating')
  })

  it('main.py validates classroom_assignment thread context anti_cheating presence', () => {
    const main = readSource('services/tutor-graph/app/main.py')
    expect(main).toContain('_thread_contexts')
    expect(main).toContain('classroom_assignment context must declare anti_cheating')
    expect(main).toContain('thread_context')
  })

  it('tutor graph state.py declares context dict field', () => {
    const state = readSource('services/tutor-graph/app/graph/state.py')
    expect(state).toContain('context: dict')
  })
})
