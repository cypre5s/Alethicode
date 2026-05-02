import ojApi from '@/pages/oj/api'
import adminApi from '@/pages/admin/api'

function pick(api, names) {
  return names.reduce((acc, name) => {
    acc[name] = (...args) => api[name](...args)
    return acc
  }, {})
}

export const ojClassroomApi = pick(ojApi, [
  'getClassroomList',
  'createClassroom',
  'getClassroom',
  'updateClassroom',
  'deleteClassroom',
  'getClassroomStats',
  'generateInvitation',
  'joinClassroom',
  'getInvitationList',
  'deactivateInvitation',
  'getClassroomMembers',
  'promoteClassroomMember',
  'demoteClassroomMember',
  'removeClassroomMember',
  'getClassroomProblems',
  'getClassroomProblem',
  'addClassroomProblem',
  'updateClassroomProblem',
  'removeClassroomProblem',
  'importObjectiveQuestions',
  'exportObjectiveQuestions',
  'getLessonList',
  'uploadLesson',
  'getLesson',
  'deleteLesson',
  'generateProblemFromLesson',
  'getAIGeneratedProblems',
  'getAIGeneratedProblem',
  'updateAIGeneratedProblem',
  'publishAIGeneratedProblem',
  'deleteAIGeneratedProblem',
  'getAIGeneratedTaskStatus',
  'promoteAIGeneratedProblem',
  'exportReviewedAIGeneratedProblems',
  'validateAIGeneratedProblem',
  'reviewPassAIGeneratedProblem',
  'reviewRejectAIGeneratedProblem',
  'getAIGeneratedKcOptions',
  'createCollaborationSession',
  'getCollaborationSessions',
  'getCollaborationSession',
  'endCollaborationSession',
  'deleteCollaborationSession',
  'transferRelayToken',
  'getClassroomAssignments',
  'createClassroomAssignment',
  'getClassroomAssignment',
  'updateClassroomAssignment',
  'deleteClassroomAssignment',
  'submitAssignment',
  'getAssignmentSubmissions',
  'gradeAssignmentProblem',
  'previewClassroomAssignmentSmartCompose',
  'getStudentSnapshots',
  'getMonitoringStats',
  'getCodePlayback',
  'getClassErrorClusters',
  'getInterventionCandidates'
])

export const adminClassroomApi = pick(adminApi, [
  'getClassroomChapterOverview'
])

export default {
  oj: ojClassroomApi,
  admin: adminClassroomApi
}
