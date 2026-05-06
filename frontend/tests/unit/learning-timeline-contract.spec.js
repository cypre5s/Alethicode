/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource (relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('L99 Sprint 01 — learning timeline contract', () => {
  test('LearningTimeline.vue exists and imports LearningTimelineEvent', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/LearningTimeline.vue')
    expect(src).toContain("import LearningTimelineEvent from './LearningTimelineEvent.vue'")
    expect(src).toContain('LearningTimelineEvent')
  })

  test('LearningTimeline calls getLearningTimeline API with from/to/kinds/limit', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/LearningTimeline.vue')
    expect(src).toContain('getLearningTimeline')
    expect(src).toContain('from:')
    expect(src).toContain('to:')
    expect(src).toContain('kinds:')
    expect(src).toContain('limit:')
  })

  test('LearningTimeline has 4 kind filter chips', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/LearningTimeline.vue')
    expect(src).toContain("value: 'submission'")
    expect(src).toContain("value: 'memory'")
    expect(src).toContain("value: 'ai_event'")
    expect(src).toContain("value: 'notebook'")
  })

  test('LearningTimeline handles loading / error / empty states', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/LearningTimeline.vue')
    expect(src).toContain('v-if="loading"')
    expect(src).toContain('v-else-if="error"')
    expect(src).toContain('v-else-if="events.length === 0"')
    expect(src).toContain('你还没有学习记录哦')
    expect(src).toContain('去做第一道题')
  })

  test('LearningTimelineEvent.vue exists and has hover card', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/LearningTimelineEvent.vue')
    expect(src).toContain('tl-event-card')
    expect(src).toContain('查看详情')
    expect(src).toContain("role=\"listitem\"")
  })

  test('LearningTimelineEvent maps event kinds to labels', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/LearningTimelineEvent.vue')
    expect(src).toContain("submission: '代码提交'")
    expect(src).toContain("memory: '学习记忆'")
    expect(src).toContain("ai_event: 'AI 事件'")
    expect(src).toContain("notebook: '错题笔记'")
  })

  test('twin API module exports getLearningTimeline', () => {
    const src = readSource('../../src/pages/oj/api/twin.js')
    expect(src).toContain('getLearningTimeline')
    expect(src).toContain("'twin/timeline'")
  })

  test('api.js aggregator imports twin module', () => {
    const src = readSource('../../src/pages/oj/api.js')
    expect(src).toContain("import twin from './api/twin'")
    expect(src).toContain('...twin')
  })

  test('NotebookHeader has timeline tab', () => {
    const src = readSource('../../src/pages/oj/views/user/notebook/NotebookHeader.vue')
    expect(src).toContain("value: 'timeline'")
    expect(src).toContain('学习时间线')
  })

  test('LearnerNotebook registers LearningTimeline and routes timeline view', () => {
    const src = readSource('../../src/pages/oj/views/user/LearnerNotebook.vue')
    expect(src).toContain("import LearningTimeline from './twin/LearningTimeline.vue'")
    expect(src).toContain("v-if=\"viewMode === 'timeline'\"")
  })

  test('notebookConstants VIEW_MODES includes TIMELINE', () => {
    const src = readSource('../../src/pages/oj/views/user/notebook/notebookConstants.js')
    expect(src).toContain("TIMELINE: 'timeline'")
  })

  test('l99-tokens.less design tokens exist', () => {
    const src = readSource('../../src/styles/l99-tokens.less')
    expect(src).toContain('@l99-primary:')
    expect(src).toContain('@l99-accent:')
    expect(src).toContain('@l99-shadow-1:')
    expect(src).toContain('@l99-ease:')
  })

  test('LearningTimeline uses l99-tokens for styling', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/LearningTimeline.vue')
    expect(src).toContain("@import '~@/styles/l99-tokens.less'")
  })

  test('LearningTimeline has responsive breakpoint for mobile', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/LearningTimeline.vue')
    expect(src).toContain('@media (max-width: 767px)')
  })
})
