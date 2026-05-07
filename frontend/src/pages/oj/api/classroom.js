/**
 * 课堂域接口按 `/classroom/${classroomId}/...` 主前缀集中维护。
 */

import { ajax } from './shared'

export default {
  getClassroomList(params) {
    return ajax('classroom/', 'get', { params })
  },
  createClassroom(data) {
    return ajax('classroom/', 'post', { data })
  },
  getClassroom(classroomId) {
    return ajax(`classroom/${classroomId}/`, 'get')
  },
  updateClassroom(classroomId, data) {
    return ajax(`classroom/${classroomId}/`, 'patch', { data })
  },
  deleteClassroom(classroomId) {
    return ajax(`classroom/${classroomId}/`, 'delete')
  },
  getClassroomStats(classroomId) {
    return ajax(`classroom/${classroomId}/monitor/stats/`, 'get')
  },

  generateInvitation(classroomId, data) {
    return ajax(`classroom/invitation/generate/${classroomId}/`, 'post', { data })
  },
  joinClassroom(data) {
    return ajax('classroom/invitation/join/', 'post', { data })
  },
  getInvitationList(classroomId, params) {
    return ajax(`classroom/invitation/list/${classroomId}/`, 'get', { params })
  },
  deactivateInvitation(invitationId) {
    return ajax(`classroom/invitation/${invitationId}/deactivate/`, 'post')
  },

  getClassroomMembers(classroomId, params) {
    return ajax(`classroom/${classroomId}/members/`, 'get', { params })
  },
  promoteClassroomMember(classroomId, memberId) {
    return ajax(`classroom/${classroomId}/members/${memberId}/promote/`, 'post')
  },
  demoteClassroomMember(classroomId, memberId) {
    return ajax(`classroom/${classroomId}/members/${memberId}/demote/`, 'post')
  },
  removeClassroomMember(classroomId, memberId) {
    return ajax(`classroom/${classroomId}/members/${memberId}/`, 'delete')
  },

  getClassroomProblems(classroomId, params) {
    return ajax(`classroom/${classroomId}/problems/`, 'get', { params })
  },
  getClassroomProblem(classroomId, classroomProblemId) {
    return ajax(`classroom/${classroomId}/problems/${classroomProblemId}/`, 'get')
  },
  addClassroomProblem(classroomId, data) {
    return ajax(`classroom/${classroomId}/problems/`, 'post', { data })
  },
  updateClassroomProblem(classroomId, problemId, data) {
    return ajax(`classroom/${classroomId}/problems/${problemId}/`, 'patch', { data })
  },
  removeClassroomProblem(classroomId, problemId) {
    return ajax(`classroom/${classroomId}/problems/${problemId}/`, 'delete')
  },
  importObjectiveQuestions(classroomId, formData) {
    return ajax(`classroom/${classroomId}/problems/import-objective-json/`, 'post', {
      data: formData
    })
  },
  exportObjectiveQuestions(classroomId) {
    return ajax(`classroom/${classroomId}/problems/export-objective-json/`, 'get')
  },

  getLessonList(classroomId, params) {
    return ajax(`classroom/${classroomId}/lessons/`, 'get', { params })
  },
  uploadLesson(classroomId, formData) {
    return ajax(`classroom/${classroomId}/lessons/`, 'post', {
      data: formData
    })
  },
  getLesson(classroomId, lessonId) {
    return ajax(`classroom/${classroomId}/lessons/${lessonId}/`, 'get')
  },
  deleteLesson(classroomId, lessonId) {
    return ajax(`classroom/${classroomId}/lessons/${lessonId}/`, 'delete')
  },

  generateProblemFromLesson(classroomId, data) {
    return ajax(`classroom/${classroomId}/ai/generated-problems/`, 'post', { data })
  },
  getAIGeneratedProblems(classroomId, params) {
    return ajax(`classroom/${classroomId}/ai/generated-problems/`, 'get', { params })
  },
  getAIGeneratedProblem(classroomId, problemId) {
    return ajax(`classroom/${classroomId}/ai/generated-problems/${problemId}/`, 'get')
  },
  updateAIGeneratedProblem(classroomId, problemId, data) {
    return ajax(`classroom/${classroomId}/ai/generated-problems/${problemId}/`, 'patch', { data })
  },
  publishAIGeneratedProblem(classroomId, problemId) {
    return ajax(`classroom/${classroomId}/ai/generated-problems/${problemId}/publish/`, 'post')
  },
  deleteAIGeneratedProblem(classroomId, problemId) {
    return ajax(`classroom/${classroomId}/ai/generated-problems/${problemId}/`, 'delete')
  },
  getAIGeneratedTaskStatus(classroomId, taskId) {
    return ajax(`classroom/${classroomId}/ai/generated-problems/task-status/${taskId}/`, 'get')
  },
  promoteAIGeneratedProblem(classroomId, problemId) {
    return ajax(`classroom/${classroomId}/ai/generated-problems/${problemId}/promote/`, 'post')
  },
  exportReviewedAIGeneratedProblems(classroomId) {
    return ajax(`classroom/${classroomId}/ai/generated-problems/export-reviewed-json/`, 'get')
  },
  getAIGeneratedKcOptions(classroomId) {
    return ajax(`classroom/${classroomId}/ai/generated-problems/kc-options/`, 'get')
  },
  validateAIGeneratedProblem(classroomId, problemId) {
    return ajax(`classroom/${classroomId}/ai/generated-problems/${problemId}/validate/`, 'post')
  },
  reviewPassAIGeneratedProblem(classroomId, problemId, data = {}) {
    return ajax(`classroom/${classroomId}/ai/generated-problems/${problemId}/review-pass/`, 'post', { data })
  },
  reviewRejectAIGeneratedProblem(classroomId, problemId, data = {}) {
    return ajax(`classroom/${classroomId}/ai/generated-problems/${problemId}/review-reject/`, 'post', { data })
  },

  createCollaborationSession(classroomId, data) {
    return ajax(`classroom/${classroomId}/sessions/`, 'post', { data })
  },
  getCollaborationSessions(classroomId, params) {
    return ajax(`classroom/${classroomId}/sessions/`, 'get', { params })
  },
  getCollaborationSession(classroomId, sessionId) {
    return ajax(`classroom/${classroomId}/sessions/${sessionId}/`, 'get')
  },
  endCollaborationSession(classroomId, sessionId) {
    return ajax(`classroom/${classroomId}/sessions/${sessionId}/end/`, 'post')
  },
  deleteCollaborationSession(classroomId, sessionId) {
    const endpoint = `classroom/${classroomId}/sessions/${sessionId}/`
    return ajax(endpoint, 'delete').catch(err => {
      // 部分反向代理会拦截 DELETE，会话删除需按后端约定降级为 POST。
      if (err && err.response && err.response.status === 405) {
        return ajax(endpoint, 'post')
      }
      return Promise.reject(err)
    })
  },
  transferRelayToken(classroomId, sessionId, data) {
    return ajax(`classroom/${classroomId}/sessions/${sessionId}/transfer-token/`, 'post', { data })
  },

  getClassroomAssignments(classroomId, params) {
    return ajax(`classroom/${classroomId}/assignments/`, 'get', { params })
  },
  createClassroomAssignment(classroomId, data) {
    return ajax(`classroom/${classroomId}/assignments/`, 'post', { data })
  },
  getClassroomAssignment(classroomId, assignmentId) {
    return ajax(`classroom/${classroomId}/assignments/${assignmentId}/`, 'get')
  },
  updateClassroomAssignment(classroomId, assignmentId, data) {
    return ajax(`classroom/${classroomId}/assignments/${assignmentId}/`, 'put', { data })
  },
  deleteClassroomAssignment(classroomId, assignmentId) {
    return ajax(`classroom/${classroomId}/assignments/${assignmentId}/`, 'delete')
  },
  submitAssignment(classroomId, assignmentId, data) {
    return ajax(`classroom/${classroomId}/assignments/${assignmentId}/submit/`, 'post', { data })
  },
  getAssignmentSubmissions(classroomId, assignmentId) {
    return ajax(`classroom/${classroomId}/assignments/${assignmentId}/submissions/`, 'get')
  },
  getAssignmentStats(classroomId, assignmentId) {
    return ajax(`classroom/${classroomId}/assignments/${assignmentId}/stats/`, 'get')
  },
  gradeAssignmentProblem(classroomId, assignmentId, detailId, data) {
    return ajax(`classroom/${classroomId}/assignments/${assignmentId}/grade/${detailId}/`, 'put', { data })
  },
  previewClassroomAssignmentSmartCompose(classroomId, data) {
    return ajax(`classroom/${classroomId}/assignments/preview-smart-compose/`, 'post', { data })
  },

  getStudentSnapshots(classroomId, params) {
    return ajax(`classroom/${classroomId}/monitor/snapshots/`, 'get', { params })
  },
  getMonitoringStats(classroomId) {
    return ajax(`classroom/${classroomId}/monitor/stats/`, 'get')
  },
  getCodePlayback(classroomId, params) {
    return ajax(`classroom/${classroomId}/monitor/playback/`, 'get', { params })
  },

  getClassErrorClusters(classroomId, timeWindowMinutes = 1440) {
    return ajax(`classroom/${classroomId}/monitor/error-clusters/`, 'get', {
      params: { time_window: timeWindowMinutes }
    })
  },
  getInterventionCandidates(classroomId, timeWindowMinutes = 30) {
    return ajax(`classroom/${classroomId}/monitor/intervention-candidates/`, 'get', {
      params: { time_window: timeWindowMinutes }
    })
  },

  getWeeklyPulse(classroomId, range = 'week') {
    return ajax(`classroom/${classroomId}/analytics/weekly-pulse`, 'get', { params: { range } })
  },
  getKcHeatmap(classroomId) {
    return ajax(`classroom/${classroomId}/analytics/kc-heatmap`, 'get')
  },
  getWeakKcSuggestions(classroomId) {
    return ajax(`classroom/${classroomId}/analytics/weak-kc-suggestions`, 'get')
  },
  getRiskStudents(classroomId) {
    return ajax(`classroom/${classroomId}/analytics/risk-students`, 'get')
  },
  getWeeklyReport(classroomId) {
    return ajax(`classroom/${classroomId}/analytics/weekly-report`, 'get')
  },
  getRiskStudentAdvice(classroomId, userId) {
    return ajax(`classroom/${classroomId}/analytics/risk-students/${userId}/advice`, 'get')
  },
  getStudentProfile(classroomId, userId) {
    return ajax(`classroom/${classroomId}/analytics/student/${userId}/profile`, 'get')
  },
  getCoursewareUsage(classroomId) {
    return ajax(`classroom/${classroomId}/analytics/courseware-usage`, 'get')
  }
}
