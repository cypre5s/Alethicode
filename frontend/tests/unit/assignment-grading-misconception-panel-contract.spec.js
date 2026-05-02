/* eslint-env jest */

const fs = require('fs')
const path = require('path')

const repoRoot = path.resolve(__dirname, '../../..')

function readSource(relativePath) {
  return fs.readFileSync(path.resolve(repoRoot, relativePath), 'utf-8')
}

describe('Assignment grading learner-context contract', () => {
  it('domain service injects recent_misconceptions and linked_review_package', () => {
    const impl = readSource('backend/src/main/java/com/alethicode/service/classroom/impl/ClassroomAssignmentDomainServiceImpl.java')
    expect(impl).toContain('recent_misconceptions')
    expect(impl).toContain('linked_review_package')
    expect(impl).toContain('ai_learner_notebook')
    expect(impl).toContain('ai_error_review_package')
  })

  it('AssignmentGrading.vue renders MisconceptionTagCloud and review package card', () => {
    const view = readSource('frontend/src/pages/oj/views/classroom/AssignmentGrading.vue')
    expect(view).toContain('MisconceptionTagCloud')
    expect(view).toContain('linked_review_package')
    expect(view).toContain('recent_misconceptions')
    expect(view).toContain('error_taxonomy')
    expect(view).toContain('reviewPackageHref')
  })
})

describe('LearningEventPublisher.publishAssignmentSubmissionGraded contract', () => {
  it('interface and noop / nats implementations carry the new method', () => {
    const intf = readSource('backend/src/main/java/com/alethicode/service/aitutor/events/LearningEventPublisher.java')
    expect(intf).toContain('publishAssignmentSubmissionGraded')

    const noop = readSource('backend/src/main/java/com/alethicode/service/aitutor/events/NoopLearningEventPublisher.java')
    expect(noop).toContain('publishAssignmentSubmissionGraded')

    const nats = readSource('backend/src/main/java/com/alethicode/service/aitutor/events/NatsLearningEventPublisher.java')
    expect(nats).toContain('publishAssignmentSubmissionGraded')
    expect(nats).toContain('alethicode.classroom.assignment.problem.submitted')
  })
})
