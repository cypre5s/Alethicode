/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource (relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('knowledge star map review actions wiring', () => {
  const starMapSource = readSource('../../src/pages/oj/components/skillProfile/KnowledgeStarMap.vue')

  test('tooltip template renders recommended review actions when present', () => {
    expect(starMapSource).toContain('class="ksm-tooltip-actions"')
    expect(starMapSource).toContain('ksm-tooltip-actions-title')
    expect(starMapSource).toContain('推荐复习动作')
    expect(starMapSource).toMatch(/v-for="\(action, ai\) in tooltip\.reviewActions"/)
    expect(starMapSource).toContain('action.label')
    expect(starMapSource).toContain('action.hint')
  })

  test('showTooltip reads recommended_review_actions from node data', () => {
    expect(starMapSource).toContain('d.recommended_review_actions')
    expect(starMapSource).toContain('reviewActions: actions')
  })

  test('tooltip initial data includes empty reviewActions list', () => {
    expect(starMapSource).toMatch(/reviewActions:\s*\[\s*\]/)
  })

  test('tooltip actions wrapper is hidden when reviewActions is empty', () => {
    expect(starMapSource).toMatch(/v-if="tooltip\.reviewActions && tooltip\.reviewActions\.length"/)
  })

  test('CSS styles for tooltip actions are declared', () => {
    expect(starMapSource).toContain('.ksm-tooltip-actions {')
    expect(starMapSource).toContain('.ksm-tooltip-action-label')
    expect(starMapSource).toContain('.ksm-tooltip-action-hint')
  })
})
