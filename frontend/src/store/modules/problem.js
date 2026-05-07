/**
 * 集中管理题目页共享状态，避免 Problem.vue 继续承载跨面板状态。
 */
import storage from '@/utils/storage'
import { buildProblemCodeKey } from '@/utils/constants'

const state = {
  problem: {
    title: '',
    description: '',
    hint: '',
    my_status: '',
    template: {},
    languages: [],
    created_by: { username: '' },
    tags: [],
    io_mode: { io_mode: 'Standard IO', input: 'input.txt', output: 'output.txt' }
  },
  code: '',
  language: '',
  theme: 'solarized',
  submissionId: '',
  submitting: false,
  submitted: false,
  result: { result: 9 },
  statusVisible: false,
  submissionExists: false,

  drawerVisible: false,
  chatHistory: [],
  loadingAI: false,
  aiStage: 'DISCOVERY',
  userInputMessage: '',

  activeTab: 'chat',
  analysisLoading: false,
  analysisData: null,
  antiPatterns: [],
  attributionPath: null,

  debugging: false,
  debugInput: '',
  debugOutput: '',
  debugError: false,

  diagnosing: false,
  diagnosisResult: null,
  consecutiveErrors: 0
}

const getters = {
  isObjectiveProblem: (state, getters) => {
    const qType = getters.objectiveQuestionType
    return qType === 'choice' || qType === 'fill_blank'
  },
  objectivePayload: (state) => {
    const stat = state.problem && state.problem.statistic_info
    const payload = stat && stat.objective_question
    return payload && typeof payload === 'object' ? payload : null
  },
  objectiveQuestionType: (state, getters) => {
    if (getters.objectivePayload && getters.objectivePayload.question_type) {
      return getters.objectivePayload.question_type
    }
    return ''
  },
  stageLabel: (state) => {
    const map = {
      DISCOVERY: 'Phase 1: Discovery & Logic',
      HINTING: 'Phase 2: Hints & Strategy',
      EXPLANATION: 'Phase 3: Code Implementation'
    }
    return map[state.aiStage] || ''
  }
}

const mutations = {
  SET_PROBLEM (state, problem) { state.problem = problem },
  SET_CODE (state, code) { state.code = code },
  SET_LANGUAGE (state, lang) { state.language = lang },
  SET_THEME (state, theme) { state.theme = theme },
  SET_SUBMISSION_ID (state, id) { state.submissionId = id },
  SET_SUBMITTING (state, val) { state.submitting = val },
  SET_SUBMITTED (state, val) { state.submitted = val },
  SET_RESULT (state, result) { state.result = result },
  SET_STATUS_VISIBLE (state, val) { state.statusVisible = val },
  SET_SUBMISSION_EXISTS (state, val) { state.submissionExists = val },
  SET_DRAWER_VISIBLE (state, val) { state.drawerVisible = val },
  TOGGLE_DRAWER (state) { state.drawerVisible = !state.drawerVisible },
  ADD_CHAT_MESSAGE (state, msg) { state.chatHistory.push(msg) },
  SET_LOADING_AI (state, val) { state.loadingAI = val },
  SET_AI_STAGE (state, stage) { state.aiStage = stage },
  SET_USER_INPUT (state, val) { state.userInputMessage = val },
  SET_ACTIVE_TAB (state, tab) { state.activeTab = tab },
  SET_ANALYSIS_LOADING (state, val) { state.analysisLoading = val },
  SET_ANALYSIS_DATA (state, data) { state.analysisData = data },
  SET_ANTI_PATTERNS (state, patterns) { state.antiPatterns = patterns },
  SET_ATTRIBUTION_PATH (state, path) { state.attributionPath = path },
  SET_DEBUGGING (state, val) { state.debugging = val },
  SET_DEBUG_INPUT (state, val) { state.debugInput = val },
  SET_DEBUG_OUTPUT (state, val) { state.debugOutput = val },
  SET_DEBUG_ERROR (state, val) { state.debugError = val },
  SET_DIAGNOSING (state, val) { state.diagnosing = val },
  SET_DIAGNOSIS_RESULT (state, val) { state.diagnosisResult = val },
  SET_CONSECUTIVE_ERRORS (state, val) { state.consecutiveErrors = val },
  INCREMENT_CONSECUTIVE_ERRORS (state) { state.consecutiveErrors++ },
  RESET_CONSECUTIVE_ERRORS (state) { state.consecutiveErrors = 0 },
  UPDATE_CHAT_DISPLAY (state, { index, content }) {
    if (state.chatHistory[index]) {
      state.chatHistory[index].displayContent = content
    }
  }
}

const actions = {
  saveCodeToStorage ({ state }) {
    storage.set(
      buildProblemCodeKey(state.problem._id),
      { code: state.code, language: state.language }
    )
  },
  loadCodeFromStorage ({ commit }, { problemID }) {
    const saved = storage.get(buildProblemCodeKey(problemID))
    if (saved) {
      if (typeof saved.language === 'string' && saved.language) {
        commit('SET_LANGUAGE', saved.language)
      }
      if (typeof saved.code === 'string') {
        commit('SET_CODE', saved.code)
      }
    }
    return saved
  }
}

export default {
  namespaced: true,
  state,
  getters,
  mutations,
  actions
}
