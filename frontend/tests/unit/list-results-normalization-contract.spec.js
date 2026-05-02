/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource (relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('list results normalization contract', () => {
  test('HomeDashboard normalizes paginated results before map and slice', () => {
    const source = readSource('../../src/pages/oj/views/general/HomeDashboard.vue')
    expect(source).toContain('const results = Array.isArray(data.results) ? data.results : []')
    expect(source).toContain('this.recentSubmissions = results.map(')
    expect(source).toContain('this.announcements = results.slice(0, 2)')
  })

  test('classroom lists normalize results payloads before storing or mapping', () => {
    const aiGeneratedProblemsSource = readSource('../../src/pages/oj/views/classroom/AIGeneratedProblems.vue')
    const classroomListSource = readSource('../../src/pages/oj/views/classroom/ClassroomList.vue')
    const lessonManagementSource = readSource('../../src/pages/oj/views/classroom/LessonManagement.vue')

    expect(aiGeneratedProblemsSource).toContain('this.problems = Array.isArray(res.data.data.results) ? res.data.data.results : []')
    expect(aiGeneratedProblemsSource).toContain("Array.isArray(payload.results) ? payload.results : (Array.isArray(res.data.results) ? res.data.results : [])")
    expect(classroomListSource).toContain('this.classrooms = Array.isArray(res.data.data.results) ? res.data.data.results : []')
    expect(lessonManagementSource).toContain("Array.isArray(payload.results) ? payload.results : (Array.isArray(res.data.results) ? res.data.results : [])")
  })

  test('admin problem kc loading normalizes results before mapping', () => {
    const source = readSource('../../src/pages/admin/views/problem/Problem.vue')
    expect(source).toContain('const rawResults = res && res.data && res.data.data ? res.data.data.results : []')
    expect(source).toContain("const options = (Array.isArray(rawResults) ? rawResults : [])")
    expect(source).toContain("const kcNames = (Array.isArray(rawResults) ? rawResults : []).map(item => item.name).filter(Boolean)")
  })
})
