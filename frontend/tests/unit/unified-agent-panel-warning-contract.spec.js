/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource (relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('unified agent panel warning contract', () => {
  test('icon component lookup should stay out of Vue deep reactivity', () => {
    const source = readSource('../../src/pages/oj/views/problem/UnifiedAgentPanel.vue')

    // Phase 1 sprint 起 panel 同时引入 ref/computed/markRaw 等多个 vue API；只校验 markRaw
    // 出现在 vue 的具名 import 列表里，而不是锁死单一写法。
    expect(source).toMatch(/import \{[^}]*\bmarkRaw\b[^}]*\} from 'vue'/)
    expect(source).toContain('const ICON_COMPONENTS = markRaw({')
    expect(source).toContain('iconComponents: ICON_COMPONENTS')
  })

  test('panel should declare the custom emits it receives from Problem.vue', () => {
    const source = readSource('../../src/pages/oj/views/problem/UnifiedAgentPanel.vue')

    expect(source).toContain('emits: [')
    expect(source).toContain("'close'")
    expect(source).toContain("'send'")
    expect(source).toContain("'trigger-agent'")
    expect(source).toContain("'switch-input-mode'")
    expect(source).toContain("'request-transfer'")
    expect(source).toContain("'insert-code'")
    expect(source).toContain("'clear-highlights'")
    expect(source).toContain("'request-execution-trace'")
    expect(source).toContain("'approve-action'")
    expect(source).toContain("'restart-workflow'")
  })

  test('panel should not hardcode a second visualize quick action outside backend quickActions', () => {
    const source = readSource('../../src/pages/oj/views/problem/UnifiedAgentPanel.vue')

    expect(source).not.toContain("@click=\"(isInputBlocked || loading || !canChatInput) ? null : handleRequestVisualize()\"")
  })
})
