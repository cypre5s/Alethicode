/* eslint-env jest */

const fs = require('fs')
const path = require('path')
const babel = require('@babel/core')

jest.mock('@oj/api', () => ({
  __esModule: true,
  default: {
    workflowGetSession: jest.fn(),
    workflowCreateSession: jest.fn(),
    workflowGetCheckpoints: jest.fn(),
    workflowClearSession: jest.fn(),
    workflowEvent: jest.fn(),
    tutorWorkflowCreateSession: jest.fn(),
    tutorWorkflowGetSession: jest.fn(),
    tutorWorkflowDeleteSession: jest.fn(),
    tutorWorkflowCreateRun: jest.fn(),
    tutorWorkflowGetCheckpoints: jest.fn(),
    tutorWorkflowRestoreCheckpoint: jest.fn(),
    tutorWorkflowRespondInterrupt: jest.fn()
  }
}))

jest.mock('@/utils/storage', () => ({
  __esModule: true,
  default: {
    get: jest.fn(),
    set: jest.fn(),
    remove: jest.fn()
  }
}))

jest.mock('@/utils/websocketUrl', () => ({
  __esModule: true,
  buildWebSocketUrl: jest.fn(() => 'ws://127.0.0.1:8081/ws/workflow/mock-session')
}))

jest.mock('@/utils/runtimeContract', () => ({
  __esModule: true,
  normalizeRuntimeEvent: jest.fn((raw) => ({
    sessionId: raw.session_id || null,
    taskId: raw.task_id || null,
    checkpointId: raw.checkpoint_id || null,
    traceId: raw.trace_id || null,
    runtimeState: raw.runtime_state || null,
    clientEvent: raw.client_event || null,
    serverEvent: raw.server_event || null,
    approvalState: raw.approval_state || null,
    failureBucket: raw.failure_bucket || null,
    timestamp: raw.timestamp || null,
    data: raw.data || null
  })),
  assertAllowedForProblemPage: jest.fn(),
  isTerminalRuntimeState: jest.fn((s) => ['FAILED', 'COMPLETED', 'EXPIRED'].includes(s)),
  isBlockingRuntimeState: jest.fn((s) => ['WAITING_TOOL', 'WAITING_HUMAN_APPROVAL', 'RESTORING'].includes(s)),
  SERVER_EVENTS: {
    TASK_QUEUED: 'TASK_QUEUED',
    TASK_STARTED: 'TASK_STARTED',
    TASK_PROGRESS: 'TASK_PROGRESS',
    TOOL_CALL_STARTED: 'TOOL_CALL_STARTED',
    TOOL_CALL_COMPLETED: 'TOOL_CALL_COMPLETED',
    CARD_GENERATED: 'CARD_GENERATED',
    APPROVAL_REQUESTED: 'APPROVAL_REQUESTED',
    APPROVAL_RESOLVED: 'APPROVAL_RESOLVED',
    TASK_INTERRUPTED: 'TASK_INTERRUPTED',
    TASK_RESTORING: 'TASK_RESTORING',
    TASK_COMPLETED: 'TASK_COMPLETED',
    TASK_FAILED: 'TASK_FAILED',
    TASK_EXPIRED: 'TASK_EXPIRED'
  }
}))

function loadWorkflowStateMachineModule() {
  const filePath = path.resolve(__dirname, '../../src/pages/oj/views/problem/workflowStateMachine.js')
  const source = fs.readFileSync(filePath, 'utf8')
  const transformed = babel.transformSync(source, {
    filename: filePath,
    presets: [require.resolve('@babel/preset-env')]
  })
  const module = { exports: {} }
  const localRequire = (request) => {
    if (request === '@tanstack/vue-query') {
      class QueryClient {
        constructor() {
          this.cache = new Map()
          this.inFlight = new Map()
        }

        async fetchQuery({ queryKey, queryFn, staleTime = 0 }) {
          const key = JSON.stringify(queryKey)
          const cached = this.cache.get(key)
          const now = Date.now()
          if (cached && cached.expiresAt > now) {
            return cached.data
          }
          if (this.inFlight.has(key)) {
            return this.inFlight.get(key)
          }
          const promise = Promise.resolve(queryFn()).then((data) => {
            this.cache.set(key, { data, expiresAt: now + staleTime })
            this.inFlight.delete(key)
            return data
          }).catch((error) => {
            this.inFlight.delete(key)
            throw error
          })
          this.inFlight.set(key, promise)
          return promise
        }

        setQueryData(queryKey, data) {
          this.cache.set(JSON.stringify(queryKey), { data, expiresAt: Number.MAX_SAFE_INTEGER })
        }

        getQueryData(queryKey) {
          const cached = this.cache.get(JSON.stringify(queryKey))
          return cached ? cached.data : undefined
        }

        async invalidateQueries({ queryKey }) {
          this.cache.delete(JSON.stringify(queryKey))
        }

        removeQueries({ queryKey }) {
          this.cache.delete(JSON.stringify(queryKey))
        }
      }

      return { QueryClient }
    }
    if (request === './agentContracts') {
      return {
        PHASES: [
          'READING',
          'IDEATING',
          'CODING',
          'ERROR_FEEDBACK',
          'AC_REVIEW',
          'TRANSFER'
        ],
        CARD_TYPES: [
          'problem_guide',
          'ideate_analysis',
          'skeleton_code',
          'error_diagnosis',
          'post_ac',
          'transfer_problem',
          'ai_reply',
          'execution_trace_explainer',
          'knowledge_review',
          'visualize',
          'parsons_problem'
        ]
      }
    }
    if (request.startsWith('.')) {
      const resolved = path.resolve(path.dirname(filePath), request)
      const resolvedPath = fs.existsSync(resolved) ? resolved : resolved + '.js'
      const src = fs.readFileSync(resolvedPath, 'utf8')
      if (/\bimport\b.*\bfrom\b|\bexport\b/.test(src)) {
        const t = babel.transformSync(src, {
          filename: resolvedPath,
          presets: [require.resolve('@babel/preset-env')]
        })
        const m = { exports: {} }
        const f = new Function('module', 'exports', 'require', t.code)
        f(m, m.exports, localRequire)
        return m.exports
      }
      return require(resolved)
    }
    return require(request)
  }
  // eslint-disable-next-line no-new-func
  const fn = new Function('module', 'exports', 'require', transformed.code)
  fn(module, module.exports, localRequire)
  return module.exports.default || module.exports
}

const workflowStateMachine = loadWorkflowStateMachineModule()
const api = require('@oj/api').default
const storage = require('@/utils/storage').default

function createVm(overrides = {}) {
  const vm = {
    ...workflowStateMachine.data(),
    problemID: 110,
    ...overrides
  }

  Object.keys(workflowStateMachine.methods).forEach((name) => {
    vm[name] = workflowStateMachine.methods[name].bind(vm)
  })

  vm._connectWorkflowWs = jest.fn()
  vm._disconnectWorkflowWs = jest.fn()
  vm._fetchCheckpoints = jest.fn()
  vm._ensureWorkflowWsReady = jest.fn().mockResolvedValue(false)

  return vm
}

describe('workflow state machine session restore cache contract', () => {
  beforeEach(() => {
    jest.clearAllMocks()
  })

  test('initWorkflowSession should keep rebuilt ideate card instead of overwriting it with stale same-session cache', async () => {
    const sessionId = 'session-restore-1'
    const ideatePayload = {
      understood_as: '你想先求平均值，再根据平均值判断结果。',
      step_plan: ['遍历列表求和', '用总和除以元素个数得到平均值'],
      has_logic_gap: false,
      logic_gap_hint: '',
      confidence_level: 'medium'
    }

    api.tutorWorkflowGetSession.mockResolvedValue({
      data: {
        data: {
          session_id: sessionId,
          phase: 'IDEATING',
          node_outputs: {
            ideate: ideatePayload,
            last_event: {
              event: 'IDEATING',
              ts: '2026-03-29T02:25:36.000Z'
            }
          },
          behavior_metrics: {}
        }
      }
    })

    storage.get.mockReturnValue({
      session_id: sessionId,
      messages: [
        {
          id: 'cached-user-message',
          type: 'user',
          content: '使用avg函数解决',
          timestamp: 1
        },
        {
          id: 'cached-system-message',
          type: 'system',
          content: '正在执行: ideating...',
          timestamp: 2
        }
      ]
    })

    const vm = createVm({
      workflowContext: {
        ...workflowStateMachine.data().workflowContext,
        session_id: sessionId
      }
    })

    await vm.initWorkflowSession(110)

    expect(vm.agentMessages).toEqual(expect.arrayContaining([
      expect.objectContaining({
        type: 'ideate_analysis',
        data: ideatePayload
      })
    ]))
  })

  test('rebuildFromTrace should append user chat history while avoiding duplicated assistant replay', () => {
    const vm = createVm()

    vm._rebuildFromTrace({
      execution_trace: [
        {
          type: 'agent_output',
          message_type: 'ai_reply',
          output_key: 'chat',
          payload: {
            role: 'assistant',
            content: '先从输入输出入手。'
          }
        }
      ],
      node_outputs: {
        chat: {
          history: [
            { role: 'user', content: '我应该先写哪一步？' },
            { role: 'assistant', content: '先从输入输出入手。' }
          ]
        }
      }
    })

    expect(vm.agentMessages).toEqual(expect.arrayContaining([
      expect.objectContaining({
        type: 'ai_reply',
        content: '先从输入输出入手。'
      }),
      expect.objectContaining({
        type: 'user',
        content: '我应该先写哪一步？'
      })
    ]))
    expect(vm.agentMessages.filter(entry => entry.type === 'ai_reply')).toHaveLength(1)
  })

  test('initWorkflowSession should merge same-session cached user messages after rebuilding assistant trace', async () => {
    const sessionId = 'session-restore-chat-1'

    api.tutorWorkflowGetSession.mockResolvedValue({
      data: {
        data: {
          session_id: sessionId,
          phase: 'CODING',
          execution_trace: [
            {
              type: 'agent_output',
              message_type: 'ai_reply',
              output_key: 'chat',
              payload: {
                role: 'assistant',
                content: '先检查输入解析。'
              }
            }
          ],
          node_outputs: {
            chat: {
              history: [
                { role: 'assistant', content: '先检查输入解析。' }
              ]
            }
          },
          behavior_metrics: {}
        }
      }
    })

    storage.get.mockReturnValue({
      session_id: sessionId,
      messages: [
        {
          id: 'cached-user-message',
          type: 'user',
          content: '为什么这里会报错？',
          timestamp: 1
        },
        {
          id: 'cached-ai-message',
          type: 'ai_reply',
          content: '先检查输入解析。',
          timestamp: 2
        }
      ]
    })

    const vm = createVm({
      workflowContext: {
        ...workflowStateMachine.data().workflowContext,
        session_id: sessionId
      }
    })

    await vm.initWorkflowSession(110)

    expect(vm.agentMessages).toEqual(expect.arrayContaining([
      expect.objectContaining({
        type: 'user',
        content: '为什么这里会报错？'
      }),
      expect.objectContaining({
        type: 'ai_reply',
        content: '先检查输入解析。'
      })
    ]))
    expect(vm.agentMessages.filter(entry => entry.type === 'ai_reply' && entry.content === '先检查输入解析。')).toHaveLength(1)
  })

  test('clearWorkflow should abort in-flight work and create a fresh empty session instead of restoring old dialogue', async () => {
    const abort = jest.fn()

    api.tutorWorkflowDeleteSession.mockResolvedValue({ data: { data: { cleared: true } } })
    api.tutorWorkflowCreateSession.mockResolvedValue({
      data: {
        data: {
          session_id: 'fresh-session',
          thread_id: 'fresh-thread',
          phase: 'READING',
          node_outputs: {},
          behavior_metrics: {},
          pending_human_action: '',
          available_actions: []
        }
      }
    })
    api.tutorWorkflowGetSession.mockResolvedValue({
      data: {
        data: {
          session_id: 'stale-session',
          phase: 'CODING',
          node_outputs: {},
          behavior_metrics: {}
        }
      }
    })

    const vm = createVm({
      workflowContext: {
        ...workflowStateMachine.data().workflowContext,
        problem_id: 110,
        session_id: 'old-session'
      },
      language: 'Python',
      agentMessages: [
        { id: 'm1', type: 'user', content: '旧对话', timestamp: 1 }
      ],
      _activeAbortController: { abort }
    })

    await vm.clearWorkflow()

    expect(abort).toHaveBeenCalledTimes(1)
    expect(api.tutorWorkflowGetSession).not.toHaveBeenCalled()
    expect(api.tutorWorkflowCreateSession).toHaveBeenCalledTimes(1)
    expect(vm.agentMessages).toEqual([])
    expect(vm.workflowContext.problem_id).toBe(110)
    expect(vm.workflowContext.session_id).toBe('fresh-session')
  })

  test('dispatchWorkflowEvent should delegate illegal transition to backend and surface 422 error message', async () => {
    api.tutorWorkflowCreateRun.mockRejectedValue({
      response: {
        status: 422,
        data: {
          error: 'event IDEATING is not allowed from current phase CODING'
        }
      }
    })
    const vm = createVm({
      workflowContext: {
        ...workflowStateMachine.data().workflowContext,
        problem_id: 110,
        session_id: 'session-illegal-transition-1',
        current_state: 'CODING'
      },
      language: 'Python'
    })

    const result = await vm.dispatchWorkflowEvent('IDEATING', {
      problem_id: 110,
      thought_text: 'test'
    })

    expect(result).toBeNull()
    expect(api.tutorWorkflowCreateRun).toHaveBeenCalledTimes(1)
    expect(vm.agentMessages).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          type: 'error',
          content: 'event IDEATING is not allowed from current phase CODING'
        })
      ])
    )
  })

  test('dispatchWorkflowEvent should auto-retry session creation when session_id is null', async () => {
    api.tutorWorkflowCreateSession.mockRejectedValue(new Error('service unavailable'))
    const vm = createVm({
      workflowContext: {
        ...workflowStateMachine.data().workflowContext,
        problem_id: 110,
        session_id: null,
        current_state: 'READING'
      },
      language: 'Python'
    })

    const result = await vm.dispatchWorkflowEvent('READING', { problem_id: 110 })

    expect(result).toBeNull()
    expect(api.tutorWorkflowCreateSession).toHaveBeenCalledTimes(1)
    expect(api.tutorWorkflowCreateRun).not.toHaveBeenCalled()
    expect(vm.agentMessages).toEqual([
      expect.objectContaining({
        type: 'error',
        content: expect.stringMatching(/AI 导学会话尚未就绪/)
      })
    ])
  })

  test('dispatchWorkflowEvent should recover session and proceed when auto-retry succeeds', async () => {
    api.tutorWorkflowCreateSession.mockResolvedValue({
      data: { data: { session_id: 'recovered-session', phase: 'READING' } }
    })
    api.tutorWorkflowCreateRun.mockResolvedValue({
      data: {
        data: {
          session_id: 'recovered-session',
          phase: 'READING',
          node_outputs: {},
          available_actions: []
        }
      }
    })
    const vm = createVm({
      workflowContext: {
        ...workflowStateMachine.data().workflowContext,
        problem_id: 110,
        session_id: null,
        current_state: 'READING'
      },
      language: 'Python'
    })

    const result = await vm.dispatchWorkflowEvent('READING', { problem_id: 110 })

    expect(api.tutorWorkflowCreateSession).toHaveBeenCalledTimes(1)
    expect(api.tutorWorkflowCreateRun).toHaveBeenCalledTimes(1)
    expect(vm.workflowContext.session_id).toBe('recovered-session')
    expect(vm.agentMessages).not.toEqual(
      expect.arrayContaining([
        expect.objectContaining({ type: 'error', content: expect.stringMatching(/尚未就绪/) })
      ])
    )
  })

  test('dispatchWorkflowEvent should deduplicate consecutive identical error messages', async () => {
    api.tutorWorkflowCreateSession.mockRejectedValue(new Error('service unavailable'))
    const vm = createVm({
      workflowContext: {
        ...workflowStateMachine.data().workflowContext,
        problem_id: 110,
        session_id: null,
        current_state: 'READING'
      },
      language: 'Python'
    })

    await vm.dispatchWorkflowEvent('READING', { problem_id: 110 })
    await vm.dispatchWorkflowEvent('READING', { problem_id: 110 })
    await vm.dispatchWorkflowEvent('READING', { problem_id: 110 })

    const errorMessages = vm.agentMessages.filter(m => m.type === 'error')
    expect(errorMessages).toHaveLength(1)
  })

  test('quickActions should use backend actions directly once session is ready', () => {
    const vm = createVm({
      workflowContext: {
        ...workflowStateMachine.data().workflowContext,
        problem_id: 110,
        session_id: 'session-qa-1',
        current_state: 'CODING'
      },
      backendAvailableActions: [
        { key: 'error_chain', label: '错误诊断', event: 'ERROR_FEEDBACK', agent_id: 4 },
        { key: 'ideate', label: '思路分析', event: 'IDEATING', agent_id: 2 }
      ]
    })

    const quickActions = workflowStateMachine.computed.quickActions.call(vm)

    expect(quickActions).toEqual([
      expect.objectContaining({
        key: 'error_chain',
        event: 'ERROR_FEEDBACK'
      }),
      expect.objectContaining({
        key: 'ideate',
        event: 'IDEATING'
      })
    ])
  })

  test('quickActions should hide coding entry but keep PARSONS actions for AI tutor buttons', () => {
    const vm = createVm({
      workflowContext: {
        ...workflowStateMachine.data().workflowContext,
        problem_id: 110,
        session_id: 'session-qa-1',
        current_state: 'IDEATING'
      },
      backendAvailableActions: [
        { key: 'ideate', label: '继续思路分析', event: 'IDEATING', agent_id: 2 },
        { key: 'coding', label: '开始编码', event: 'CODING', agent_id: 0 },
        { key: 'coding_mode', label: '编码', event: 'CODING', agent_id: 0 },
        { key: 'parsons', label: '试试拼装版', event: 'PARSONS', agent_id: 2 },
        { key: 'visualize', label: '画一下', event: 'VISUALIZE', agent_id: 7 }
      ]
    })

    const quickActions = workflowStateMachine.computed.quickActions.call(vm)

    expect(quickActions.map(action => action.label)).toEqual(['继续思路分析', '试试拼装版', '画一下'])
    expect(quickActions.map(action => action.event)).not.toContain('CODING')
    expect(quickActions.map(action => action.event)).toContain('PARSONS')
  })

  test('quickActions should fallback to bootstrap actions when session is not ready', () => {
    const vm = createVm({
      workflowContext: {
        ...workflowStateMachine.data().workflowContext,
        problem_id: 110,
        current_state: 'TRANSFER'
      },
      pendingHumanAction: 'confirm_transfer'
    })

    const quickActions = workflowStateMachine.computed.quickActions.call(vm)

    expect(quickActions.map(action => action.key)).toEqual(['problem_guide', 'ideate'])
  })

  test('watchdog should recover FAILED snapshot with real error instead of emitting sync timeout', async () => {
    const sessionId = 'session-watchdog-failed'
    api.tutorWorkflowGetSession.mockResolvedValue({
      data: {
        data: {
          session_id: sessionId,
          phase: 'IDEATING',
          runtime_state: 'FAILED',
          failure_bucket: 'SYSTEM_ERROR',
          last_error: 'LLM generation failed',
          node_outputs: {},
          available_actions: [
            { key: 'ideate', label: '继续思路分析', event: 'IDEATING', agent_id: 2 }
          ]
        }
      }
    })

    const vm = createVm({
      workflowContext: {
        ...workflowStateMachine.data().workflowContext,
        problem_id: 110,
        session_id: sessionId,
        current_state: 'IDEATING'
      },
      agentLoading: true
    })
    vm._wsResultWatchdogContext = {
      sessionId,
      expectedEvent: 'IDEATING',
      requestStartAt: Date.now(),
      attempt: 0
    }

    await vm._runWsResultWatchdog()

    expect(vm.agentLoading).toBe(false)
    expect(vm.runtimeContext.runtimeState).toBe('FAILED')
    expect(vm.runtimeContext.failureBucket).toBe('SYSTEM_ERROR')
    expect(vm.runtimeContext.lastError).toBe('LLM generation failed')
    expect(vm.agentMessages).toEqual(expect.arrayContaining([
      expect.objectContaining({ type: 'error', content: 'LLM generation failed' })
    ]))
    expect(vm.agentMessages).not.toEqual(expect.arrayContaining([
      expect.objectContaining({ type: 'system', content: '结果同步超时，请重试一次。' })
    ]))
    expect(vm._wsResultWatchdogContext).toBe(null)
  })

  test('syncPlanProjection should let xstate drive planPaused and planCompleted flags', () => {
    const vm = createVm({
      workflowContext: {
        ...workflowStateMachine.data().workflowContext,
        problem_id: 110,
        session_id: 'session-plan-state',
        current_state: 'READING'
      }
    })

    vm._syncPlanProjection({
      plan_id: 'plan-1',
      status: 'paused',
      steps: [{ step_id: 'step-1', status: 'active' }]
    })

    expect(vm.planPaused).toBe(true)
    expect(vm.planCompleted).toBe(false)
    expect(vm.planSteps).toEqual([expect.objectContaining({ step_id: 'step-1' })])

    vm._sendWorkflowMachineEvent('RUN_SETTLED', {
      phase: 'READING',
      runtimeState: 'COMPLETED',
      lifecycleState: 'ready'
    })

    expect(vm.planPaused).toBe(true)
    expect(vm.planCompleted).toBe(false)

    vm._syncPlanProjection({
      plan_id: 'plan-1',
      status: 'completed',
      steps: [{ step_id: 'step-1', status: 'completed' }]
    })

    expect(vm.planPaused).toBe(false)
    expect(vm.planCompleted).toBe(true)

    vm._syncPlanProjection({})

    expect(vm.planPaused).toBe(false)
    expect(vm.planCompleted).toBe(false)
    expect(vm.planSteps).toEqual([])
  })

  test('error diagnosis should embed same-run visualize payload instead of appending a separate card', () => {
    const vm = createVm({ agentMessages: [] })
    const diagnosis = { root_cause: '输出格式不符合题目要求' }
    const visualize = {
      intent: 'flowchart',
      format: 'mermaid',
      payload: 'flowchart TD\nA[读取半径] --> B[计算面积]',
      alt_text: '圆面积程序流程图'
    }

    vm._pushCardMessage('ERROR_FEEDBACK', {
      error_diagnosis: diagnosis,
      visualize
    })

    expect(vm.agentMessages).toHaveLength(1)
    expect(vm.agentMessages[0].type).toBe('error_diagnosis')
    expect(vm.agentMessages[0].data).toEqual({ ...diagnosis, visualize })
  })

  test('restored error diagnosis should keep same-run visualize inside the diagnosis card', () => {
    const vm = createVm({ agentMessages: [] })
    const diagnosis = { root_cause: '变量更新顺序错误' }
    const visualize = {
      intent: 'flowchart',
      format: 'mermaid',
      payload: 'flowchart TD\nA --> B',
      alt_text: '错误路径流程图'
    }

    vm._rebuildAgentMessages({
      node_outputs: {
        error_diagnosis: diagnosis,
        visualize
      }
    })

    expect(vm.agentMessages).toHaveLength(1)
    expect(vm.agentMessages[0].type).toBe('error_diagnosis')
    expect(vm.agentMessages[0].data).toEqual({ ...diagnosis, visualize })
  })

  test('restored explicit visualize event should remain a standalone visualize card', () => {
    const vm = createVm({ agentMessages: [] })
    const diagnosis = { root_cause: '变量更新顺序错误' }
    const visualize = {
      intent: 'flowchart',
      format: 'mermaid',
      payload: 'flowchart TD\nA --> B',
      alt_text: '单独请求的流程图'
    }

    vm._rebuildAgentMessages({
      node_outputs: {
        error_diagnosis: diagnosis,
        visualize,
        last_event: { event: 'VISUALIZE' }
      }
    })

    expect(vm.agentMessages).toEqual(expect.arrayContaining([
      expect.objectContaining({ type: 'error_diagnosis', data: diagnosis }),
      expect.objectContaining({ type: 'visualize', data: visualize })
    ]))
  })
})
