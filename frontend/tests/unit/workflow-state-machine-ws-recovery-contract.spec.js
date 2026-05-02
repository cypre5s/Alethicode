/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource (relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('workflow state machine ws recovery contract', () => {
  test('should schedule ws result watchdog for async dispatched workflow events', () => {
    const source = readSource('../../src/pages/oj/views/problem/workflowStateMachine.js')

    expect(source).toMatch(/_scheduleWsResultWatchdog\s*\(/)
    expect(source).toMatch(/data\.runtime_state === 'QUEUED'|data\.status === 'dispatched'/)
    expect(source).toMatch(/this\._scheduleWsResultWatchdog\(normalizedEvent\)/)
  })

  test('should clear ws result watchdog after receiving ws result', () => {
    const source = readSource('../../src/pages/oj/views/problem/workflowStateMachine.js')

    expect(source).toMatch(/_clearWsResultWatchdog\s*\(/)
    expect(source).toMatch(/_handleWsResult\s*\(data\)\s*\{[\s\S]*this\._clearWsResultWatchdog\(\)/)
  })

  test('should fallback to node_outputs rendering when execution_trace cannot render any card', () => {
    const source = readSource('../../src/pages/oj/views/problem/workflowStateMachine.js')

    expect(source).toMatch(/const pushedCount = this\._pushExecutionTrace\(/)
    expect(source).toContain('if (!this._pushExecutionTraceExplainerIfPresent(nodeOutputs)) {')
    expect(source).toMatch(/if \(pushedCount === 0\) \{/)
  })

  test('should retry watchdog recovery quickly after the first missed result window', () => {
    const source = readSource('../../src/pages/oj/views/problem/workflowStateMachine.js')

    expect(source).toMatch(/const WS_RESULT_WATCHDOG_RETRY_DELAY_MS = 1000/)
    expect(source).toMatch(/_queueWsResultWatchdog\s*\(delayMs = WS_RESULT_WATCHDOG_DELAY_MS\)/)
    expect(source).toMatch(/this\._queueWsResultWatchdog\(WS_RESULT_WATCHDOG_RETRY_DELAY_MS\)/)
  })

  test('should allow a longer watchdog timeout budget before declaring sync failure', () => {
    const source = readSource('../../src/pages/oj/views/problem/workflowStateMachine.js')

    expect(source).toMatch(/const WS_RESULT_WATCHDOG_MAX_RETRY = 30/)
  })
})
