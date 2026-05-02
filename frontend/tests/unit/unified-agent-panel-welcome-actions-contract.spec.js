/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource (relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('unified agent panel welcome starter actions wiring', () => {
  const panelSource = readSource('../../src/pages/oj/views/problem/UnifiedAgentPanel.vue')
  const problemSource = readSource('../../src/pages/oj/views/problem/Problem.vue')
  const fsmSource = readSource('../../src/pages/oj/views/problem/workflowStateMachine.js')
  const contractsSource = readSource('../../src/pages/oj/views/problem/agentContracts.js')

  test('effectiveWelcomeActions passes through structured event/payload from backend', () => {
    expect(panelSource).toMatch(/effectiveWelcomeActions\s*\(\)\s*\{/)
    expect(panelSource).toContain('item.event')
    expect(panelSource).toContain('item.payload')
    expect(panelSource).toContain("key: item.key || ('welcome_' + i)")
  })

  test('effectiveWelcomeActions filters hidden starter buttons from the tutor UI', () => {
    expect(panelSource).toContain(".filter(action => !this.isHiddenTutorAction(action))")
    expect(panelSource).toContain("isHiddenTutorAction (action)")
    expect(panelSource).toContain("normalizedEvent === 'CODING'")
    expect(panelSource).toContain("normalizedLabel === '开始编码' || normalizedLabel === '编码'")
    expect(panelSource).not.toContain("normalizedEvent === 'PARSONS'")
    expect(panelSource).not.toContain("normalizedLabel === '试试拼装版'")
  })

  test('Problem.handleTriggerAgent dispatches KNOWLEDGE_REVIEW for knowledge_review key', () => {
    expect(problemSource).toContain("key === 'knowledge_review'")
    expect(problemSource).toContain("'KNOWLEDGE_REVIEW'")
    expect(problemSource).toContain('知识点回顾请求失败')
  })

  test('Problem.handleTriggerAgent consumes welcome payload submission_id for error_chain', () => {
    expect(problemSource).toContain('welcomePayload.submission_id')
  })

  test('workflowStateMachine exposes KNOWLEDGE_REVIEW transitions and output mapping', () => {
    expect(fsmSource).toContain("KNOWLEDGE_REVIEW: 'knowledge_review'")
    expect(fsmSource).toContain('KNOWLEDGE_REVIEW: CARD_TYPES[8]')
    expect(fsmSource).toContain("case 'KNOWLEDGE_REVIEW':")
  })

  test('agentContracts WORKFLOW_EVENTS and CARD_TYPES include KNOWLEDGE_REVIEW', () => {
    expect(contractsSource).toContain("'KNOWLEDGE_REVIEW'")
    expect(contractsSource).toContain("'knowledge_review'")
  })

  test('UnifiedAgentPanel renders knowledge_review card timeline item', () => {
    expect(panelSource).toContain("item.type === 'knowledge_review'")
    expect(panelSource).toContain('<KnowledgeReviewCard')
    expect(panelSource).toContain("import KnowledgeReviewCard from './cards/KnowledgeReviewCard.vue'")
  })

  test('workflowStateMachine KNOWLEDGE_REVIEW event data includes problem_id', () => {
    expect(fsmSource).toMatch(/case 'KNOWLEDGE_REVIEW':[\s\S]*?problem_id:\s*payload\.problem_id/)
  })
})
