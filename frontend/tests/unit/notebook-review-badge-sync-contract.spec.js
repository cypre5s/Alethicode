/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource (relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('notebook review badge sync contract', () => {
  test('NavBar subscribes review badge refresh event', () => {
    const source = readSource('../../src/pages/oj/components/NavBar.vue')
    expect(source).toContain("const REVIEW_DUE_UPDATED_EVENT = 'oj:review-due-updated'")
    expect(source).toContain('window.addEventListener(REVIEW_DUE_UPDATED_EVENT')
    expect(source).toContain('window.removeEventListener(REVIEW_DUE_UPDATED_EVENT')
  })

  test('Notebook constants module owns the shared event name', () => {
    const source = readSource('../../src/pages/oj/views/user/notebook/notebookConstants.js')
    expect(source).toContain("export const REVIEW_DUE_UPDATED_EVENT = 'oj:review-due-updated'")
  })

  test('LearnerNotebook orchestrator publishes review badge refresh after mutations', () => {
    const source = readSource('../../src/pages/oj/views/user/LearnerNotebook.vue')
    expect(source).toContain('REVIEW_DUE_UPDATED_EVENT')
    expect(source).toContain('window.dispatchEvent(new CustomEvent(REVIEW_DUE_UPDATED_EVENT))')
    expect(source).toContain('api.deleteLearnerNotebookEntry')
    expect(source).toContain('api.addLearnerNotebookEntry')
  })
})
