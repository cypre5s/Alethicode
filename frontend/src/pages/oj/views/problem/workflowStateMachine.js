import api from '@oj/api'
import { buildWebSocketUrl } from '@/utils/websocketUrl'
import { CARD_TYPES, PHASES } from './agentContracts'
import {
  workflowChatCacheKey,
  persistAgentMessagesCache,
  readAgentMessagesCache,
  readCachedSessionId,
  mergeAgentMessagesCache,
  clearAgentMessagesCache
} from './workflowCache'
import {
  createWorkflowSessionQueryClient,
  fetchWorkflowSessionSnapshot,
  setWorkflowSessionSnapshot,
  removeWorkflowSessionSnapshot,
  fetchWorkflowCheckpoints,
  removeWorkflowCheckpoints
} from './workflowServerState'
import {
  normalizeRuntimeEvent,
  assertAllowedForProblemPage,
  SERVER_EVENTS
} from '@/utils/runtimeContract'

const STATES = [...PHASES]

const EVENT_MAP = {
  1: 'READING',
  2: 'IDEATING',
  3: 'CODING',
  4: 'ERROR_FEEDBACK',
  5: 'AC_REVIEW',
  6: 'TRANSFER'
}

const ENABLE_WORKFLOW_WS = true
// 看门狗要早于后端 10 分钟上限暴露失败，同时覆盖常见慢任务。
const WS_RESULT_WATCHDOG_DELAY_MS = 6000
const WS_RESULT_WATCHDOG_RETRY_DELAY_MS = 1000
const WS_RESULT_WATCHDOG_MAX_RETRY = 30

const EVENT_OUTPUT_KEY = {
  READING: 'problem_guide',
  IDEATING: 'ideate',
  SKELETON: 'skeleton_code',
  ERROR_FEEDBACK: 'error_diagnosis',
  AC_REVIEW: 'post_ac',
  TRANSFER: 'transfer',
  CHAT: 'chat',
  KNOWLEDGE_REVIEW: 'knowledge_review',
  VISUALIZE: 'visualize',
  PARSONS: 'parsons'
}

const EVENT_MSG_TYPE = {
  READING: CARD_TYPES[0],
  IDEATING: CARD_TYPES[1],
  SKELETON: CARD_TYPES[2],
  ERROR_FEEDBACK: CARD_TYPES[3],
  AC_REVIEW: CARD_TYPES[4],
  TRANSFER: CARD_TYPES[5],
  CHAT: CARD_TYPES[6],
  KNOWLEDGE_REVIEW: CARD_TYPES[8],
  VISUALIZE: CARD_TYPES[9],
  PARSONS: CARD_TYPES[10]
}

function isHiddenTutorAction(action = {}) {
  const normalizedEvent = String(action.event || '').toUpperCase()
  const normalizedKey = String(action.key || '').trim().toLowerCase()
  const normalizedLabel = String(action.label || '').trim()
  return normalizedEvent === 'CODING' ||
    normalizedKey === 'coding' ||
    normalizedLabel === '开始编码' ||
    normalizedLabel === '编码'
}

export default {
  beforeUnmount() {
    this._persistAgentMessagesCache()
    this._clearWsResultWatchdog()
    this._disconnectWorkflowWs()
  },

  data() {
    return {
      workflowContext: {
        problem_id: null,
        session_id: null,
        thread_id: null,
        submissionId: null,
        current_state: 'READING',
        state_history: [],
        problemGuide: null,
        ideateResult: null,
        codeIssues: [],
        lastCodeSnapshot: '',
        diagnosisHistory: [],
        consecutiveErrors: 0,
        acReview: null,
        transferProblem: null,
        submissionCount: 0,
        editFrequency: 0,
        dwellTime: 0,
        deleteRatio: 0
      },
      agentPanelVisible: false,
      agentMessages: [],
      agentLoading: false,
      agentInputMode: 'chat',
      agentUserInput: '',
      // ModeBar 展示后端同步的 ConversationMode，CHAT 事件也复用该值。
      activeConversationMode: 'reading',
      lastConversationCards: [],
      pendingHumanAction: '',
      backendAvailableActions: null,
      contextUsage: { tokens_used: 0, tokens_limit: 0, model_name: '', last_updated: null },
      _activeAbortController: null,
      workflowCheckpoints: [],
      planId: null,
      planSteps: [],
      planReasoning: '',
      planPaused: false,
      planCompleted: false,
      planSurrendered: false,
      planRecommendation: null,
      _planRecommendationDismissedUntil: 0,
      autonomyLevel: 'passive',
      _wsConnection: null,
      _wsReconnectTimer: null,
      _wsReadyPromise: null,
      _wsResultWatchdogTimer: null,
      _wsResultWatchdogContext: null,
      _workflowSessionQueryClient: createWorkflowSessionQueryClient(),
      runtimeContext: {
        sessionId: null,
        taskId: null,
        checkpointId: null,
        traceId: null,
        runtimeState: null,
        serverEvent: null,
        approvalState: null,
        failureBucket: null,
        lastError: null,
        updatedAt: null
      }
    }
  },

  computed: {
    quickActions() {
      const ICON_MAP = {
        problem_guide: 'Reading',
        ideate: 'Sunny',
        re_read: 'Reading',
        re_ideate: 'Sunny',
        coding: 'Monitor',
        error_chain: 'Warning',
        ac_review: 'StarFilled',
        transfer: 'Sort',
        visualize: 'DArrowRight',
        parsons: 'Grid',
        knowledge_review: 'Collection',
        skeleton: 'Document'
      }

      const hasPostAcCard = this.agentMessages.some(m => m.type === 'post_ac')
      const bootstrapActions = [
        { key: 'problem_guide', label: '题目导读', agentId: 1, event: 'READING', icon: 'Reading' },
        { key: 'ideate', label: '思路分析', agentId: 2, event: 'IDEATING', icon: 'Sunny' }
      ]

      if (Array.isArray(this.backendAvailableActions) && this.backendAvailableActions.length > 0) {
        return this.filterWorkflowActions(this.backendAvailableActions
          .filter(a => !(a.key === 'ac_review' && hasPostAcCard))
          .map(a => ({
            key: a.key,
            label: a.label,
            agentId: a.agent_id,
            event: a.event,
            icon: ICON_MAP[a.key] || 'Lightning'
          })))
      }

      if (!this.workflowContext.session_id) {
        return bootstrapActions
      }

      return []
    },

    inputPlaceholder() {
      return this.agentInputMode === 'ideate'
        ? '描述你的解题思路...'
        : '输入消息与 Agent 对话...'
    }
  },

  methods: {
    _workflowChatCacheKey(problemId) {
      const id = problemId || this.problemID || (this.workflowContext && this.workflowContext.problem_id)
      return workflowChatCacheKey(id)
    },

    _sendWorkflowMachineEvent(type, payload = {}) {
      const eventType = String(type || '').toUpperCase()
      const hasPhase = typeof payload.phase === 'string' && STATES.includes(String(payload.phase).toUpperCase())
      const hasRuntimeState = payload.runtimeState !== undefined
      const hasPlanSyncPayload = payload.planState !== undefined || payload.planPaused !== undefined

      if (hasPhase) {
        this.workflowContext.current_state = String(payload.phase).toUpperCase()
      }

      if (hasRuntimeState) {
        this.runtimeContext = {
          ...this.runtimeContext,
          runtimeState: payload.runtimeState
        }
      }

      if (eventType === 'RESET' || eventType === 'CLEAR') {
        this.planPaused = false
        this.planCompleted = false
      } else if (hasPlanSyncPayload || eventType === 'PLAN_SYNC') {
        const planState = String(payload.planState || '').toLowerCase()
        if (planState === 'plan_paused') {
          this.planPaused = true
          this.planCompleted = false
        } else if (planState === 'plan_completed') {
          this.planPaused = false
          this.planCompleted = true
        } else if (planState === 'plan_active' || planState === 'idle') {
          this.planPaused = false
          this.planCompleted = false
        } else if (payload.planPaused !== undefined) {
          this.planPaused = !!payload.planPaused
          if (payload.planPaused) {
            this.planCompleted = false
          }
        }
      }

      const lifecycleState = payload.lifecycleState
        || this._deriveWorkflowLifecycleState(
          hasRuntimeState ? payload.runtimeState : this.runtimeContext.runtimeState,
          payload
        )
      this.agentLoading = lifecycleState === 'running' || lifecycleState === 'restoring' || lifecycleState === 'ws_connecting'
    },

    _deriveWorkflowLifecycleState(runtimeState = this.runtimeContext.runtimeState, options = {}) {
      if (options.cleared) {
        return 'cleared'
      }
      if (options.wsConnecting) {
        return 'ws_connecting'
      }
      if (!this.workflowContext.session_id && !options.hasSession) {
        return 'session_bootstrap'
      }
      if (runtimeState === 'WAITING_HUMAN_APPROVAL' || this.pendingHumanAction) {
        return 'waiting_human_approval'
      }
      if (runtimeState === 'RESTORING' || options.restoring) {
        return 'restoring'
      }
      if (runtimeState === 'FAILED' || options.failed) {
        return 'failed'
      }
      if (runtimeState === 'QUEUED' || runtimeState === 'RUNNING' || runtimeState === 'WAITING_TOOL' || options.loading) {
        return 'running'
      }
      return 'ready'
    },

    _derivePlanState(plan = null) {
      const source = plan && typeof plan === 'object'
        ? plan
        : {
            status: this.planCompleted
              ? 'completed'
              : (this.planPaused ? 'paused' : (this.planSteps.length ? 'active' : 'idle'))
          }
      const status = String(source.status || '').toLowerCase()
      if (status === 'paused' || source.paused) {
        return 'plan_paused'
      }
      if (status === 'completed' || source.completed || source.surrendered) {
        return 'plan_completed'
      }
      if (status === 'active' || status === 'in_progress' || status === 'recommended' || (Array.isArray(source.steps) && source.steps.length > 0)) {
        return 'plan_active'
      }
      return 'idle'
    },

    _syncWorkflowSnapshotToMachine(payload = {}) {
      this._sendWorkflowMachineEvent('SYNC_SNAPSHOT', {
        phase: payload.phase || this.workflowContext.current_state,
        planPaused: payload.planPaused !== undefined ? payload.planPaused : this.planPaused,
        planState: payload.planState || this._derivePlanState(),
        runtimeState: payload.runtimeState !== undefined ? payload.runtimeState : this.runtimeContext.runtimeState,
        lifecycleState: payload.lifecycleState || this._deriveWorkflowLifecycleState(payload.runtimeState, payload)
      })
    },

    _persistAgentMessagesCache(problemId) {
      const key = this._workflowChatCacheKey(problemId)
      const sessionId = this.workflowContext && this.workflowContext.session_id ? this.workflowContext.session_id : null
      persistAgentMessagesCache(key, sessionId, this.agentMessages)
    },

    _readAgentMessagesCache(problemId, expectedSessionId = null) {
      const key = this._workflowChatCacheKey(problemId)
      return readAgentMessagesCache(key, expectedSessionId)
    },

    _restoreAgentMessagesCache(problemId, expectedSessionId = null) {
      const messages = this._readAgentMessagesCache(problemId, expectedSessionId)
      if (!messages) return false
      this.agentMessages = messages
      return true
    },

    _mergeAgentMessagesCache(problemId, expectedSessionId = null) {
      const cachedMessages = this._readAgentMessagesCache(problemId, expectedSessionId)
      const merged = mergeAgentMessagesCache(this.agentMessages, cachedMessages)
      if (!merged) return false
      this.agentMessages = merged
      return true
    },

    _clearAgentMessagesCache(problemId) {
      const key = this._workflowChatCacheKey(problemId)
      clearAgentMessagesCache(key)
    },

    async initWorkflowSession(problemId) {
      this._sendWorkflowMachineEvent('SESSION_BOOTSTRAP', {
        phase: this.workflowContext.current_state,
        runtimeState: this.runtimeContext.runtimeState,
        lifecycleState: 'session_bootstrap'
      })
      this.workflowContext.problem_id = problemId
      if (!this.workflowContext.session_id) {
        const cachedSid = readCachedSessionId(this._workflowChatCacheKey(problemId))
        if (cachedSid) {
          this.workflowContext.session_id = cachedSid
        }
      }
      const preRestoreCache = this._readAgentMessagesCache(problemId)
      try {
        const restored = await this.restoreWorkflowSession(problemId)
        if (restored) {
          if (Array.isArray(preRestoreCache) && preRestoreCache.length > 0) {
            const merged = mergeAgentMessagesCache(preRestoreCache, this.agentMessages)
            if (merged) {
              this.agentMessages = merged
            }
          }
          if (!this.agentMessages.length && Array.isArray(preRestoreCache) && preRestoreCache.length > 0) {
            this.agentMessages = preRestoreCache
          }
          this._persistAgentMessagesCache(problemId)
          return
        }
        await this.createFreshWorkflowSession(problemId)
      } catch (e) {
        this.workflowContext.session_id = null
        this.workflowContext.thread_id = null
        console.warn('[workflow] session init failed', e)
        this.pushAgentMessage({
          type: 'error',
          content: this._formatWorkflowInitError(e)
        })
        this._persistAgentMessagesCache(problemId)
      }
    },

    /**
     * @param {unknown} error 会话创建/恢复阶段抛出的异常
     * @returns {string} 面向用户的中文错误提示
     */
    _formatWorkflowInitError(error) {
      const response = error && error.response ? error.response : null
      const status = response ? response.status : 0
      const payload = response && response.data ? response.data : null
      const backendMessage = payload && typeof payload === 'object'
        ? (payload.error || (payload.data && payload.data.error) || null)
        : null
      if (status === 503 || (typeof backendMessage === 'string' && /tutor-graph/i.test(backendMessage))) {
        return 'AI 导学服务暂不可用（tutor-graph 未启动或连接失败），请稍后重试或联系管理员'
      }
      if (typeof backendMessage === 'string' && backendMessage.trim()) {
        return `AI 导学会话初始化失败：${backendMessage}`
      }
      return 'AI 导学会话初始化失败，请稍后重试'
    },

    _resolveProblemLanguage() {
      if (this.language) return this.language
      if (this.problem && Array.isArray(this.problem.languages) && this.problem.languages.length > 0) {
        return this.problem.languages[0]
      }
      return ''
    },

    _resolveTutorSessionContext(problemId) {
      const route = this.$route || (this.$root && this.$root.$route) || null
      const query = (route && route.query) || {}
      const isAssignment = query.from === 'assignment' || (query.classroom_id && query.assignment_id)
      if (!isAssignment) return null
      const allowed = (() => {
        if (typeof query.ai_tutor_allowed === 'undefined') return true
        const v = String(query.ai_tutor_allowed).toLowerCase()
        return v === '1' || v === 'true' || v === 'yes'
      })()
      const antiCheating = (() => {
        if (typeof query.anti_cheating === 'undefined') return false
        const v = String(query.anti_cheating).toLowerCase()
        return v === '1' || v === 'true' || v === 'yes'
      })()
      return {
        source: 'classroom_assignment',
        classroom_id: query.classroom_id || '',
        assignment_id: query.assignment_id || '',
        problem_id: problemId,
        allow_ai_tutor: allowed,
        anti_cheating: antiCheating
      }
    },

    async createFreshWorkflowSession(problemId) {
      const language = this._resolveProblemLanguage()
      if (!language) {
        this.pushAgentMessage({ type: 'error', content: '无法确定题目语言，请稍后重试或检查题目配置' })
        throw new Error('tutor workflow session requires a language; neither editor language nor problem.languages was available')
      }
      const sessionPayload = { problem_id: problemId, language }
      const tutorContext = this._resolveTutorSessionContext(problemId)
      if (tutorContext) {
        sessionPayload.context = tutorContext
      }
      const res = await api.tutorWorkflowCreateSession(sessionPayload)
      const data = res.data && res.data.data !== undefined ? res.data.data : res.data
      if (!data || !data.session_id) {
        throw new Error('tutor-graph returned no session_id on createSession')
      }
      this._applySessionSnapshot(data, problemId)
      if (data.available_actions !== undefined) {
        this.backendAvailableActions = data.available_actions
      }
      this._fetchCheckpoints()
      this._persistAgentMessagesCache(problemId)
    },

    _cancelActiveWorkflowRequest() {
      if (this._activeAbortController) {
        this._activeAbortController.abort()
        this._activeAbortController = null
      }
      this._clearWsResultWatchdog()
      this._sendWorkflowMachineEvent('RUN_SETTLED', {
        phase: this.workflowContext.current_state,
        runtimeState: this.runtimeContext.runtimeState,
        lifecycleState: this._deriveWorkflowLifecycleState(this.runtimeContext.runtimeState)
      })
    },

    async restoreWorkflowSession(problemId) {
      try {
        const existingSessionId = this.workflowContext && this.workflowContext.session_id
        if (!existingSessionId) return false
        const data = await fetchWorkflowSessionSnapshot(this._workflowSessionQueryClient, existingSessionId, { silent: true })
        if (data && data.session_id) {
          this._applySessionSnapshot(data, problemId)
          if (data.available_actions !== undefined) {
            this.backendAvailableActions = data.available_actions
          }
          this._rebuildFromTrace(data)
          this._fetchCheckpoints()
          return true
        }
      } catch (e) {
        console.warn('[workflow] session restore failed', e)
        this.workflowContext.session_id = null
        clearAgentMessagesCache(this._workflowChatCacheKey(problemId))
      }
      return false
    },

    _applySessionSnapshot(data, problemId) {
      const previousSessionId = this.workflowContext.session_id
      if (data && data.session_id) {
        setWorkflowSessionSnapshot(this._workflowSessionQueryClient, data.session_id, data)
      }
      if (data && data.usage) {
        this._applyContextUsage(data.usage)
      } else if (data && data.session_id) {
        this._fetchSessionUsage(data.session_id).catch(() => {})
      }
      if (previousSessionId && data && data.session_id && previousSessionId !== data.session_id) {
        removeWorkflowSessionSnapshot(this._workflowSessionQueryClient, previousSessionId)
        removeWorkflowCheckpoints(this._workflowSessionQueryClient, previousSessionId)
      }
      this.workflowContext.session_id = data.session_id
      this.workflowContext.thread_id = data.thread_id
      if (problemId) this.workflowContext.problem_id = problemId
      this.refreshConversationContext()
      if (data.submission_id) this.workflowContext.submissionId = data.submission_id
      if (data.phase && STATES.includes(data.phase)) {
          this.workflowContext.current_state = data.phase
          this._sendWorkflowMachineEvent('PHASE_CHANGE', { phase: data.phase })
      }
      if (data.node_outputs) {
        this._syncNodeOutputs(data.node_outputs)
      }
      if (data.behavior_metrics) {
        const bm = data.behavior_metrics
        if (bm.consecutiveErrors !== undefined) this.workflowContext.consecutiveErrors = bm.consecutiveErrors
        if (bm.submissionCount !== undefined) this.workflowContext.submissionCount = bm.submissionCount
        if (bm.editFrequency !== undefined) this.workflowContext.editFrequency = bm.editFrequency
        if (bm.dwellTime !== undefined) this.workflowContext.dwellTime = bm.dwellTime
        if (bm.deleteRatio !== undefined) this.workflowContext.deleteRatio = bm.deleteRatio
        if (bm._execution_trace) this.workflowContext.executionTrace = bm._execution_trace
      }
      this.pendingHumanAction = data.pending_human_action || ''
      if (data.plan && typeof data.plan === 'object') {
        this._syncPlanProjection(data.plan, data.recommendation_reason || '')
      } else if (data.recommendation_reason) {
        this._applyPlanRecommendation({
          plan_id: data.plan_id || this.planId || '',
          status: 'recommended',
          coordination_reasoning: data.coordination_reasoning || ''
        }, data.recommendation_reason)
      }
      if (data.runtime_state || data.task_id || data.approval_state || data.failure_bucket) {
        this.runtimeContext = {
          sessionId: data.session_id || this.runtimeContext.sessionId,
          taskId: data.task_id || this.runtimeContext.taskId,
          checkpointId: data.checkpoint_id || this.runtimeContext.checkpointId,
          traceId: data.trace_id || this.runtimeContext.traceId,
          runtimeState: data.runtime_state || this.runtimeContext.runtimeState,
          serverEvent: this.runtimeContext.serverEvent,
          approvalState: data.approval_state !== undefined ? data.approval_state : this.runtimeContext.approvalState,
          failureBucket: data.failure_bucket || this.runtimeContext.failureBucket,
          lastError: data.last_error !== undefined ? this._normalizeSnapshotLastError(data.last_error) : this.runtimeContext.lastError,
          updatedAt: new Date().toISOString()
        }
      }
      this._syncWorkflowSnapshotToMachine({
        phase: this.workflowContext.current_state,
        planPaused: this.planPaused,
        runtimeState: this.runtimeContext.runtimeState
      })
      if (ENABLE_WORKFLOW_WS && this.workflowContext.session_id &&
        (this.workflowContext.session_id !== previousSessionId || !this._wsConnection)) {
        this._connectWorkflowWs()
      }
    },

    _syncPlanProjection(plan, recommendationReason = '') {
      if (!plan || typeof plan !== 'object') {
        this._clearPlanRecommendation()
        return
      }
      if (Object.keys(plan).length === 0) {
        this.planId = null
        this.planSteps = []
        this.planReasoning = ''
        this.planSurrendered = false
        this._sendWorkflowMachineEvent('PLAN_SYNC', {
          planPaused: false,
          planState: 'idle'
        })
        this._clearPlanRecommendation()
        return
      }
      const status = String(plan.status || '').toLowerCase()
      const steps = Array.isArray(plan.steps)
        ? plan.steps.slice()
        : (Array.isArray(plan.plan_steps) ? plan.plan_steps.slice() : [])

      this.planId = plan.plan_id || this.planId
      this.planSteps = steps
      this.planReasoning = plan.coordination_reasoning || plan.reasoning || ''
      this.planSurrendered = status === 'surrendered' || !!plan.surrendered
      this._sendWorkflowMachineEvent('PLAN_SYNC', {
        planPaused: status === 'paused' || !!plan.paused,
        planState: this._derivePlanState(plan)
      })

      if (status === 'recommended') {
        this._applyPlanRecommendation(plan, recommendationReason)
        return
      }

      if (status) {
        this._clearPlanRecommendation()
      }
    },

    _applyPlanRecommendation(plan, recommendationReason = '') {
      if (Date.now() < (this._planRecommendationDismissedUntil || 0)) {
        return
      }
      this.planRecommendation = {
        planId: plan.plan_id || this.planId || '',
        reason: recommendationReason || plan.recommendation_reason || '',
        coordinationReasoning: plan.coordination_reasoning || '',
        triggerSource: plan.trigger_source || 'rules',
        phase: this.getCurrentWorkflowPhase()
      }
    },

    _clearPlanRecommendation() {
      this.planRecommendation = null
    },

    _getCurrentPlanStep() {
      if (!Array.isArray(this.planSteps) || this.planSteps.length === 0) return null
      const current = this.planSteps.find(step => ['active', 'current', 'in_progress'].includes(String(step.status || '').toLowerCase()))
      if (current) return current
      const pending = this.planSteps.find(step => String(step.status || '').toLowerCase() === 'pending')
      return pending || this.planSteps[0]
    },

    _rebuildFromTrace(data) {
      const trace = data.execution_trace || []
      const outputs = data.node_outputs || {}

      if (trace.length === 0) {
        this._rebuildAgentMessages(data)
        return
      }

      const agentEntries = trace.filter(e =>
        (e.type === 'agent_output' || e._event_type === 'agent_output') && e.message_type
      )

      if (agentEntries.length === 0) {
        this._rebuildAgentMessages(data)
        return
      }

      for (const entry of agentEntries) {
        const payload = entry.payload || outputs[entry.output_key]
        if (!payload) continue
        this._pushTraceMessage(entry.message_type, payload)
      }
      this._pushVisualizeMessageIfPresent(outputs)
      this._appendChatHistoryMessages(outputs.chat, { includeAssistant: false })
    },

    _rebuildAgentMessages(data) {
      const outputs = data.node_outputs || {}
      const lastEvent = String(outputs.last_event && outputs.last_event.event ? outputs.last_event.event : '').toUpperCase()
      const cardEntries = []
      if (outputs.problem_guide) {
        cardEntries.push({ type: 'problem_guide', data: outputs.problem_guide })
      }
      if (outputs.ideate) {
        cardEntries.push({ type: 'ideate_analysis', data: outputs.ideate })
      }
      if (outputs.skeleton_code) {
        cardEntries.push({ type: 'skeleton_code', data: outputs.skeleton_code })
      }
      let embeddedErrorVisualize = false
      if (outputs.error_diagnosis) {
        const shouldInlineErrorVisualize = (!lastEvent || lastEvent === 'ERROR_FEEDBACK') &&
          !!(outputs.visualize && typeof outputs.visualize === 'object')
        const diag = Array.isArray(outputs.error_diagnosis) ? outputs.error_diagnosis : [outputs.error_diagnosis]
        diag.forEach(d => {
          cardEntries.push({
            type: 'error_diagnosis',
            data: shouldInlineErrorVisualize ? this._withInlineVisualizePayload(d, outputs) : d
          })
        })
        embeddedErrorVisualize = shouldInlineErrorVisualize
      }
      if (outputs.execution_trace_explainer) {
        cardEntries.push({ type: 'execution_trace_explainer', data: outputs.execution_trace_explainer })
      }
      if (outputs.post_ac) {
        cardEntries.push({ type: 'post_ac', data: outputs.post_ac })
      }
      if (outputs.transfer) {
        cardEntries.push({ type: 'transfer_problem', data: outputs.transfer })
      }
      if (outputs.knowledge_review) {
        cardEntries.push({ type: 'knowledge_review', data: outputs.knowledge_review })
      }
      if (outputs.visualize && !embeddedErrorVisualize) {
        cardEntries.push({ type: 'visualize', data: outputs.visualize })
      }
      if (outputs.parsons) {
        cardEntries.push({ type: 'parsons_problem', data: outputs.parsons })
      }

      const lastEventType = EVENT_MSG_TYPE[lastEvent] || ''
      const orderedCards = lastEventType
        ? [
            ...cardEntries.filter(entry => entry.type !== lastEventType),
            ...cardEntries.filter(entry => entry.type === lastEventType)
          ]
        : cardEntries
      orderedCards.forEach((entry) => this.pushAgentMessage(entry))
      this._appendChatHistoryMessages(outputs.chat)
    },

    _appendChatHistoryMessages(chatPayload, options = {}) {
      const includeAssistant = options.includeAssistant !== false
      if (!chatPayload || !Array.isArray(chatPayload.history)) return
      chatPayload.history.forEach(entry => {
        if (!entry || !entry.role) return
        if (entry.role === 'user') {
          this.pushAgentMessage({ type: 'user', content: entry.content || '' })
          return
        }
        if (includeAssistant && entry.role === 'assistant') {
          this.pushAgentMessage({ type: 'ai_reply', content: entry.content || '' })
        }
      })
    },

    _pushExecutionTraceExplainerIfPresent(nodeOutputs) {
      const outputs = nodeOutputs || {}
      if (!outputs.execution_trace_explainer) return false
      const alreadyExists = this.agentMessages.some(
        m => m.type === 'execution_trace_explainer'
      )
      if (alreadyExists) return false
      this.pushAgentMessage({ type: 'execution_trace_explainer', data: outputs.execution_trace_explainer })
      return true
    },

    _withInlineVisualizePayload(payload, nodeOutputs) {
      const visualize = nodeOutputs && nodeOutputs.visualize
      if (!visualize || typeof visualize !== 'object' || !payload || typeof payload !== 'object') {
        return payload
      }
      return { ...payload, visualize }
    },

    _shouldInlineVisualize(normalizedEvent, nodeOutputs) {
      return normalizedEvent === 'ERROR_FEEDBACK' &&
        !!(nodeOutputs && nodeOutputs.visualize && typeof nodeOutputs.visualize === 'object')
    },

    _pushVisualizeMessageIfPresent(nodeOutputs) {
      const outputs = nodeOutputs || {}
      const payload = outputs.visualize
      if (!payload || typeof payload !== 'object') return false
      const serialized = JSON.stringify(payload)
      const exists = this.agentMessages.some(
        m => m.type === 'visualize' && JSON.stringify(m.data || {}) === serialized
      )
      if (exists) return false
      this.pushAgentMessage({ type: 'visualize', data: payload })
      return true
    },

    _pushTraceMessage(messageType, payload) {
      const normalizedType = this._normalizeTraceMessageType(messageType)
      if (!normalizedType) return false

      if (normalizedType === 'ai_reply') {
        const content = payload && typeof payload === 'object' ? payload.content : payload
        this.pushAgentMessage({ type: 'ai_reply', content: content || '' })
        return true
      }
      this.pushAgentMessage({ type: normalizedType, data: payload })
      return true
    },

    _syncNodeOutputs(outputs) {
      if (outputs.problem_guide) this.workflowContext.problemGuide = outputs.problem_guide
      if (outputs.ideate) this.workflowContext.ideateResult = outputs.ideate
      if (outputs.error_diagnosis) {
        this.workflowContext.diagnosisHistory = Array.isArray(outputs.error_diagnosis)
          ? outputs.error_diagnosis
          : [outputs.error_diagnosis]
      }
      if (outputs.post_ac) this.workflowContext.acReview = outputs.post_ac
      if (outputs.transfer) this.workflowContext.transferProblem = outputs.transfer
    },

    getCurrentWorkflowPhase() {
      const currentState = String((this.workflowContext && this.workflowContext.current_state) || 'READING').toUpperCase()
      return STATES.includes(currentState) ? currentState : 'READING'
    },

    buildWorkflowActionPayload(action = {}) {
      const normalizedKey = String(action.key || '').trim()
      const normalizedEvent = String(action.event || '').toUpperCase()
      const problemId = this.workflowContext.problem_id || this.problemID || (this.problem && this.problem.id) || null
      const submissionId = this.workflowContext.submissionId || this.submissionId || null
      const code = typeof this.code === 'string' ? this.code : ''
      const language = this._resolveProblemLanguage()

      if (normalizedKey === 'ac_review') {
        return {
          problem_id: problemId,
          submission_id: submissionId,
          code,
          language,
          guidance_level: 1
        }
      }
      if (normalizedKey === 'transfer') {
        return {
          problem_id: problemId,
          submission_id: submissionId,
          code,
          language
        }
      }
      if (normalizedKey === 'error_chain') {
        return { submission_id: submissionId, language }
      }
      if (normalizedKey === 'coding') {
        return { problem_id: problemId, code, language }
      }
      if (normalizedKey === 'visualize') {
        return {
          problem_id: problemId,
          code,
          language,
          intent: action.intent || '',
          prompt: action.prompt || '',
          context_hints: action.context_hints || {}
        }
      }
      if (
        normalizedKey === 'problem_guide' ||
        normalizedKey === 're_read' ||
        normalizedKey === 'ideate' ||
        normalizedKey === 're_ideate' ||
        normalizedKey === 'skeleton'
      ) {
        return { problem_id: problemId, language }
      }
      if (normalizedEvent === 'CHAT') {
        return {
          problem_id: problemId,
          submission_id: submissionId,
          code,
          language,
          message: ''
        }
      }
      return { problem_id: problemId, submission_id: submissionId, code, language }
    },

    isWorkflowActionAllowed(action = {}) {
      const normalizedEvent = String(action.event || '').toUpperCase()
      if (!normalizedEvent) return false
      if (isHiddenTutorAction(action)) return false
      return true
    },

    filterWorkflowActions(actions = []) {
      if (!Array.isArray(actions) || actions.length === 0) return []
      return actions.filter(action => this.isWorkflowActionAllowed(action))
    },

    isWorkflowEventAllowed(event) {
      const normalizedEvent = String(event || '').toUpperCase()
      return !!normalizedEvent
    },

    _applyContextUsage(usage) {
      if (!usage || typeof usage !== 'object') return
      const toNumber = (value) => {
        const n = Number(value)
        return Number.isFinite(n) && n >= 0 ? n : 0
      }
      this.contextUsage = {
        tokens_used: toNumber(usage.tokens_used),
        tokens_limit: toNumber(usage.tokens_limit),
        model_name: usage.model_name ? String(usage.model_name) : '',
        last_updated: usage.last_updated || null
      }
    },

    async _fetchSessionUsage(sessionId) {
      if (!sessionId) return
      const response = await api.tutorWorkflowGetSessionUsage(sessionId, { silent: true })
      const payload = response && response.data && response.data.data !== undefined
        ? response.data.data
        : (response ? response.data : null)
      this._applyContextUsage(payload)
    },

    _resolveWorkflowDispatchError(err) {
      const response = err && err.response ? err.response : null
      if (response) {
        const status = Number(response.status || 0)
        const body = response.data || {}
        const bodyError = typeof body.error === 'string' ? body.error.trim() : ''
        if (bodyError) return bodyError
        if (status === 422) return '当前操作不合法，请检查输入后重试'
        if (status === 409) return '当前会话已有进行中的任务，请稍后重试'
        if (status === 403) return '当前会话无访问权限，请刷新页面后重试'
      }
      const rawMessage = typeof (err && err.message) === 'string' ? err.message.trim() : ''
      return rawMessage || '工作流请求失败，请稍后重试'
    },

    transitionState(newState) {
      if (!STATES.includes(newState)) return
      this.workflowContext.current_state = newState
      this.workflowContext.state_history.push({
        state: newState,
        timestamp: Date.now()
      })
    },

    pushAgentMessage(msg) {
      const entry = {
        id: `msg_${Date.now()}_${Math.random().toString(36).slice(2, 9)}`,
        timestamp: Date.now(),
        ...(typeof msg === 'object' ? msg : { type: 'system', content: msg })
      }
      if (entry.type === 'error' && entry.content) {
        const last = this.agentMessages[this.agentMessages.length - 1]
        if (last && last.type === 'error' && last.content === entry.content) {
          return
        }
      }
      this.agentMessages.push(entry)
      this._persistAgentMessagesCache()
    },

    async refreshConversationContext() {
      const sessionId = this.workflowContext.session_id
      if (!sessionId) {
        this.activeConversationMode = 'reading'
        this.lastConversationCards = []
        return
      }
      try {
        const res = await api.getConversation(sessionId)
        const data = (res && res.data && res.data.data) || res?.data || null
        if (data && typeof data.active_mode === 'string') {
          this.activeConversationMode = data.active_mode
        }
        this.lastConversationCards = Array.isArray(data && data.last_cards)
          ? data.last_cards
          : []
      } catch (err) {
        this.lastConversationCards = []
      }
    },

    async switchConversationMode(targetMode) {
      const sessionId = this.workflowContext.session_id
      if (!sessionId || !targetMode) return
      try {
        const res = await api.switchConversationMode(sessionId, targetMode)
        const data = (res && res.data && res.data.data) || res?.data || null
        if (data && typeof data.active_mode === 'string') {
          this.activeConversationMode = data.active_mode
        }
      } catch (err) {
        const reason = err && err.response && err.response.data
          ? (err.response.data.error || (err.response.data.data && err.response.data.data.error) || '')
          : ''
        this.pushAgentMessage({ type: 'system', content: reason || `切换模式失败：${targetMode}` })
      }
    },

    _normalizeTraceMessageType(messageType, outputKey = '') {
      const rawType = String(messageType || '').trim()
      const normalizedRawType = rawType.toLowerCase()
      if (normalizedRawType && CARD_TYPES.includes(normalizedRawType)) return normalizedRawType
      if (normalizedRawType === 'ai_reply') return 'ai_reply'

      const normalizedOutputKey = String(outputKey || '').trim().toLowerCase()
      if (normalizedOutputKey) {
        const matchedEvent = Object.keys(EVENT_OUTPUT_KEY).find((eventKey) => EVENT_OUTPUT_KEY[eventKey].toLowerCase() === normalizedOutputKey)
        if (matchedEvent && EVENT_MSG_TYPE[matchedEvent]) {
          return EVENT_MSG_TYPE[matchedEvent]
        }
      }

      const aliasMap = {
        ideate: 'ideate_analysis',
        skeleton: 'skeleton_code',
        transfer: 'transfer_problem',
        chat: 'ai_reply',
        visualize: 'visualize'
      }
      return aliasMap[normalizedRawType] || ''
    },

    _pushExecutionTrace(executionTrace, nodeOutputs) {
      if (!executionTrace || !executionTrace.length) return 0
      let pushedCount = 0
      for (const entry of executionTrace) {
        if (entry.type === 'agent_output' && entry.message_type) {
          const normalizedMessageType = this._normalizeTraceMessageType(entry.message_type, entry.output_key)
          if (!normalizedMessageType) continue
          const payload = entry.payload || (entry.output_key && nodeOutputs[entry.output_key])
          if (payload) {
            if (this._pushTraceMessage(normalizedMessageType, payload)) {
              pushedCount += 1
            }
          }
        }
      }
      return pushedCount
    },
    async dispatchWorkflowEvent(event, payload = {}, options = {}) {
      if (!this.workflowContext.session_id) {
        const problemId = this.workflowContext.problem_id
          || (payload && payload.problem_id)
          || (this.problem && this.problem.id)
        if (problemId && !this._sessionRetrying) {
          this._sessionRetrying = true
          try {
            await this.createFreshWorkflowSession(problemId)
          } catch {
            // 会话创建失败时保持 null，让后续守卫统一处理。
          } finally {
            this._sessionRetrying = false
          }
        }
        if (!this.workflowContext.session_id) {
          this._sendWorkflowMachineEvent('FAILED', {
            phase: this.workflowContext.current_state,
            runtimeState: this.runtimeContext.runtimeState,
            lifecycleState: 'failed'
          })
          this.pushAgentMessage({
            type: 'error',
            content: 'AI 导学会话尚未就绪，请刷新页面或稍后重试'
          })
          return null
        }
      }
      let keepLoading = false
      try {
        if (!event) throw new Error('Workflow event is required')
        const normalizedEvent = String(event).toUpperCase()
        this._sendWorkflowMachineEvent('RUN_REQUESTED', {
          phase: this.workflowContext.current_state,
          runtimeState: this.runtimeContext.runtimeState,
          lifecycleState: 'running'
        })
        const controller = new AbortController()
        this._activeAbortController = controller
        const eventData = this._buildEventData(normalizedEvent, payload)
        if (ENABLE_WORKFLOW_WS && this.workflowContext.session_id) {
          await this._ensureWorkflowWsReady()
        }

        const resolvedLanguage = eventData.language || this._resolveProblemLanguage()
        if (!resolvedLanguage) {
          throw new Error('language is required but cannot be resolved (no current editor language and problem has no allowed languages)')
        }
        const requestPayload = {
          event: normalizedEvent,
          event_data: eventData,
          language: resolvedLanguage
        }

        const sessionId = this.workflowContext.session_id
        const res = await api.tutorWorkflowCreateRun(sessionId, requestPayload, { signal: controller.signal })

        const data = res.data && res.data.data !== undefined ? res.data.data : res.data
        if (data) {
          if (data.usage) this._applyContextUsage(data.usage)
          const isAsync = data.runtime_state === 'QUEUED' || data.status === 'dispatched'
          if (isAsync) {
            if (data.session_id) {
              this.workflowContext.session_id = data.session_id
            }
            if (data.run_id) {
              this.runtimeContext.taskId = data.run_id
            }
            if (ENABLE_WORKFLOW_WS && (!this._wsConnection || this._wsConnection.readyState !== WebSocket.OPEN)) {
              this._connectWorkflowWs()
            }
            this._syncWorkflowSnapshotToMachine({
              phase: this.workflowContext.current_state,
              runtimeState: data.runtime_state || 'QUEUED',
              lifecycleState: this._deriveWorkflowLifecycleState(data.runtime_state || 'QUEUED', { loading: true, hasSession: true })
            })
            this._scheduleWsResultWatchdog(normalizedEvent)
            this._lastAgentCall = { event: normalizedEvent, payload, options }
            keepLoading = true
            return data
          }

          if (data.session_id) this.workflowContext.session_id = data.session_id
          if (data.phase && STATES.includes(data.phase)) {
            this.transitionState(data.phase)
          }
          if (data.node_outputs) {
            this._syncNodeOutputs(data.node_outputs)
          }
          if (data.behavior_metrics && data.behavior_metrics._execution_trace) {
            this.workflowContext.executionTrace = data.behavior_metrics._execution_trace
          }
          this.pendingHumanAction = data.pending_human_action || ''

          if (data.available_actions !== undefined) {
            this.backendAvailableActions = data.available_actions
          }

          if (!options.silent) {
            if (data.error) {
              this.pushAgentMessage({ type: 'error', content: data.error })
            } else if (data.execution_trace && data.execution_trace.length) {
              const pushedCount = this._pushExecutionTrace(data.execution_trace, data.node_outputs || {})
              if (pushedCount === 0) {
                if (!this._pushExecutionTraceExplainerIfPresent(data.node_outputs || {})) {
                  this._pushCardMessage(normalizedEvent, data.node_outputs || {})
                }
              }
              this._pushVisualizeMessageIfPresent(data.node_outputs || {})
            } else if (data.guardrail_result && data.guardrail_result.passed === false) {
              const sr = data.safe_response
              if (sr && typeof sr === 'object' && !Array.isArray(sr)) {
                this._pushCardMessage(normalizedEvent, data.node_outputs || {})
              } else {
                this.pushAgentMessage({
                  type: 'system',
                  content: (typeof sr === 'string' && sr) || '回复已被安全策略过滤'
                })
              }
            } else {
              if (!this._pushExecutionTraceExplainerIfPresent(data.node_outputs || {})) {
                this._pushCardMessage(normalizedEvent, data.node_outputs || {})
              }
            }
          }

          this._lastAgentCall = { event: normalizedEvent, payload, options }
          this._fetchCheckpoints()

          const outputKey = EVENT_OUTPUT_KEY[normalizedEvent] || EVENT_OUTPUT_KEY[data.phase]
          return (data.node_outputs && data.node_outputs[outputKey]) || data.node_outputs || data
        }
        return data
      } catch (err) {
        if (err && (err.code === 'ERR_CANCELED' || err.name === 'CanceledError' || err.name === 'AbortError')) {
          return null
        }
        this.pushAgentMessage({ type: 'error', content: this._resolveWorkflowDispatchError(err) })
        return null
      } finally {
        this._activeAbortController = null
        if (this.agentLoading && !keepLoading) {
          this._clearWsResultWatchdog()
          this._sendWorkflowMachineEvent('RUN_SETTLED', {
            phase: this.workflowContext.current_state,
            runtimeState: this.runtimeContext.runtimeState,
            lifecycleState: this._deriveWorkflowLifecycleState(this.runtimeContext.runtimeState)
          })
        }
      }
    },

    async callAgent(agentId, payload, options = {}) {
      const event = EVENT_MAP[agentId]
      if (!event) throw new Error(`Unknown agent id: ${agentId}`)
      return this.dispatchWorkflowEvent(event, payload, { ...options, agentId })
    },

    _pushCardMessage(event, nodeOutputs) {
      const normalizedEvent = String(event || '').toUpperCase()
      const outputKey = EVENT_OUTPUT_KEY[normalizedEvent]
      const msgType = EVENT_MSG_TYPE[normalizedEvent]
      if (!msgType || !outputKey) return

      const shouldInlineVisualize = this._shouldInlineVisualize(normalizedEvent, nodeOutputs)
      const payload = shouldInlineVisualize
        ? this._withInlineVisualizePayload(nodeOutputs[outputKey], nodeOutputs)
        : nodeOutputs[outputKey]
      if (!payload) return

      if (msgType === 'ai_reply') {
        const history = Array.isArray(payload.history) ? payload.history : []
        const lastReply = [...history].reverse().find(item => item.role === 'assistant')
        if (lastReply) {
          this.pushAgentMessage({ type: 'ai_reply', content: lastReply.content })
        }
        return
      }
      this.pushAgentMessage({ type: msgType, data: payload })
      if (outputKey !== 'visualize' && !shouldInlineVisualize) {
        this._pushVisualizeMessageIfPresent(nodeOutputs)
      }
    },

    _pushKnowledgeReviewMessageIfPresent(nodeOutputs) {
      const payload = (nodeOutputs || {}).knowledge_review
      if (!payload) return false
      this.pushAgentMessage({ type: 'knowledge_review', data: payload })
      return true
    },

    _buildEventData(event, payload) {
      const language = payload.language || this._resolveProblemLanguage()
      const base = {
        language,
        behavior_metrics: {
          consecutiveErrors: this.workflowContext.consecutiveErrors,
          submissionCount: this.workflowContext.submissionCount,
          editFrequency: this.workflowContext.editFrequency,
          dwellTime: this.workflowContext.dwellTime,
          deleteRatio: this.workflowContext.deleteRatio
        }
      }
      switch (event) {
        case 'IDEATING':
          return { ...base, thought_text: payload.thought_text }
        case 'SKELETON':
          return { ...base }
        case 'CODING':
          return {
            ...base,
            code: payload.code,
            request_execution_trace: !!payload.request_execution_trace
          }
        case 'ERROR_FEEDBACK':
          return {
            ...base,
            submission_id: payload.submission_id,
            request_execution_trace: !!payload.request_execution_trace
          }
        case 'AC_REVIEW':
          return {
            ...base,
            submission_id: payload.submission_id,
            code: payload.code,
            language: payload.language,
            guidance_level: payload.guidance_level
          }
        case 'TRANSFER':
          return {
            ...base,
            submission_id: payload.submission_id,
            code: payload.code
          }
        case 'CHAT':
          return {
            ...base,
            message: payload.message || payload.text,
            code: payload.code,
            language: payload.language,
            submission_id: payload.submission_id
          }
        case 'KNOWLEDGE_REVIEW':
          return {
            ...base,
            problem_id: payload.problem_id
          }
        case 'PLAN_RECOMMEND':
          return {
            ...base,
            current_phase: payload.current_phase || this.getCurrentWorkflowPhase()
          }
        case 'PLAN_START':
          return {
            ...base,
            reason: payload.reason,
            trigger_source: payload.trigger_source || payload.triggerSource,
            current_phase: payload.current_phase || payload.currentPhase || this.getCurrentWorkflowPhase(),
            code_snapshot_id: payload.code_snapshot_id || payload.codeSnapshotId || ''
          }
        case 'PLAN_RESPONSE':
          return {
            ...base,
            plan_id: payload.plan_id || payload.planId || this.planId,
            step_id: payload.step_id || payload.stepId,
            evidence_type: payload.evidence_type || payload.evidenceType,
            response_text: payload.response_text || payload.responseText || '',
            code_snapshot_id: payload.code_snapshot_id || payload.codeSnapshotId || '',
            sample_prediction: payload.sample_prediction || payload.samplePrediction || ''
          }
        case 'PLAN_STEERING':
          return {
            ...base,
            plan_id: payload.plan_id || payload.planId || this.planId,
            signal_type: payload.signal_type || payload.signalType,
            redirect_instruction: payload.redirect_instruction || payload.redirectInstruction || ''
          }
        case 'VISUALIZE':
          return {
            ...base,
            intent: payload.intent,
            prompt: payload.prompt,
            context_hints: payload.context_hints || payload.contextHints || {}
          }
        default:
          return base
      }
    },

    async handleInterrupt(action, data = {}) {
      if (!this.workflowContext.session_id) return
      this._sendWorkflowMachineEvent('RUN_REQUESTED', {
        phase: this.workflowContext.current_state,
        runtimeState: this.runtimeContext.runtimeState,
        lifecycleState: 'running'
      })
      try {
        const res = await api.tutorWorkflowRespondInterrupt(
          this.workflowContext.session_id,
          { action: action, data: data }
        )
        const result = res.data && res.data.data !== undefined ? res.data.data : res.data
        if (result) {
          if (result.phase && STATES.includes(result.phase)) {
            this.transitionState(result.phase)
          }
          if (result.node_outputs) {
            this._syncNodeOutputs(result.node_outputs)
          }
          if (result.available_actions !== undefined) {
            this.backendAvailableActions = result.available_actions
          }
          if (result.execution_trace && result.execution_trace.length) {
            this._pushExecutionTrace(result.execution_trace, result.node_outputs || {})
          }
          this.pendingHumanAction = ''
        }
        return result
      } finally {
        this._sendWorkflowMachineEvent('RUN_SETTLED', {
          phase: this.workflowContext.current_state,
          runtimeState: this.runtimeContext.runtimeState,
          lifecycleState: this._deriveWorkflowLifecycleState(this.runtimeContext.runtimeState)
        })
      }
    },

    async handleAcceptPlanRecommendation() {
      if (!this.planRecommendation) return null
      const recommendation = { ...this.planRecommendation }
      this._clearPlanRecommendation()
      return this.dispatchWorkflowEvent('PLAN_START', {
        reason: recommendation.reason,
        trigger_source: recommendation.triggerSource || 'rules',
        current_phase: recommendation.phase || this.getCurrentWorkflowPhase()
      })
    },

    handleDismissPlanRecommendation() {
      this._planRecommendationDismissedUntil = Date.now() + 10 * 60 * 1000
      this._clearPlanRecommendation()
    },

    async onPlanConfirmStep(payload = {}) {
      const currentStep = payload.step || this._getCurrentPlanStep()
      if (!currentStep || !this.planId) return null

      const evidenceType = payload.evidenceType || payload.evidence_type || currentStep.evidence_type || 'text'
      const responseText = payload.responseText || payload.response_text || ''
      const samplePrediction = payload.samplePrediction || payload.sample_prediction || ''
      const codeSnapshotId = payload.codeSnapshotId || payload.code_snapshot_id || ''

      const requestPayload = {
        plan_id: this.planId,
        step_id: currentStep.step_id,
        evidence_type: evidenceType
      }

      if (evidenceType === 'code_change') {
        requestPayload.response_text = this.workflowContext.lastCodeSnapshot || this.code || responseText || ''
        requestPayload.code_snapshot_id = codeSnapshotId
      } else if (evidenceType === 'sample_prediction') {
        requestPayload.sample_prediction = samplePrediction || responseText
        requestPayload.response_text = responseText
      } else {
        requestPayload.response_text = responseText
      }

      return this.dispatchWorkflowEvent('PLAN_RESPONSE', requestPayload)
    },

    async onPlanSkipStep() {
      if (!this.planId) return null
      return this.dispatchWorkflowEvent('PLAN_STEERING', {
        plan_id: this.planId,
        signal_type: 'skip'
      })
    },

    async onPlanPause() {
      if (!this.planId) return null
      return this.dispatchWorkflowEvent('PLAN_STEERING', {
        plan_id: this.planId,
        signal_type: this.planPaused ? 'resume' : 'pause'
      })
    },

    async onPlanTakeOver() {
      if (!this.planId) return null
      return this.dispatchWorkflowEvent('PLAN_STEERING', {
        plan_id: this.planId,
        signal_type: 'take_over'
      })
    },

    async onPlanRedirect(redirectInstruction) {
      if (!this.planId || !redirectInstruction) return null
      return this.dispatchWorkflowEvent('PLAN_STEERING', {
        plan_id: this.planId,
        signal_type: 'redirect',
        redirect_instruction: redirectInstruction
      })
    },

    onSubmissionResult(result, submissionId) {
      this.workflowContext.submissionId = submissionId
      this.workflowContext.submissionCount = (this.workflowContext.submissionCount || 0) + 1
      if (!this.isAITutorAvailableInAssignment) {
        return
      }
      const isAC = result && (result.result === 0 || result.result_code === 0 || result.status === 'AC')

      if (!this.agentPanelVisible) this.agentPanelVisible = true

      if (isAC) {
        this.workflowContext.consecutiveErrors = 0
        this.transitionState('AC_REVIEW')
        this.dispatchWorkflowEvent('AC_REVIEW', {
          submission_id: submissionId,
          code: this.workflowContext.lastCodeSnapshot || this.code,
          language: this._resolveProblemLanguage(),
          problem_id: this.workflowContext.problem_id,
          guidance_level: 1
        }).catch(() => {
          this.pushAgentMessage({ type: 'system', content: 'AC 总结请求失败，请稍后重试' })
        })
      } else {
        this.workflowContext.consecutiveErrors = (this.workflowContext.consecutiveErrors || 0) + 1
        this.dispatchWorkflowEvent('ERROR_FEEDBACK', { submission_id: submissionId }).catch(() => {
          this.transitionState('ERROR_FEEDBACK')
          this.pushAgentMessage({ type: 'system', content: '错误诊断请求失败，请稍后重试' })
        })
      }
    },

    stopAgent() {
      if (this._activeAbortController) {
        this._activeAbortController.abort()
        this._activeAbortController = null
      }
      if (this._wsConnection && this._wsConnection.readyState === WebSocket.OPEN) {
        this._wsConnection.send(JSON.stringify({ type: 'cancel' }))
      }
      this._clearWsResultWatchdog()
      this._sendWorkflowMachineEvent('RUN_SETTLED', {
        phase: this.workflowContext.current_state,
        runtimeState: this.runtimeContext.runtimeState,
        lifecycleState: this._deriveWorkflowLifecycleState(this.runtimeContext.runtimeState)
      })
      this.pushAgentMessage({ type: 'system', content: '已中断当前操作' })
    },

    async _fetchCheckpoints() {
      const sessionId = this.workflowContext.session_id
      if (!sessionId) return
      try {
        this.workflowCheckpoints = await fetchWorkflowCheckpoints(this._workflowSessionQueryClient, sessionId)
      } catch (e) {
        console.warn('[workflow] fetch checkpoints failed', e)
      }
      // 每次 run 成功后刷新 ModeBar 与最近卡片，保证投影状态回显到对话区。
      this.refreshConversationContext()
    },

    async restoreCheckpoint(checkpointId) {
      const sessionId = this.workflowContext.session_id
      if (!sessionId) return
      this._sendWorkflowMachineEvent('RESTORE_STARTED', {
        phase: this.workflowContext.current_state,
        runtimeState: 'RESTORING',
        lifecycleState: 'restoring'
      })
      try {
        this._resetRuntimeContext()
        const res = await api.tutorWorkflowRestoreCheckpoint(
          sessionId,
          { checkpoint_id: checkpointId }
        )
        const data = res.data && res.data.data !== undefined ? res.data.data : res.data
        if (data) {
          const restored = data.restored_state || data
          if (restored && restored.session_id) {
            setWorkflowSessionSnapshot(this._workflowSessionQueryClient, restored.session_id, restored)
          }
          this._applySessionSnapshot(restored)
          if (restored.available_actions !== undefined) {
            this.backendAvailableActions = restored.available_actions
          }
          this.agentMessages = []
          this._rebuildFromTrace(restored)
          this._persistAgentMessagesCache()
          this._fetchCheckpoints()
        }
      } finally {
        this._sendWorkflowMachineEvent('RESTORE_COMPLETED', {
          phase: this.workflowContext.current_state,
          runtimeState: this.runtimeContext.runtimeState,
          lifecycleState: this._deriveWorkflowLifecycleState(this.runtimeContext.runtimeState)
        })
      }
    },

    async regenerateFromMessage(messageId) {
      const msgIndex = this.agentMessages.findIndex(m => m.id === messageId)
      if (msgIndex < 0) return

      let targetCpId = null
      for (let i = this.workflowCheckpoints.length - 1; i >= 0; i--) {
        const cp = this.workflowCheckpoints[i]
        const cpTime = new Date(cp.created_at).getTime()
        if (cpTime < this.agentMessages[msgIndex].timestamp) {
          targetCpId = cp.checkpoint_id
          break
        }
      }
      if (!targetCpId && this.workflowCheckpoints.length) {
        targetCpId = this.workflowCheckpoints[this.workflowCheckpoints.length - 1].checkpoint_id
      }
      if (!targetCpId) return

      await this.restoreCheckpoint(targetCpId)
      if (this._lastAgentCall) {
        const { event, payload } = this._lastAgentCall
        await this.dispatchWorkflowEvent(event, payload)
      }
    },

    _connectWorkflowWs() {
      if (!ENABLE_WORKFLOW_WS) return
      const sessionId = this.workflowContext.session_id
      if (!sessionId) return
      if (this._wsConnection &&
        (this._wsConnection.readyState === WebSocket.CONNECTING || this._wsConnection.readyState === WebSocket.OPEN)) {
        return
      }
      this._disconnectWorkflowWs()

      const wsUrl = buildWebSocketUrl(`/ws/tutor-workflow-sessions/${sessionId}`)
      const ws = new WebSocket(wsUrl)
      this._sendWorkflowMachineEvent('WS_CONNECTING', {
        phase: this.workflowContext.current_state,
        runtimeState: this.runtimeContext.runtimeState,
        lifecycleState: 'ws_connecting'
      })
      let settleReady = null
      let rejectReady = null
      let readySettled = false
      this._wsReadyPromise = new Promise((resolve, reject) => {
        settleReady = resolve
        rejectReady = reject
      })

      ws.onopen = () => {
        if (this._wsConnection === ws && !readySettled) {
          readySettled = true
          settleReady(true)
          this._syncWorkflowSnapshotToMachine({
            phase: this.workflowContext.current_state,
            runtimeState: this.runtimeContext.runtimeState,
            lifecycleState: this._deriveWorkflowLifecycleState(this.runtimeContext.runtimeState, { hasSession: true })
          })
        }
      }

      ws.onmessage = (evt) => {
        let msg
        try { msg = JSON.parse(evt.data) } catch { return }

        if (msg.type === 'runtime_event') {
          this._handleRuntimeEvent(msg)
        } else if (msg.type === 'cancelled') {
          this._sendWorkflowMachineEvent('RUN_SETTLED', {
            phase: this.workflowContext.current_state,
            runtimeState: this.runtimeContext.runtimeState,
            lifecycleState: this._deriveWorkflowLifecycleState(this.runtimeContext.runtimeState)
          })
          this.pushAgentMessage({ type: 'system', content: '后端已取消当前操作' })
        }
      }

      ws.onclose = () => {
        if (this._wsConnection === ws) {
          if (!readySettled) {
            readySettled = true
            rejectReady(new Error('workflow websocket connection closed before ready'))
          }
          this._wsReadyPromise = null
          this._wsConnection = null
          this._wsReconnectTimer = setTimeout(() => this._connectWorkflowWs(), 3000)
        }
      }

      ws.onerror = () => {
        if (!readySettled) {
          readySettled = true
          rejectReady(new Error('workflow websocket connection failed'))
        }
      }

      this._wsConnection = ws
    },

    async _ensureWorkflowWsReady() {
      if (!ENABLE_WORKFLOW_WS) return false
      if (!this.workflowContext.session_id) return false
      if (this._wsConnection && this._wsConnection.readyState === WebSocket.OPEN) {
        return true
      }
      this._connectWorkflowWs()
      if (!this._wsReadyPromise) {
        throw new Error('workflow websocket is unavailable')
      }
      await this._wsReadyPromise
      return this._wsConnection && this._wsConnection.readyState === WebSocket.OPEN
    },

    _disconnectWorkflowWs() {
      if (this._wsReconnectTimer) {
        clearTimeout(this._wsReconnectTimer)
        this._wsReconnectTimer = null
      }
      this._clearWsResultWatchdog()
      this._wsReadyPromise = null
      if (this._wsConnection) {
        const ws = this._wsConnection
        this._wsConnection = null
        ws.onclose = null
        ws.onerror = null
        ws.onopen = null
        ws.close()
      }
    },

    _inferWorkflowEventFromResult(data) {
      const nodeOutputs = data && data.node_outputs ? data.node_outputs : {}
      const lastEventRaw = nodeOutputs.last_event && nodeOutputs.last_event.event
      const lastEvent = String(lastEventRaw || '').toUpperCase()
      if (lastEvent && EVENT_OUTPUT_KEY[lastEvent]) {
        return lastEvent
      }

      const phase = String((data && data.phase) || '').toUpperCase()
      if (phase && EVENT_OUTPUT_KEY[phase]) {
        return phase
      }

      for (const event of Object.keys(EVENT_OUTPUT_KEY)) {
        const outputKey = EVENT_OUTPUT_KEY[event]
        if (outputKey !== 'chat' && nodeOutputs[outputKey]) {
          return event
        }
      }
      return ''
    },

    _clearWsResultWatchdog() {
      if (this._wsResultWatchdogTimer) {
        clearTimeout(this._wsResultWatchdogTimer)
        this._wsResultWatchdogTimer = null
      }
      this._wsResultWatchdogContext = null
    },

    _normalizeSnapshotLastError(raw) {
      const text = typeof raw === 'string' ? raw.trim() : ''
      return text || null
    },

    _recoverWatchdogTerminalSnapshot(data) {
      const runtimeState = String((data && data.runtime_state) || '').toUpperCase()
      if (runtimeState !== 'FAILED' && runtimeState !== 'EXPIRED') {
        return false
      }

      this._applySessionSnapshot(data)
      if (data.phase && STATES.includes(data.phase)) {
        this.transitionState(data.phase)
      }
      if (data.available_actions !== undefined) {
        this.backendAvailableActions = data.available_actions
      }
      this._fetchCheckpoints()

      if (runtimeState === 'FAILED') {
        const errorMessage = this._normalizeSnapshotLastError(data.last_error) || '任务执行失败'
        this.runtimeContext.lastError = errorMessage
        this.runtimeContext.failureBucket = data.failure_bucket || this.runtimeContext.failureBucket || null
        this._sendWorkflowMachineEvent('FAILED', {
          phase: this.workflowContext.current_state,
          runtimeState: 'FAILED',
          lifecycleState: 'failed'
        })
        this.pushAgentMessage({ type: 'error', content: errorMessage })
        return true
      }

      this._sendWorkflowMachineEvent('RUN_SETTLED', {
        phase: this.workflowContext.current_state,
        runtimeState: 'EXPIRED',
        lifecycleState: 'ready'
      })
      this.pushAgentMessage({ type: 'system', content: '任务已超时' })
      return true
    },

    _scheduleWsResultWatchdog(normalizedEvent) {
      if (!ENABLE_WORKFLOW_WS || !this.workflowContext.session_id) return
      this._clearWsResultWatchdog()
      this._wsResultWatchdogContext = {
        sessionId: this.workflowContext.session_id,
        expectedEvent: String(normalizedEvent || '').toUpperCase(),
        requestStartAt: Date.now(),
        attempt: 0
      }
      this._queueWsResultWatchdog()
    },

    _queueWsResultWatchdog(delayMs = WS_RESULT_WATCHDOG_DELAY_MS) {
      this._wsResultWatchdogTimer = setTimeout(() => {
        this._runWsResultWatchdog().catch(() => { })
      }, delayMs)
    },

    _isWatchdogResultReady(data, context) {
      if (!data || !context) return false
      const nodeOutputs = data.node_outputs || {}
      const lastEventRaw = nodeOutputs.last_event && nodeOutputs.last_event.event
      const lastEvent = String(lastEventRaw || '').toUpperCase()

      if (lastEvent) {
        if (context.expectedEvent && lastEvent !== context.expectedEvent) {
          return false
        }
        const eventTsRaw = nodeOutputs.last_event && nodeOutputs.last_event.ts
        const eventTs = eventTsRaw ? new Date(eventTsRaw).getTime() : 0
        if (Number.isFinite(eventTs) && eventTs > 0 && eventTs + 500 < context.requestStartAt) {
          return false
        }
      } else if (context.expectedEvent) {
        const expectedOutputKey = EVENT_OUTPUT_KEY[context.expectedEvent]
        if (!expectedOutputKey || !nodeOutputs[expectedOutputKey]) {
          return false
        }
      } else {
        return false
      }

      if (nodeOutputs.execution_trace_explainer) {
        return true
      }
      const inferredEvent = this._inferWorkflowEventFromResult(data) || context.expectedEvent
      if (!inferredEvent) return false
      const outputKey = EVENT_OUTPUT_KEY[inferredEvent]
      return !!(outputKey && nodeOutputs[outputKey])
    },

    async _runWsResultWatchdog() {
      const context = this._wsResultWatchdogContext
      this._wsResultWatchdogTimer = null
      if (!context) return
      if (!this.agentLoading) {
        this._clearWsResultWatchdog()
        return
      }
      if (this.workflowContext.session_id !== context.sessionId) {
        this._clearWsResultWatchdog()
        return
      }

      let recovered = false
      try {
        const data = await fetchWorkflowSessionSnapshot(this._workflowSessionQueryClient, context.sessionId, { force: true })
        if (!this.agentLoading || this._wsResultWatchdogContext !== context) {
          return
        }
        if (this._recoverWatchdogTerminalSnapshot(data)) {
          recovered = true
        } else if (this._isWatchdogResultReady(data, context)) {
          this._applySessionSnapshot(data)
          if (data.phase && STATES.includes(data.phase)) {
            this.transitionState(data.phase)
          }
          if (data.available_actions !== undefined) {
            this.backendAvailableActions = data.available_actions
          }
          if (!this._pushExecutionTraceExplainerIfPresent(data.node_outputs || {})) {
            const recoveredEvent = this._inferWorkflowEventFromResult(data) || context.expectedEvent
            if (recoveredEvent) {
              this._pushCardMessage(recoveredEvent, data.node_outputs || {})
            }
          }
          this._pushVisualizeMessageIfPresent(data.node_outputs || {})
          this._fetchCheckpoints()
          this._sendWorkflowMachineEvent('RUN_SETTLED', {
            phase: this.workflowContext.current_state,
            runtimeState: this.runtimeContext.runtimeState,
            lifecycleState: this._deriveWorkflowLifecycleState(this.runtimeContext.runtimeState)
          })
          recovered = true
        }
      } catch (e) {
        console.warn('[workflowStateMachine] watchdog recovery failed:', e)
      }

      if (recovered || !this.agentLoading) {
        this._clearWsResultWatchdog()
        return
      }

      context.attempt = (context.attempt || 0) + 1
      if (context.attempt >= WS_RESULT_WATCHDOG_MAX_RETRY) {
        this.pushAgentMessage({ type: 'system', content: '结果同步超时，请重试一次。' })
        this._sendWorkflowMachineEvent('FAILED', {
          phase: this.workflowContext.current_state,
          runtimeState: 'FAILED',
          lifecycleState: 'failed'
        })
        this._clearWsResultWatchdog()
        return
      }
      this._queueWsResultWatchdog(WS_RESULT_WATCHDOG_RETRY_DELAY_MS)
    },

    _updateRuntimeContext(normalized) {
      this.runtimeContext = {
        sessionId: normalized.sessionId || this.runtimeContext.sessionId,
        taskId: normalized.taskId || this.runtimeContext.taskId,
        checkpointId: normalized.checkpointId || this.runtimeContext.checkpointId,
        traceId: normalized.traceId || this.runtimeContext.traceId,
        runtimeState: normalized.runtimeState || this.runtimeContext.runtimeState,
        serverEvent: normalized.serverEvent || this.runtimeContext.serverEvent,
        approvalState: normalized.approvalState !== undefined ? normalized.approvalState : this.runtimeContext.approvalState,
        failureBucket: normalized.failureBucket !== undefined ? normalized.failureBucket : this.runtimeContext.failureBucket,
        lastError: this.runtimeContext.lastError,
        updatedAt: normalized.timestamp || new Date().toISOString()
      }
      this._sendWorkflowMachineEvent('RUNTIME_EVENT', {
        runtimeState: normalized.runtimeState,
        lifecycleState: this._deriveWorkflowLifecycleState(normalized.runtimeState, {
          loading: normalized.runtimeState === 'QUEUED' || normalized.runtimeState === 'RUNNING' || normalized.runtimeState === 'WAITING_TOOL',
          failed: normalized.runtimeState === 'FAILED',
          restoring: normalized.runtimeState === 'RESTORING'
        })
      })
    },

    _handleRuntimeEvent(msg) {
      const normalized = normalizeRuntimeEvent(msg)
      assertAllowedForProblemPage(normalized.runtimeState)
      const serverEvent = normalized.serverEvent

      this._updateRuntimeContext(normalized)
      if (normalized.data && normalized.data.usage) {
        this._applyContextUsage(normalized.data.usage)
      }

      switch (serverEvent) {
        case SERVER_EVENTS.TASK_STARTED:
          this._sendWorkflowMachineEvent('RUN_REQUESTED', {
            phase: this.workflowContext.current_state,
            runtimeState: normalized.runtimeState || 'RUNNING',
            lifecycleState: 'running'
          })
          this.pushAgentMessage({ type: 'system', content: '任务开始执行' })
          break

        case SERVER_EVENTS.TASK_COMPLETED:
          this._clearWsResultWatchdog()
          if (normalized.data) {
            this._handleWsResult(normalized.data)
          } else {
            this._sendWorkflowMachineEvent('RUN_SETTLED', {
              phase: this.workflowContext.current_state,
              runtimeState: normalized.runtimeState || 'COMPLETED',
              lifecycleState: 'ready'
            })
          }
          break

        case SERVER_EVENTS.TASK_FAILED:
          this._clearWsResultWatchdog()
          this.runtimeContext.lastError = (normalized.data && normalized.data.error) || null
          this.runtimeContext.failureBucket = normalized.failureBucket || null
          this._sendWorkflowMachineEvent('FAILED', {
            phase: this.workflowContext.current_state,
            runtimeState: normalized.runtimeState || 'FAILED',
            lifecycleState: 'failed'
          })
          if (normalized.data && normalized.data.error) {
            this.pushAgentMessage({ type: 'error', content: normalized.data.error })
          } else {
            this.pushAgentMessage({ type: 'error', content: '任务执行失败' })
          }
          break

        case SERVER_EVENTS.TASK_EXPIRED:
          this._clearWsResultWatchdog()
          this._sendWorkflowMachineEvent('RUN_SETTLED', {
            phase: this.workflowContext.current_state,
            runtimeState: normalized.runtimeState || 'EXPIRED',
            lifecycleState: 'ready'
          })
          this.pushAgentMessage({ type: 'system', content: '任务已超时' })
          break

        case SERVER_EVENTS.APPROVAL_REQUESTED:
          this._clearWsResultWatchdog()
          this._sendWorkflowMachineEvent('APPROVAL_REQUESTED', {
            phase: this.workflowContext.current_state,
            runtimeState: normalized.runtimeState || 'WAITING_HUMAN_APPROVAL',
            lifecycleState: 'waiting_human_approval'
          })
          if (normalized.data) {
            this.pendingHumanAction = normalized.data.pending_human_action || 'confirm_transfer'
            if (normalized.data.node_outputs) {
              this._syncNodeOutputs(normalized.data.node_outputs)
            }
          }
          break

        case SERVER_EVENTS.APPROVAL_RESOLVED:
          this.pendingHumanAction = ''
          this._sendWorkflowMachineEvent('APPROVAL_RESOLVED', {
            phase: this.workflowContext.current_state,
            runtimeState: normalized.runtimeState,
            lifecycleState: this._deriveWorkflowLifecycleState(normalized.runtimeState)
          })
          break

        case SERVER_EVENTS.TASK_INTERRUPTED:
        case SERVER_EVENTS.TASK_RESTORING:
          break
        case SERVER_EVENTS.TASK_PROGRESS:
        case SERVER_EVENTS.TOOL_CALL_STARTED:
        case SERVER_EVENTS.TOOL_CALL_COMPLETED:
        case SERVER_EVENTS.CARD_GENERATED:
          break

        default:
          break
      }
    },

    // 计划控制统一交给 LangGraph interrupt/resume。

    _handleWsResult(data) {
      if (!data) return
      this._clearWsResultWatchdog()
      const pendingEvent = this._lastAgentCall ? this._lastAgentCall.event : ''
      if (data.usage) this._applyContextUsage(data.usage)
      this._applySessionSnapshot(data)
      if (data.phase && STATES.includes(data.phase)) {
        this.transitionState(data.phase)
      }
      if (data.available_actions !== undefined) {
        this.backendAvailableActions = data.available_actions
      }
      if (data.plan && typeof data.plan === 'object') {
        this._syncPlanProjection(data.plan, data.recommendation_reason || '')
      } else if (data.recommendation_reason) {
        this._applyPlanRecommendation({
          plan_id: data.plan_id || this.planId || '',
          status: 'recommended',
          coordination_reasoning: data.coordination_reasoning || ''
        }, data.recommendation_reason)
      }

      const nodeOutputs = data.node_outputs || {}
      const wsError = typeof data.error === 'string' ? data.error.trim() : ''
      const hasNodeOutputs = nodeOutputs && Object.keys(nodeOutputs).length > 0
      const hasExecutionTrace = Array.isArray(data.execution_trace) && data.execution_trace.length > 0
      if (!wsError && !hasNodeOutputs && !hasExecutionTrace) {
        this._fetchCheckpoints()
        this._sendWorkflowMachineEvent('RUN_SETTLED', {
          phase: this.workflowContext.current_state,
          runtimeState: this.runtimeContext.runtimeState,
          lifecycleState: this._deriveWorkflowLifecycleState(this.runtimeContext.runtimeState)
        })
        return
      }
      if (wsError) {
        this.pushAgentMessage({ type: 'error', content: wsError })
      } else if (hasExecutionTrace) {
        const pushedCount = this._pushExecutionTrace(data.execution_trace, nodeOutputs)
        if (pushedCount === 0) {
          if (!this._pushExecutionTraceExplainerIfPresent(nodeOutputs)) {
            const fallbackEventFromTrace = this._inferWorkflowEventFromResult(data) || pendingEvent
            if (fallbackEventFromTrace) {
              this._pushCardMessage(fallbackEventFromTrace, nodeOutputs)
            }
          }
        }
        this._pushVisualizeMessageIfPresent(nodeOutputs)
      } else {
        if (!this._pushExecutionTraceExplainerIfPresent(nodeOutputs)) {
          const fallbackEvent = this._inferWorkflowEventFromResult(data) || pendingEvent
          if (fallbackEvent) {
            this._pushCardMessage(fallbackEvent, nodeOutputs)
          }
        }
      }

      this._fetchCheckpoints()
      this._sendWorkflowMachineEvent('RUN_SETTLED', {
        phase: this.workflowContext.current_state,
        runtimeState: this.runtimeContext.runtimeState,
        lifecycleState: this._deriveWorkflowLifecycleState(this.runtimeContext.runtimeState)
      })
    },

    async clearWorkflow() {
      const problemId = this.workflowContext.problem_id
      const sessionId = this.workflowContext.session_id
      if (!problemId) return

      this._cancelActiveWorkflowRequest()
      if (sessionId) {
        await api.tutorWorkflowDeleteSession(sessionId).catch(() => {})
        removeWorkflowSessionSnapshot(this._workflowSessionQueryClient, sessionId)
        removeWorkflowCheckpoints(this._workflowSessionQueryClient, sessionId)
      }
      this._clearAgentMessagesCache(problemId)
      this._sendWorkflowMachineEvent('CLEAR', {
        lifecycleState: 'cleared'
      })
      this.resetWorkflowContext()
      await this.createFreshWorkflowSession(problemId)
    },

    _resetRuntimeContext() {
      this.runtimeContext = {
        sessionId: null,
        taskId: null,
        checkpointId: null,
        traceId: null,
        runtimeState: null,
        serverEvent: null,
        approvalState: null,
        failureBucket: null,
        lastError: null,
        updatedAt: null
      }
    },

    resetWorkflowContext() {
      this.workflowContext = {
        problem_id: null,
        session_id: null,
        thread_id: null,
        submissionId: null,
        current_state: 'READING',
        state_history: [],
        problemGuide: null,
        ideateResult: null,
        codeIssues: [],
        lastCodeSnapshot: '',
        diagnosisHistory: [],
        consecutiveErrors: 0,
        acReview: null,
        transferProblem: null,
        submissionCount: 0,
        editFrequency: 0,
        dwellTime: 0,
        deleteRatio: 0
      }
      this.agentMessages = []
      this.agentUserInput = ''
      this.pendingHumanAction = ''
      this.backendAvailableActions = null
      this.workflowCheckpoints = []
      this.planId = null
      this.planSteps = []
      this.planReasoning = ''
      this.planPaused = false
      this.planCompleted = false
      this.planSurrendered = false
      this.planRecommendation = null
      this._planRecommendationDismissedUntil = 0
      this._activeAbortController = null
      this._lastAgentCall = null
      this._wsReadyPromise = null
      this._resetRuntimeContext()
      this._clearWsResultWatchdog()
      this._disconnectWorkflowWs()
      this._sendWorkflowMachineEvent('RESET')
    }
  }
}
