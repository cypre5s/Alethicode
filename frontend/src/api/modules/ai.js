import ojApi from '@/pages/oj/api'
import adminApi from '@/pages/admin/api'

function pick(api, names) {
  return names.reduce((acc, name) => {
    acc[name] = (...args) => api[name](...args)
    return acc
  }, {})
}

export const ojAiApi = pick(ojApi, [
  'requestAIGuidance',
  'getAIGuidanceResult',
  'getAIGuidance',
  'getAITutorTaskStatus',
  'getAITaskStatus',
  'getAISession',
  'getSkillRadar',
  'getPracticeHeatmap',
  'requestErrorAttribution',
  'analyzeAntiPatterns',
  'reportEvalFeedback',
  'submitEvalFeedback',
  'submitSafetyFeedback',
  'getLearnerNotebook',
  'addLearnerNotebookEntry',
  'updateLearnerNotebookEntry',
  'deleteLearnerNotebookEntry',
  'exportLearnerNotebook',
  'analyzeFrustration',
  'recordFrustrationEvent',
  'sendFrustrationAlert',
  'tutorWorkflowCreateSession',
  'tutorWorkflowGetSession',
  'tutorWorkflowDeleteSession',
  'tutorWorkflowCreateRun',
  'tutorWorkflowGetCheckpoints',
  'tutorWorkflowRestoreCheckpoint',
  'tutorWorkflowRespondInterrupt',
  'submitCodeSnapshot',
  'submitLearningEventsBatch',
  'getReviewDue',
  'createReviewPackage',
  'getReviewPackages',
  'getReviewPackage',
  'getMyMisconceptions',
  'preflightCheck',
  'calibrationStatus',
  'calibrationAnswer',
  'calibrationSkip',
  'getKnowledgeGraph',
  'getKnowledgeGraphSnapshot',
  'getKCDetail',
  'getSubmissionRiver'
  ,
  'getLanguagePackQaPacks',
  'createLanguagePackQaSession',
  'getLanguagePackQaSessions',
  'getLanguagePackQaMessages',
  'sendLanguagePackQaMessage',
  'submitLanguagePackQaFeedback',
  'getLanguagePackQaCitationPage',
  'getLanguagePackQaPreviewUrl'
])

export const adminAiApi = pick(adminApi, [
  'getAIVariantList',
  'approveAIVariant',
  'rejectAIVariant',
  'getKCList',
  'updateKC',
  'getKCProblems',
  'getMcMiningPending',
  'mcMiningApprove',
  'mcMiningReject',
  'mcMiningMerge',
  'mcMiningDiscover',
  'getPreflightStats',
  'preflightDiagnose'
])

export default {
  oj: ojAiApi,
  admin: adminAiApi
}
