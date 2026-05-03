/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource (relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('L99 Sprint 08 — twin chat contract', () => {
  test('TwinChatPanel.vue exists and calls askTwin', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/TwinChatPanel.vue')
    expect(src).toContain("name: 'TwinChatPanel'")
    expect(src).toContain('askTwin')
  })

  test('TwinChatPanel has quick question buttons', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/TwinChatPanel.vue')
    expect(src).toContain('quickQuestions')
    expect(src).toContain('getTwinQuickQuestions')
    expect(src).toContain('tc-quick__btn')
  })

  test('TwinChatPanel has message bubbles with role-based styling', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/TwinChatPanel.vue')
    expect(src).toContain('tc-msg')
    expect(src).toContain('msg.role')
    expect(src).toContain('tc-msg__bubble')
  })

  test('TwinChatPanel has input field with send button', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/TwinChatPanel.vue')
    expect(src).toContain('tc-input__field')
    expect(src).toContain('tc-input__send')
    expect(src).toContain('sendMessage')
  })

  test('TwinChatPanel auto-scrolls on new messages', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/TwinChatPanel.vue')
    expect(src).toContain('scrollToBottom')
    expect(src).toContain('scrollHeight')
  })

  test('twin API has chat methods', () => {
    const src = readSource('../../src/pages/oj/api/twin.js')
    expect(src).toContain('askTwin')
    expect(src).toContain('getTwinQuickQuestions')
    expect(src).toContain("'twin/chat'")
    expect(src).toContain("'twin/chat/quick-questions'")
  })

  test('TwinChatPanel uses l99-tokens', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/TwinChatPanel.vue')
    expect(src).toContain("@import '~@/styles/l99-tokens.less'")
  })
})
