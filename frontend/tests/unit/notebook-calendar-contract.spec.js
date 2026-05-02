/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource(relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('notebook calendar contract (Phase 2)', () => {
  test('LearnerNotebook splits into orchestration + 9 notebook subfiles', () => {
    const orchestrator = readSource('../../src/pages/oj/views/user/LearnerNotebook.vue')
    expect(orchestrator.split('\n').length).toBeLessThanOrEqual(300)

    const subfiles = [
      'NotebookHeader.vue',
      'NotebookFilterToolbar.vue',
      'NotebookCalendarView.vue',
      'NotebookCalendarCell.vue',
      'NotebookDayDrawer.vue',
      'NotebookArchiveView.vue',
      'NotebookEntryCard.vue',
      'NotebookAddDialog.vue',
      'MisconceptionTagCloud.vue'
    ]
    for (const file of subfiles) {
      const p = path.resolve(__dirname, '../../src/pages/oj/views/user/notebook/', file)
      expect(fs.existsSync(p)).toBe(true)
      const lines = fs.readFileSync(p, 'utf8').split('\n').length
      expect(lines).toBeLessThanOrEqual(300)
    }
  })

  test('LearnerNotebook routes view mode through calendar / archive children', () => {
    const orchestrator = readSource('../../src/pages/oj/views/user/LearnerNotebook.vue')
    expect(orchestrator).toContain("v-if=\"viewMode === 'calendar'\"")
    expect(orchestrator).toContain('NotebookCalendarView')
    expect(orchestrator).toContain('NotebookArchiveView')
    expect(orchestrator).toContain('NotebookDayDrawer')
  })

  test('LearnerNotebook builds calendar items by merging due reviews and entries', () => {
    const orchestrator = readSource('../../src/pages/oj/views/user/LearnerNotebook.vue')
    expect(orchestrator).toContain('calendarItems')
    expect(orchestrator).toContain("kind: 'review'")
    expect(orchestrator).toContain("kind: 'entry'")
    expect(orchestrator).toContain('toLocalDateKey(card.due_at)')
    expect(orchestrator).toContain('toLocalDateKey(e.create_time)')
  })

  test('NotebookCalendarView renders 6x7 grid (42 cells) with month navigation', () => {
    const view = readSource('../../src/pages/oj/views/user/notebook/NotebookCalendarView.vue')
    expect(view).toContain("'周日'")
    expect(view).toContain('shiftMonth(-1)')
    expect(view).toContain('shiftMonth(1)')
    expect(view).toContain('NotebookCalendarCell')
    // 42 = 6 weeks × 7 days
    expect(view).toContain('for (let i = 0; i < 42; i++)')
  })

  test('NotebookCalendarCell exposes 3 distinct chip tones and overflow chip', () => {
    const cell = readSource('../../src/pages/oj/views/user/notebook/NotebookCalendarCell.vue')
    expect(cell).toContain('ncc-chip-primary')
    expect(cell).toContain('ncc-chip-danger')
    expect(cell).toContain('ncc-chip-success')
    expect(cell).toContain('ncc-chip-overflow')
    expect(cell).toMatch(/emits:\s*\[\s*['"]open-day['"]\s*\]/)
  })

  test('toLocalDateKey is the shared helper for grouping by local day', () => {
    const formatters = readSource('../../src/pages/oj/views/user/notebook/notebookFormatters.js')
    expect(formatters).toContain('export function toLocalDateKey')
    expect(formatters).toContain('d.getFullYear()')
    expect(formatters).toContain("padStart(2, '0')")
  })

  test('notebookActions exposes the load helpers consumed by the orchestrator', () => {
    const actions = readSource('../../src/pages/oj/views/user/notebook/notebookActions.js')
    for (const fn of ['fetchNotebookEntries', 'fetchDueReviews', 'fetchMisconceptions', 'fetchClassFrequency', 'createReviewPackages', 'buildReviewPackageRoute']) {
      expect(actions).toContain('export ' + (fn === 'buildReviewPackageRoute' ? 'function ' : 'async function ') + fn)
    }
    expect(actions).toContain('export function buildReviewPackageGroups')
  })
})
