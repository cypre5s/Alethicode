const RUNTIME_STATES = Object.freeze({
  QUEUED: 'QUEUED',
  RUNNING: 'RUNNING',
  WAITING_TOOL: 'WAITING_TOOL',
  WAITING_HUMAN_APPROVAL: 'WAITING_HUMAN_APPROVAL',
  INTERRUPTED: 'INTERRUPTED',
  RESTORING: 'RESTORING',
  FAILED: 'FAILED',
  COMPLETED: 'COMPLETED',
  EXPIRED: 'EXPIRED'
})

const SERVER_EVENTS = Object.freeze({
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
})

const FAILURE_BUCKETS = Object.freeze({
  INSUFFICIENT_EVIDENCE: 'INSUFFICIENT_EVIDENCE',
  CONFLICTING_EVIDENCE: 'CONFLICTING_EVIDENCE',
  CITATION_MISMATCH: 'CITATION_MISMATCH',
  QUERY_REWRITE_REGRESSION: 'QUERY_REWRITE_REGRESSION',
  OUT_OF_SCOPE: 'OUT_OF_SCOPE',
  SCHEMA_VIOLATION: 'SCHEMA_VIOLATION',
  TOOL_EXECUTION_FAILED: 'TOOL_EXECUTION_FAILED',
  APPROVAL_TIMEOUT: 'APPROVAL_TIMEOUT',
  RAG_RETRIEVAL_FAILED: 'RAG_RETRIEVAL_FAILED',
  SYSTEM_ERROR: 'SYSTEM_ERROR',
  UNKNOWN: 'UNKNOWN'
})

const TERMINAL_STATES = Object.freeze(new Set([
  RUNTIME_STATES.FAILED,
  RUNTIME_STATES.COMPLETED,
  RUNTIME_STATES.EXPIRED
]))

const BLOCKING_STATES = Object.freeze(new Set([
  RUNTIME_STATES.WAITING_TOOL,
  RUNTIME_STATES.WAITING_HUMAN_APPROVAL,
  RUNTIME_STATES.RESTORING
]))

const APPROVAL_STATES = Object.freeze(new Set([
  RUNTIME_STATES.WAITING_HUMAN_APPROVAL
]))

const PROBLEM_PAGE_ALLOWED_STATES = Object.freeze(new Set([
  // `QUEUED` 是 createRun 202 返回时的合法起始态；tutor-graph 通过 WebSocket 的
  // `TASK_QUEUED` 事件把它回推到前端，Problem 页必须接受（同 QA 页）。漏掉
  // `QUEUED` 会让 `_handleRuntimeEvent` 第一条消息就抛 Uncaught Error，把整条
  // WebSocket 消息链路打断。
  RUNTIME_STATES.QUEUED,
  RUNTIME_STATES.RUNNING,
  RUNTIME_STATES.WAITING_TOOL,
  RUNTIME_STATES.WAITING_HUMAN_APPROVAL,
  RUNTIME_STATES.INTERRUPTED,
  RUNTIME_STATES.RESTORING,
  RUNTIME_STATES.FAILED,
  RUNTIME_STATES.COMPLETED,
  RUNTIME_STATES.EXPIRED
]))

const QA_PAGE_ALLOWED_STATES = Object.freeze(new Set([
  RUNTIME_STATES.QUEUED,
  RUNTIME_STATES.RUNNING,
  RUNTIME_STATES.FAILED,
  RUNTIME_STATES.COMPLETED,
  RUNTIME_STATES.EXPIRED
]))

export function normalizeRuntimeEvent(raw) {
  if (!raw || typeof raw !== 'object') {
    throw new Error('runtime event payload must be a non-null object')
  }
  return {
    sessionId: raw.session_id || null,
    runId: raw.run_id || null,
    taskId: raw.task_id || raw.run_id || null,
    threadId: raw.thread_id || null,
    checkpointId: raw.checkpoint_id || null,
    traceId: raw.trace_id || null,
    runtimeState: raw.runtime_state || null,
    clientEvent: raw.client_event || null,
    serverEvent: raw.server_event || null,
    approvalState: raw.approval_state || null,
    failureBucket: raw.failure_bucket || null,
    timestamp: raw.timestamp || null,
    data: raw.data || null
  }
}

export function isTerminalRuntimeState(state) {
  return TERMINAL_STATES.has(state)
}

export function isBlockingRuntimeState(state) {
  return BLOCKING_STATES.has(state)
}

export function isApprovalRuntimeState(state) {
  return APPROVAL_STATES.has(state)
}

export function assertAllowedForProblemPage(state) {
  if (!state) return
  if (!PROBLEM_PAGE_ALLOWED_STATES.has(state)) {
    throw new Error(`Problem page received disallowed runtime state: ${state}`)
  }
}

export function assertAllowedForQaPage(state) {
  if (!state) return
  if (!QA_PAGE_ALLOWED_STATES.has(state)) {
    throw new Error(`QA page received disallowed runtime state: ${state}. Tutor-only states must not leak into QA.`)
  }
}

export {
  RUNTIME_STATES,
  SERVER_EVENTS,
  FAILURE_BUCKETS,
  PROBLEM_PAGE_ALLOWED_STATES,
  QA_PAGE_ALLOWED_STATES
}
