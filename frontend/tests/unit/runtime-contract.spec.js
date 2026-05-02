/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource (relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('runtime contract', () => {
  const source = readSource('../../src/utils/runtimeContract.js')

  test('exports normalizeRuntimeEvent that maps snake_case to camelCase', () => {
    expect(source).toMatch(/export function normalizeRuntimeEvent\s*\(/)
    expect(source).toContain('sessionId: raw.session_id')
    expect(source).toContain('taskId: raw.task_id')
    expect(source).toContain('checkpointId: raw.checkpoint_id')
    expect(source).toContain('traceId: raw.trace_id')
    expect(source).toContain('runtimeState: raw.runtime_state')
    expect(source).toContain('clientEvent: raw.client_event')
    expect(source).toContain('serverEvent: raw.server_event')
    expect(source).toContain('approvalState: raw.approval_state')
    expect(source).toContain('failureBucket: raw.failure_bucket')
    expect(source).toContain('timestamp: raw.timestamp')
    expect(source).toContain('data: raw.data')
  })

  test('exports isTerminalRuntimeState for FAILED / COMPLETED / EXPIRED', () => {
    expect(source).toMatch(/export function isTerminalRuntimeState\s*\(/)
    expect(source).toContain('RUNTIME_STATES.FAILED')
    expect(source).toContain('RUNTIME_STATES.COMPLETED')
    expect(source).toContain('RUNTIME_STATES.EXPIRED')
  })

  test('exports isBlockingRuntimeState for WAITING_TOOL / WAITING_HUMAN_APPROVAL / RESTORING', () => {
    expect(source).toMatch(/export function isBlockingRuntimeState\s*\(/)
    expect(source).toContain('RUNTIME_STATES.WAITING_TOOL')
    expect(source).toContain('RUNTIME_STATES.WAITING_HUMAN_APPROVAL')
    expect(source).toContain('RUNTIME_STATES.RESTORING')
  })

  test('exports isApprovalRuntimeState for WAITING_HUMAN_APPROVAL only', () => {
    expect(source).toMatch(/export function isApprovalRuntimeState\s*\(/)
    const approvalBlock = source.match(/APPROVAL_STATES[\s\S]*?new Set\(\[([\s\S]*?)\]\)/)
    expect(approvalBlock).toBeTruthy()
    expect(approvalBlock[1]).toContain('WAITING_HUMAN_APPROVAL')
    expect(approvalBlock[1]).not.toContain('WAITING_TOOL')
  })

  test('defines PROBLEM_PAGE_ALLOWED_STATES covering all tutor runtime states', () => {
    expect(source).toContain('PROBLEM_PAGE_ALLOWED_STATES')
    const block = source.match(/PROBLEM_PAGE_ALLOWED_STATES[\s\S]*?new Set\(\[([\s\S]*?)\]\)/)
    expect(block).toBeTruthy()
    const content = block[1]
    expect(content).toContain('RUNNING')
    expect(content).toContain('WAITING_TOOL')
    expect(content).toContain('WAITING_HUMAN_APPROVAL')
    expect(content).toContain('INTERRUPTED')
    expect(content).toContain('RESTORING')
    expect(content).toContain('FAILED')
    expect(content).toContain('COMPLETED')
    expect(content).toContain('EXPIRED')
  })

  test('defines QA_PAGE_ALLOWED_STATES excluding tutor-only states', () => {
    expect(source).toContain('QA_PAGE_ALLOWED_STATES')
    const block = source.match(/QA_PAGE_ALLOWED_STATES[\s\S]*?new Set\(\[([\s\S]*?)\]\)/)
    expect(block).toBeTruthy()
    const content = block[1]
    expect(content).toContain('QUEUED')
    expect(content).toContain('RUNNING')
    expect(content).toContain('FAILED')
    expect(content).toContain('COMPLETED')
    expect(content).toContain('EXPIRED')
    expect(content).not.toContain('WAITING_HUMAN_APPROVAL')
    expect(content).not.toContain('RESTORING')
    expect(content).not.toContain('INTERRUPTED')
  })

  test('assertAllowedForQaPage throws on tutor-only states', () => {
    expect(source).toMatch(/export function assertAllowedForQaPage\s*\(/)
    expect(source).toContain('Tutor-only states must not leak into QA')
  })

  test('SERVER_EVENTS enumerates all backend ServerEvent values', () => {
    const events = [
      'TASK_QUEUED', 'TASK_STARTED', 'TASK_PROGRESS',
      'TOOL_CALL_STARTED', 'TOOL_CALL_COMPLETED', 'CARD_GENERATED',
      'APPROVAL_REQUESTED', 'APPROVAL_RESOLVED',
      'TASK_INTERRUPTED', 'TASK_RESTORING',
      'TASK_COMPLETED', 'TASK_FAILED', 'TASK_EXPIRED'
    ]
    events.forEach(e => {
      expect(source).toContain(e)
    })
  })

  test('FAILURE_BUCKETS enumerates all backend FailureBucket values', () => {
    const buckets = [
      'INSUFFICIENT_EVIDENCE', 'CONFLICTING_EVIDENCE', 'CITATION_MISMATCH',
      'QUERY_REWRITE_REGRESSION', 'OUT_OF_SCOPE', 'SCHEMA_VIOLATION',
      'TOOL_EXECUTION_FAILED', 'APPROVAL_TIMEOUT', 'RAG_RETRIEVAL_FAILED',
      'SYSTEM_ERROR', 'UNKNOWN'
    ]
    buckets.forEach(b => {
      expect(source).toContain(b)
    })
  })

  test('normalizeRuntimeEvent fails fast on null or non-object input', () => {
    expect(source).toContain('runtime event payload must be a non-null object')
  })
})
