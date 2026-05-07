/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource(relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

// 旧版 babel-jest 无法直接消费 useReferenceParse.js 的 ES module 语法；
// 测试通过 Node 执行真实函数源码，避免复制一份实现。
const parseReferences = (() => {
  const source = readSource('../../src/pages/oj/views/problem/useReferenceParse.js')
  // 去掉 ES module 语法，让 Function() 可直接执行函数体。
  const transformed = source
    .replace(/export\s+default\s+parseReferences\s*$/m, '')
    .replace(/export\s+function\s+parseReferences/, 'function parseReferences')
  // eslint-disable-next-line no-new-func
  return new Function(`${transformed}\nreturn parseReferences;`)()
})()

describe('unified chat context (P3) contracts', () => {
  test('parseReferences extracts @card and @last_xxx tokens, dedupes, and ignores unknown shorthands', () => {
    const tokens = parseReferences('@card:C-V-001 解释一下 @last_error 还有 @last_unknown 和 @last_visualize')
    expect(tokens).toEqual(['@card:C-V-001', '@last_error', '@last_visualize'])
    expect(parseReferences('')).toEqual([])
    expect(parseReferences(null)).toEqual([])
    expect(parseReferences('@card:C-V-001 @card:C-V-001')).toEqual(['@card:C-V-001'])
  })

  test('agentContracts exposes the nine Unified Chat Modes and Phase × Mode allow matrix', () => {
    const source = readSource('../../src/pages/oj/views/problem/agentContracts.js')
    expect(source).toContain('CONVERSATION_MODES')
    expect(source).toContain('CONVERSATION_MODE_ALLOWED_BY_PHASE')
    expect(source).toContain('CONVERSATION_MODE_LABEL')
    for (const mode of ['reading', 'ideate', 'coding', 'error_diag', 'visualize', 'ac_review', 'transfer', 'knowledge_review', 'chat']) {
      expect(source).toContain(`'${mode}'`)
    }
  })

  test('conversation API module exposes the ModeBar + last cards endpoints', () => {
    const apiSource = readSource('../../src/pages/oj/api/conversation.js')
    expect(apiSource).toContain("ajax(`ai/tutor-workflow-sessions/${sessionId}/conversation`, 'get')")
    expect(apiSource).toContain("ajax(`ai/tutor-workflow-sessions/${sessionId}/mode`, 'post'")
  })

  test('OJ API root re-exports conversation.js so api.getConversation / switchConversationMode resolve', () => {
    const rootSource = readSource('../../src/pages/oj/api.js')
    expect(rootSource).toContain("import conversation from './api/conversation'")
    expect(rootSource).toContain('...conversation')
  })

  test('Problem.vue chat dispatch carries parsed references and the active conversation mode', () => {
    const problemSource = readSource('../../src/pages/oj/views/problem/Problem.vue')
    expect(problemSource).toContain("import { parseReferences } from './useReferenceParse'")
    expect(problemSource).toContain(':last-conversation-cards="lastConversationCards"')
    expect(problemSource).toContain('const references = parseReferences(text)')
    expect(problemSource).toContain('references,')
    expect(problemSource).toContain("mode: this.activeConversationMode || 'chat'")
  })

  test('UnifiedAgentPanel surfaces @ card / @last_ / @courseware tokens via the shared composer hook', () => {
    const panelSource = readSource('../../src/pages/oj/views/problem/UnifiedAgentPanel.vue')
    // @ 菜单已迁移到 useChatComposer + AtMentionMenu；panel 通过 setup() 注入三组 atProviders。
    expect(panelSource).toContain('lastConversationCards')
    expect(panelSource).toContain("from '@oj/components/chat/useChatComposer'")
    expect(panelSource).toContain('AtMentionMenu')
    expect(panelSource).toContain('SlashCommandMenu')
    expect(panelSource).toContain("'@card:' + card.card_id")
    expect(panelSource).toContain("'@last_' + (SHORTHAND_BY_TYPE[card.card_type]")
    expect(panelSource).toContain("'@courseware:' + pack.id")
    expect(panelSource).toContain('formatReferenceDescription')
    expect(panelSource).toContain('引用最近的知识点回顾卡片')
    expect(panelSource).toContain('rgba(255, 255, 255, 0.98)')
    expect(panelSource).toContain('#8b5cf6')
  })

  test('workflowStateMachine tracks active conversation mode and refreshes after every run', () => {
    const stateMachineSource = readSource('../../src/pages/oj/views/problem/workflowStateMachine.js')
    expect(stateMachineSource).toContain("activeConversationMode: 'reading'")
    expect(stateMachineSource).toContain('lastConversationCards: []')
    expect(stateMachineSource).toContain('async refreshConversationContext()')
    expect(stateMachineSource).toContain('async switchConversationMode(targetMode)')
    expect(stateMachineSource).toContain('this.refreshConversationContext()')
    expect(stateMachineSource).toContain('api.getConversation(sessionId)')
    expect(stateMachineSource).toContain('api.switchConversationMode(sessionId, targetMode)')
  })
})
