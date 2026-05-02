/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource (relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('workflow runtime event contract', () => {
  const source = readSource('../../src/pages/oj/views/problem/workflowStateMachine.js')

  test('imports runtimeContract utilities', () => {
    expect(source).toContain("import {")
    expect(source).toContain('normalizeRuntimeEvent')
    expect(source).toContain('assertAllowedForProblemPage')
    expect(source).toContain('isTerminalRuntimeState')
    expect(source).toContain('SERVER_EVENTS')
    expect(source).toContain("from '@/utils/runtimeContract'")
  })

  test('data includes runtimeContext with all required fields', () => {
    expect(source).toContain('runtimeContext:')
    const fields = ['sessionId', 'taskId', 'checkpointId', 'traceId', 'runtimeState', 'serverEvent', 'approvalState', 'failureBucket', 'lastError', 'updatedAt']
    fields.forEach(field => {
      expect(source).toContain(`${field}:`)
    })
  })

  test('ws onmessage dispatches runtime_event instead of node_start/result', () => {
    expect(source).toContain("msg.type === 'runtime_event'")
    expect(source).toContain('this._handleRuntimeEvent(msg)')
    expect(source).not.toMatch(/msg\.type === ['"]node_start['"]/)
    expect(source).toContain("msg.type === 'cancelled'")
  })

  test('_handleRuntimeEvent normalizes and validates runtime event', () => {
    expect(source).toMatch(/_handleRuntimeEvent\s*\(msg\)\s*\{/)
    expect(source).toContain('normalizeRuntimeEvent(msg)')
    expect(source).toContain('assertAllowedForProblemPage(normalized.runtimeState)')
    expect(source).not.toContain('msg.plan_event')
    expect(source).not.toContain('PLAN_STARTED')
  })

  test('runtimeContract.PROBLEM_PAGE_ALLOWED_STATES includes QUEUED (createRun 202 initial state)', () => {
    // createRun 返回 runtime_state=QUEUED，tutor-graph 通过 WebSocket 的
    // TASK_QUEUED 事件把它推到 Problem 页；缺了 QUEUED 会让
    // assertAllowedForProblemPage 在第一条消息就抛 Uncaught Error，把整条
    // runtime event 链路打断。这一条测试锁定 QUEUED 必须在 allowed set 中。
    const contract = readSource('../../src/utils/runtimeContract.js')
    expect(contract).toMatch(/PROBLEM_PAGE_ALLOWED_STATES\s*=\s*Object\.freeze\(new Set\(\[[\s\S]*?RUNTIME_STATES\.QUEUED/)
  })

  test('_handleRuntimeEvent handles TASK_STARTED by entering machine-managed running state', () => {
    expect(source).toContain('case SERVER_EVENTS.TASK_STARTED:')
    expect(source).toMatch(/TASK_STARTED:[\s\S]*?this\._sendWorkflowMachineEvent\('RUN_REQUESTED'/)
  })

  test('_handleRuntimeEvent handles TASK_COMPLETED by delegating to _handleWsResult', () => {
    expect(source).toContain('case SERVER_EVENTS.TASK_COMPLETED:')
    expect(source).toMatch(/TASK_COMPLETED:[\s\S]*?this\._handleWsResult\(normalized\.data\)/)
  })

  test('_handleRuntimeEvent handles TASK_FAILED by entering failed lifecycle state and showing error', () => {
    expect(source).toContain('case SERVER_EVENTS.TASK_FAILED:')
    expect(source).toMatch(/TASK_FAILED:[\s\S]*?this\._sendWorkflowMachineEvent\('FAILED'/)
  })

  test('_handleRuntimeEvent handles TASK_EXPIRED', () => {
    expect(source).toContain('case SERVER_EVENTS.TASK_EXPIRED:')
    expect(source).toMatch(/TASK_EXPIRED:[\s\S]*?this\._sendWorkflowMachineEvent\('RUN_SETTLED'/)
  })

  test('_handleRuntimeEvent passes through state-only events without fabricating cards', () => {
    expect(source).toContain('case SERVER_EVENTS.TASK_INTERRUPTED:')
    expect(source).toContain('case SERVER_EVENTS.TASK_RESTORING:')
    expect(source).toContain('case SERVER_EVENTS.APPROVAL_REQUESTED:')
    expect(source).toContain('case SERVER_EVENTS.APPROVAL_RESOLVED:')
  })

  test('_updateRuntimeContext merges normalized event into runtimeContext', () => {
    expect(source).toMatch(/_updateRuntimeContext\s*\(normalized\)\s*\{/)
    expect(source).toContain('this.runtimeContext = {')
  })

  test('_resetRuntimeContext clears all runtime fields', () => {
    expect(source).toMatch(/_resetRuntimeContext\s*\(\)\s*\{/)
    expect(source).toMatch(/_resetRuntimeContext[\s\S]*?sessionId: null/)
  })

  test('resetWorkflowContext also resets runtimeContext', () => {
    expect(source).toMatch(/resetWorkflowContext[\s\S]*?this\._resetRuntimeContext\(\)/)
  })

  test('restoreCheckpoint resets runtimeContext before restoring', () => {
    expect(source).toMatch(/restoreCheckpoint[\s\S]*?this\._resetRuntimeContext\(\)/)
  })

  test('_applySessionSnapshot recovers runtimeContext from backend snapshot when fields are present', () => {
    expect(source).toMatch(/_applySessionSnapshot[\s\S]*?data\.runtime_state/)
  })

  test('_applySessionSnapshot recovers plan and recommendation payloads from backend snapshot', () => {
    expect(source).toMatch(/_applySessionSnapshot[\s\S]*?data\.plan/)
    expect(source).toMatch(/_applySessionSnapshot[\s\S]*?data\.recommendation_reason/)
  })

  test('_handleWsResult understands plan recommendation payloads', () => {
    expect(source).toMatch(/_handleWsResult[\s\S]*?data\.recommendation_reason/)
    expect(source).toMatch(/_handleWsResult[\s\S]*?this\.planRecommendation/)
  })

  test('no longer has msg.type === result as a primary WebSocket branch', () => {
    const wsOnMessage = source.match(/ws\.onmessage\s*=[\s\S]*?(?=ws\.onclose)/)
    expect(wsOnMessage).toBeTruthy()
    expect(wsOnMessage[0]).not.toContain("msg.type === 'result'")
  })
})
