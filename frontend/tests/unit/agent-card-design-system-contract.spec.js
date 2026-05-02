/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource(relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

const TUTOR_CARDS = [
  ['ProblemGuideCard.vue', 'guide'],
  ['IdeateAnalysisCard.vue', 'ideate'],
  ['PostACCard.vue', 'success'],
  ['ErrorDiagnosisCard.vue', 'danger'],
  ['TransferProblemCard.vue', 'transfer'],
  ['SkeletonCodeCard.vue', 'primary'],
  ['ExecutionTraceExplainerCard.vue', 'primary'],
  ['KnowledgeReviewCard.vue', 'review'],
  ['EncouragementCard.vue', 'encouragement']
]

describe('agent card design system contract (Phase 1)', () => {
  test('cardSizingTokens.less owns the 8 shared --card-* size variables', () => {
    const tokens = readSource('../../src/styles/cardSizingTokens.less')
    const required = [
      '--card-radius:',
      '--card-head-px:',
      '--card-body-px:',
      '--card-body-py:',
      '--card-font-title:',
      '--card-font-body:',
      '--card-font-label:',
      '--card-icon-size:'
    ]
    for (const variable of required) {
      expect(tokens).toContain(variable)
    }
  })

  test('cardAccentTokens.less defines all 8 accent palettes', () => {
    const tokens = readSource('../../src/styles/cardAccentTokens.less')
    const accents = ['primary', 'guide', 'ideate', 'success', 'danger', 'transfer', 'review', 'encouragement']
    for (const accent of accents) {
      expect(tokens).toContain(`.card-accent-${accent} ()`)
    }
  })

  test('global stylesheet imports both token files', () => {
    const index = readSource('../../src/styles/index.less')
    expect(index).toContain("@import './cardSizingTokens.less'")
    expect(index).toContain("@import './cardAccentTokens.less'")
  })

  test('BaseAgentCard wires accent prop, head/body/foot slots and uses sizing tokens', () => {
    const base = readSource('../../src/pages/oj/views/problem/cards/BaseAgentCard.vue')
    expect(base).toContain("name: 'BaseAgentCard'")
    expect(base).toContain('accent:')
    expect(base).toContain('ALLOWED_ACCENTS')
    expect(base).toContain('<slot name="body"')
    expect(base).toContain('<slot name="head-extra"')
    expect(base).toContain('<slot name="foot"')
    expect(base).toContain('var(--card-radius)')
    expect(base).toContain('var(--card-head-px)')
    expect(base).toContain('var(--card-body-px)')
    expect(base).toContain('var(--card-font-title)')
    expect(base).toContain('var(--card-icon-size)')
    expect(base).toContain('var(--card-accent)')
    expect(base).toContain('var(--card-accent-bg)')
    expect(base).toContain('var(--card-accent-border)')
  })

  test.each(TUTOR_CARDS)('%s renders through BaseAgentCard with accent="%s"', (file, accent) => {
    const source = readSource(`../../src/pages/oj/views/problem/cards/${file}`)
    expect(source).toContain('BaseAgentCard')
    expect(source).toContain(`accent="${accent}"`)
  })

  test.each(TUTOR_CARDS)('%s does not redeclare card sizing tokens locally', (file) => {
    const source = readSource(`../../src/pages/oj/views/problem/cards/${file}`)
    expect(source).not.toMatch(/--card-radius\s*:/)
    expect(source).not.toMatch(/--card-head-px\s*:/)
    expect(source).not.toMatch(/--card-body-px\s*:/)
    expect(source).not.toMatch(/--card-font-title\s*:/)
    expect(source).not.toMatch(/--card-icon-size\s*:/)
  })

  test('UnifiedAgentPanel no longer carries inline knowledge_review/encouragement card markup', () => {
    const panel = readSource('../../src/pages/oj/views/problem/UnifiedAgentPanel.vue')
    expect(panel).not.toMatch(/class="knowledge-review-card"/)
    expect(panel).not.toMatch(/class="encourage-card-wrap"/)
    expect(panel).not.toMatch(/\.knowledge-review-card\s*\{/)
    expect(panel).not.toMatch(/\.encourage-card-wrap\s*\{/)
  })

  test('UnifiedAgentPanel routes review/encouragement messages through the new cards', () => {
    const panel = readSource('../../src/pages/oj/views/problem/UnifiedAgentPanel.vue')
    expect(panel).toContain('KnowledgeReviewCard')
    expect(panel).toContain('EncouragementCard')
    expect(panel).toContain("v-else-if=\"item.type === 'knowledge_review'\"")
    expect(panel).toContain("v-else-if=\"item.type === 'encouragement'\"")
  })

  test('UnifiedAgentPanel no longer redeclares the --card-* sizing variables locally', () => {
    const panel = readSource('../../src/pages/oj/views/problem/UnifiedAgentPanel.vue')
    expect(panel).not.toMatch(/--card-radius\s*:\s*14px/)
    expect(panel).not.toMatch(/--card-font-title\s*:\s*13\.5px/)
  })
})
