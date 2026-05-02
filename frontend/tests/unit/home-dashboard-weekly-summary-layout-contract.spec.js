/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource (relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('home dashboard weekly summary layout contract', () => {
  test('weekly summary should appear between continue card and quick actions', () => {
    const source = readSource('../../src/pages/oj/views/general/HomeDashboard.vue')
    const continueCardIndex = source.indexOf('<section class="continue-card">')
    const weeklySummaryIndex = source.indexOf('<section v-if="weeklySummary && weeklySummary.total_errors > 0" class="weekly-section weekly-section--main">')
    const quickActionsIndex = source.indexOf('<section class="quick-actions">')

    expect(continueCardIndex).toBeGreaterThan(-1)
    expect(weeklySummaryIndex).toBeGreaterThan(continueCardIndex)
    expect(quickActionsIndex).toBeGreaterThan(weeklySummaryIndex)
    expect(source).not.toContain('<section v-if="weeklySummary && weeklySummary.total_errors > 0" class="weekly-section">')
  })
})
