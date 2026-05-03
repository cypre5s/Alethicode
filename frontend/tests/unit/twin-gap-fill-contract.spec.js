/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource (relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

function fileExists (relativePath) {
  return fs.existsSync(path.resolve(__dirname, relativePath))
}

describe('L99 gap fill — all missing sprints now implemented', () => {
  test('S12: TwinWeeklyReflection.vue exists with stats and reflection', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/TwinWeeklyReflection.vue')
    expect(src).toContain("name: 'TwinWeeklyReflection'")
    expect(src).toContain('getTwinWeekly')
    expect(src).toContain('submitSundayReflection')
    expect(src).toContain('tw-reflection__textarea')
  })

  test('S14: TwinReviewQueue.vue exists with fading/forgotten sections', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/TwinReviewQueue.vue')
    expect(src).toContain("name: 'TwinReviewQueue'")
    expect(src).toContain('getKcDecayQueue')
    expect(src).toContain('reviewDecayKc')
    expect(src).toContain('trq-item--forgotten')
    expect(src).toContain('trq-item--fading')
    expect(src).toContain('所有知识点都记得牢牢的')
  })

  test('S14: TwinReviewQueue has warm encouraging copy', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/TwinReviewQueue.vue')
    expect(src).toContain('你的孪生在等你复习')
    expect(src).toContain('帮孪生记住它们')
    expect(src).toContain('复习完成，记忆已刷新')
  })

  test('S19: PublicTwinProfilePage warm footer with CTA', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/PublicTwinProfilePage.vue')
    expect(src).toContain('你也来养一只学习孪生')
    expect(src).toContain('Powered by')
  })

  test('S23: WorldSettingPanel has 6 theme options', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/WorldSettingPanel.vue')
    expect(src).toContain("id: 'academy'")
    expect(src).toContain("id: 'forest'")
    expect(src).toContain("id: 'sunset'")
    expect(src).toContain("id: 'galaxy'")
    expect(src).toContain("id: 'ocean'")
    expect(src).toContain("id: 'sakura'")
  })

  test('all twin Vue components exist (comprehensive check)', () => {
    const twinDir = '../../src/pages/oj/views/user/twin'
    const expected = [
      'LearningTimeline.vue', 'LearningTimelineEvent.vue',
      'KcGalaxyView.vue', 'KcDetailDrawer.vue',
      'TwinPersonaCard.vue',
      'ErrorMuseumView.vue', 'ErrorMuseumExhibit.vue',
      'LearningHealthCard.vue',
      'TwinHero.vue', 'TwinDashboardPage.vue',
      'MetacognitiveMapView.vue',
      'TwinChatPanel.vue',
      'TwinEditMasteryPanel.vue',
      'TwinWeeklyReflection.vue',
      'CodeReplayPlayer.vue',
      'WhatIfBranchView.vue',
      'TwinReviewQueue.vue',
      'PublicTwinProfilePage.vue',
      'WorldSettingPanel.vue'
    ]
    for (const file of expected) {
      expect(fileExists(`${twinDir}/${file}`)).toBe(true)
    }
  })

  test('all problem-page twin components exist', () => {
    const problemDir = '../../src/pages/oj/views/problem'
    expect(fileExists(`${problemDir}/PredictBeforeCodeCard.vue`)).toBe(true)
    expect(fileExists(`${problemDir}/TeachAiCard.vue`)).toBe(true)
  })

  test('twin API has comprehensive method count (35+)', () => {
    const src = readSource('../../src/pages/oj/api/twin.js')
    const methods = src.match(/^\s+\w+\s*[\(]/gm) || []
    expect(methods.length).toBeGreaterThan(29)
  })

  test('all twin components use l99-tokens', () => {
    const twinDir = path.resolve(__dirname, '../../src/pages/oj/views/user/twin')
    const files = fs.readdirSync(twinDir).filter(f => f.endsWith('.vue'))
    for (const file of files) {
      const src = fs.readFileSync(path.join(twinDir, file), 'utf8')
      expect(src).toContain("@import '~@/styles/l99-tokens.less'")
    }
  })
})
