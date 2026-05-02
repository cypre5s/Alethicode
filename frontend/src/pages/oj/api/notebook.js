/**
 * 学习笔记本 (Learner Notebook)：记录学习条目、班级高频错点、周度总结与反思。
 */

import { ajax } from './shared'

export default {
  getLearnerNotebook(params) {
    return ajax('ai/tutor/notebook', 'get', { params })
  },
  addLearnerNotebookEntry(data) {
    return ajax('ai/tutor/notebook', 'post', { data })
  },
  updateLearnerNotebookEntry(data) {
    return ajax('ai/tutor/notebook', 'put', { data })
  },
  deleteLearnerNotebookEntry(entryId) {
    return ajax('ai/tutor/notebook', 'delete', {
      params: { id: entryId }
    })
  },
  exportLearnerNotebook() {
    return ajax('ai/tutor/notebook/export', 'get')
  },
  getNotebookClassFrequency() {
    return ajax('ai/tutor/notebook/class-frequency', 'get')
  },
  generateNotebookReflection(data) {
    return ajax('ai/tutor/notebook/generate-reflection', 'post', { data })
  },
  getNotebookWeeklySummary() {
    return ajax('ai/tutor/notebook/weekly-summary', 'get')
  }
}
