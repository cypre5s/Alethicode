/* eslint-env jest */

const fs = require('fs')
const path = require('path')

const repoRoot = path.resolve(__dirname, '../../..')

function readSource(relativePath) {
  return fs.readFileSync(path.resolve(repoRoot, relativePath), 'utf-8')
}

describe('Classroom Assignment smart compose contract', () => {
  it('backend exposes preview-smart-compose endpoint', () => {
    const controller = readSource('backend/src/main/java/com/alethicode/controller/classroom/ClassroomAssignmentController.java')
    expect(controller).toContain('preview-smart-compose')
    expect(controller).toContain('assignmentPreviewSmartCompose')
  })

  it('domain service routes smart_kc to ClassroomAssignmentSmartComposer', () => {
    const impl = readSource('backend/src/main/java/com/alethicode/service/classroom/impl/ClassroomAssignmentDomainServiceImpl.java')
    expect(impl).toContain('smartComposer.composeForClassroom')
    expect(impl).toContain('smart_kc')
    expect(impl).toContain('compose_strategy')
    expect(impl).toContain('target_kc_ids')
    expect(impl).toContain('publishAssignmentSubmissionGraded')
    expect(impl).toContain('classroomAssignmentEventSubscriber.onAssignmentSubmissionGraded')
  })

  it('frontend api wrappers cover smart compose preview', () => {
    const api = readSource('frontend/src/pages/oj/api/classroom.js')
    expect(api).toContain('previewClassroomAssignmentSmartCompose')
    expect(api).toContain('preview-smart-compose')
    const aggregator = readSource('frontend/src/api/modules/classroom.js')
    expect(aggregator).toContain('previewClassroomAssignmentSmartCompose')
  })

  it('ClassroomAssignment.vue introduces compose mode + smart compose UI', () => {
    const view = readSource('frontend/src/pages/oj/views/classroom/ClassroomAssignment.vue')
    expect(view).toContain('组卷模式')
    expect(view).toContain('compose_strategy')
    expect(view).toContain('previewSmartCompose')
    expect(view).toContain('applySmartComposeAsSections')
    expect(view).toContain('flatSmartComposePreview')
    expect(view).toContain('per_student_budget')
    expect(view).toContain('total_problem_budget')
  })
})

describe('Phase B backend schema migration V82', () => {
  it('V82 migration adds compose_strategy / target_kc_ids and submission columns', () => {
    const sql = readSource('backend/src/main/resources/db/migration/V82__classroom_assignment_smart_compose.sql')
    expect(sql).toContain('classroom_assignment')
    expect(sql).toContain('compose_strategy')
    expect(sql).toContain("'manual'")
    expect(sql).toContain("'smart_kc'")
    expect(sql).toContain('target_kc_ids')
    expect(sql).toContain('classroom_assignment_problem_submission')
    expect(sql).toContain('error_taxonomy')
    expect(sql).toContain('review_package_id')
  })
})
