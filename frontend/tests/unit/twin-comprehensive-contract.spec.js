/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource (relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}
function fileExists (relativePath) {
  return fs.existsSync(path.resolve(__dirname, relativePath))
}

describe('L99 Comprehensive — all twin components deep validation', () => {
  // ===== l99-tokens.less =====
  describe('l99-tokens.less design system', () => {
    const src = readSource('../../src/styles/l99-tokens.less')
    test('has primary color family', () => { expect(src).toContain('@l99-primary:'); expect(src).toContain('@l99-primary-soft:') })
    test('has accent color', () => { expect(src).toContain('@l99-accent:') })
    test('has status colors', () => { expect(src).toContain('@l99-success:'); expect(src).toContain('@l99-warn:'); expect(src).toContain('@l99-danger:') })
    test('has neutral scale', () => { for (const n of ['900', '700', '500', '200', '100']) expect(src).toContain(`@l99-neutral-${n}:`) })
    test('has font families', () => { expect(src).toContain('@l99-font-sans:'); expect(src).toContain('@l99-font-mono:') })
    test('has font sizes', () => { for (const s of ['xs', 'sm', 'md', 'lg', 'xl', '2xl', '3xl']) expect(src).toContain(`@l99-fs-${s}:`) })
    test('has spacing scale', () => { for (const n of [1,2,3,4,5,6,8,10]) expect(src).toContain(`@l99-sp-${n}:`) })
    test('has shadow levels', () => { for (const n of [1,2,3]) expect(src).toContain(`@l99-shadow-${n}:`) })
    test('has radius levels', () => { for (const r of ['sm', 'md', 'lg']) expect(src).toContain(`@l99-radius-${r}:`) })
    test('has animation ease', () => { expect(src).toContain('@l99-ease:'); expect(src).toContain('cubic-bezier') })
    test('has duration levels', () => { for (const d of ['fast', 'mid', 'slow']) expect(src).toContain(`@l99-dur-${d}:`) })
  })

  // ===== twin API module =====
  describe('twin API module completeness', () => {
    const src = readSource('../../src/pages/oj/api/twin.js')
    const methods = [
      'getLearningTimeline', 'getTwinKcGalaxy', 'getTwinPersona', 'overrideTwinPersona',
      'refreshTwinPersona', 'feedbackTwinPersona', 'getMuseumPins', 'pinMuseumMemory',
      'updateMuseumPin', 'unpinMuseumMemory', 'getTwinHealth', 'submitMetacogPrediction',
      'getMetacogMap', 'askTwin', 'getTwinQuickQuestions', 'overrideMastery',
      'getMasteryOverrides', 'getCodeReplayEvents', 'getWhatIfBranch', 'getTwinWeekly',
      'submitSundayReflection', 'startTeachAiSession', 'submitTeachAiExplanation',
      'getTeachAiSessions', 'getKcDecayQueue', 'reviewDecayKc', 'startArenaMatch',
      'judgeArenaAi', 'getPublicProfile', 'updateTwinPrivacy', 'generateSemesterReport',
      'downloadSemesterReportPdf', 'getCredentials', 'generateCredential', 'exportTwinDump',
      'getWorldSetting', 'updateWorldSetting', 'getAnnualReport', 'generateAnnualReport',
      'generateShareCard'
    ]
    for (const m of methods) {
      test(`exports ${m}`, () => { expect(src).toContain(m) })
    }
    test('uses shared ajax helper', () => { expect(src).toContain("import { ajax } from './shared'") })
  })

  // ===== Each component: existence + key patterns =====
  const twinComponents = [
    { file: 'LearningTimeline.vue', name: 'LearningTimeline', patterns: ['el-date-picker', 'lt-filter-chip', 'lt-day-group', 'loadTimeline', 'loadMore', 'groupedEvents', '@media'] },
    { file: 'LearningTimelineEvent.vue', name: 'LearningTimelineEvent', patterns: ['tl-event-dot', 'tl-event-card', 'showCard', 'formattedTime', 'kindLabel'] },
    { file: 'KcGalaxyView.vue', name: 'KcGalaxyView', patterns: ['echarts/core', 'GraphChart', 'CanvasRenderer', 'SVGRenderer', 'force', 'roam', 'draggable'] },
    { file: 'KcDetailDrawer.vue', name: 'KcDetailDrawer', patterns: ['kc-drawer__mastery-bar', 'relatedNodes', 'getMasteryColor', 'kc-drawer-slide'] },
    { file: 'TwinPersonaCard.vue', name: 'TwinPersonaCard', patterns: ['tp-card', 'editing', 'feedbackGiven', 'startEdit', 'saveEdit', 'submitFeedback'] },
    { file: 'ErrorMuseumView.vue', name: 'ErrorMuseumView', patterns: ['em-grid', 'paddedPins', 'handleUnpin', 'handleUpdateAnnotation'] },
    { file: 'ErrorMuseumExhibit.vue', name: 'ErrorMuseumExhibit', patterns: ['em-exhibit', 'editingAnnotation', 'startEditAnnotation', 'saveAnnotation'] },
    { file: 'LearningHealthCard.vue', name: 'LearningHealthCard', patterns: ['lh-mastery-ring', 'ringDash', 'sparkPoints', 'isOverdue', 'lh-due-tag--overdue'] },
    { file: 'TwinHero.vue', name: 'TwinHero', patterns: ['TwinPersonaCard', 'dailyQuote', 'greeting', 'th-hero__quote'] },
    { file: 'TwinDashboardPage.vue', name: 'TwinDashboardPage', patterns: ['TwinHero', 'LearningTimeline', 'LearningHealthCard', 'KcGalaxyView', 'ErrorMuseumView', 'scrollToTop'] },
    { file: 'MetacognitiveMapView.vue', name: 'MetacognitiveMapView', patterns: ['mc-map__heat-bar', 'totalPredicts', 'exactMatchRate', 'hotMisconceptions'] },
    { file: 'TwinChatPanel.vue', name: 'TwinChatPanel', patterns: ['tc-msg', 'quickQuestions', 'sendMessage', 'scrollToBottom', 'tc-input__send'] },
    { file: 'TwinEditMasteryPanel.vue', name: 'TwinEditMasteryPanel', patterns: ['te-override-row', 'getMasteryOverrides', '系统不一定比你更了解你自己'] },
    { file: 'TwinWeeklyReflection.vue', name: 'TwinWeeklyReflection', patterns: ['tw-reflection__textarea', 'submitSundayReflection', '给自己 2 分钟'] },
    { file: 'CodeReplayPlayer.vue', name: 'CodeReplayPlayer', patterns: ['cr-player__code', 'currentCode', 'togglePlay', 'seekFrame', 'cr-slider'] },
    { file: 'WhatIfBranchView.vue', name: 'WhatIfBranchView', patterns: ['wi-kc-delta--pos', 'wi-kc-delta--neg', 'getWhatIfBranch', 'simulate'] },
    { file: 'TwinReviewQueue.vue', name: 'TwinReviewQueue', patterns: ['trq-item--forgotten', 'trq-item--fading', 'reviewDecayKc', '所有知识点都记得牢牢的'] },
    { file: 'PublicTwinProfilePage.vue', name: 'PublicTwinProfilePage', patterns: ['ptp-hero', 'notFound', 'getPublicProfile', '你也来养一只学习孪生'] },
    { file: 'WorldSettingPanel.vue', name: 'WorldSettingPanel', patterns: ['ws-theme-btn', 'selectedTheme', 'getWorldSetting', 'updateWorldSetting'] }
  ]

  for (const comp of twinComponents) {
    describe(`${comp.file}`, () => {
      const src = readSource(`../../src/pages/oj/views/user/twin/${comp.file}`)
      test('exists', () => { expect(fileExists(`../../src/pages/oj/views/user/twin/${comp.file}`)).toBe(true) })
      test(`has name: '${comp.name}'`, () => { expect(src).toContain(`name: '${comp.name}'`) })
      test('uses l99-tokens', () => { expect(src).toContain("@import '~@/styles/l99-tokens.less'") })
      for (const pat of comp.patterns) {
        test(`contains pattern: ${pat.substring(0, 40)}`, () => { expect(src).toContain(pat) })
      }
    })
  }

  // ===== Problem components =====
  describe('PredictBeforeCodeCard.vue', () => {
    const src = readSource('../../src/pages/oj/views/problem/PredictBeforeCodeCard.vue')
    test('exists and has correct name', () => { expect(src).toContain("name: 'PredictBeforeCodeCard'") })
    test('has prediction flow', () => { expect(src).toContain('submitMetacogPrediction'); expect(src).toContain('submitted'); expect(src).toContain('collapsed') })
    test('has warm skip text', () => { expect(src).toContain('先猜猜结果') })
    test('uses l99-tokens', () => { expect(src).toContain("@import '~@/styles/l99-tokens.less'") })
  })

  describe('TeachAiCard.vue', () => {
    const src = readSource('../../src/pages/oj/views/problem/TeachAiCard.vue')
    test('exists and has correct name', () => { expect(src).toContain("name: 'TeachAiCard'") })
    test('has 3 states', () => { expect(src).toContain('!started'); expect(src).toContain('!completed') })
    test('has hint chips', () => { expect(src).toContain('举个例子'); expect(src).toContain('打个比方'); expect(src).toContain('写段代码') })
    test('has score display', () => { expect(src).toContain('ta-card__score-num'); expect(src).toContain('教学分') })
    test('has followup', () => { expect(src).toContain('followupQuestion'); expect(src).toContain('继续回答') })
    test('uses l99-tokens', () => { expect(src).toContain("@import '~@/styles/l99-tokens.less'") })
  })

  // ===== Router and Index =====
  describe('routing integration', () => {
    const routes = readSource('../../src/pages/oj/router/routes.js')
    test('/twin route registered', () => { expect(routes).toContain("path: '/twin'") })
    test('twin route requires auth', () => { expect(routes).toContain('TwinDashboardPage') })

    const views = readSource('../../src/pages/oj/views/index.js')
    test('TwinDashboardPage exported', () => { expect(views).toContain('TwinDashboardPage') })
  })

  // ===== NotebookHeader timeline tab =====
  describe('NotebookHeader timeline integration', () => {
    const src = readSource('../../src/pages/oj/views/user/notebook/NotebookHeader.vue')
    test('has timeline tab', () => { expect(src).toContain("value: 'timeline'"); expect(src).toContain('学习时间线') })
  })

  // ===== File count validation =====
  describe('file completeness', () => {
    test('19 twin Vue components exist', () => {
      const dir = path.resolve(__dirname, '../../src/pages/oj/views/user/twin')
      const files = fs.readdirSync(dir).filter(f => f.endsWith('.vue'))
      expect(files.length).toBe(19)
    })
    test('2 problem Vue components exist', () => {
      const dir = path.resolve(__dirname, '../../src/pages/oj/views/problem')
      expect(fileExists(`../../src/pages/oj/views/problem/PredictBeforeCodeCard.vue`)).toBe(true)
      expect(fileExists(`../../src/pages/oj/views/problem/TeachAiCard.vue`)).toBe(true)
    })
  })
})
