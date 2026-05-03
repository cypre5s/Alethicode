/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource (relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('L99 Phase C — Learning by Teaching contract', () => {
  test('S13: TeachAiCard.vue has invite / chat / result states', () => {
    const src = readSource('../../src/pages/oj/views/problem/TeachAiCard.vue')
    expect(src).toContain("name: 'TeachAiCard'")
    expect(src).toContain('v-if="!started"')
    expect(src).toContain('v-else-if="!completed"')
    expect(src).toContain('startTeachAiSession')
    expect(src).toContain('submitTeachAiExplanation')
  })

  test('S13: TeachAiCard AI persona is warm and approachable', () => {
    const src = readSource('../../src/pages/oj/views/problem/TeachAiCard.vue')
    expect(src).toContain('刚学编程的新手')
    expect(src).toContain('好呀，我来教你')
    expect(src).not.toContain('系统提示')
  })

  test('S13: TeachAiCard has hint chips for guided explanation', () => {
    const src = readSource('../../src/pages/oj/views/problem/TeachAiCard.vue')
    expect(src).toContain('ta-card__hint-chip')
    expect(src).toContain('举个例子')
    expect(src).toContain('打个比方')
    expect(src).toContain('写段代码')
  })

  test('S13: TeachAiCard shows score and feedback after grading', () => {
    const src = readSource('../../src/pages/oj/views/problem/TeachAiCard.vue')
    expect(src).toContain('ta-card__score-num')
    expect(src).toContain('ta-card__feedback')
    expect(src).toContain('教学分')
  })

  test('S13: TeachAiCard supports followup question and continue', () => {
    const src = readSource('../../src/pages/oj/views/problem/TeachAiCard.vue')
    expect(src).toContain('followupQuestion')
    expect(src).toContain('继续回答')
    expect(src).toContain('ai_followup_question')
  })

  test('S13: TeachAiCard enforces min 10 chars before submit', () => {
    const src = readSource('../../src/pages/oj/views/problem/TeachAiCard.vue')
    expect(src).toContain('explanation.trim().length < 10')
  })

  test('twin API has all Phase C methods', () => {
    const src = readSource('../../src/pages/oj/api/twin.js')
    expect(src).toContain('startTeachAiSession')
    expect(src).toContain('submitTeachAiExplanation')
    expect(src).toContain('getTeachAiSessions')
    expect(src).toContain('getKcDecayQueue')
    expect(src).toContain('reviewDecayKc')
    expect(src).toContain('startArenaMatch')
    expect(src).toContain('judgeArenaAi')
  })

  test('TeachAiCard uses l99-tokens and has warm accent colors', () => {
    const src = readSource('../../src/pages/oj/views/problem/TeachAiCard.vue')
    expect(src).toContain("@import '~@/styles/l99-tokens.less'")
    expect(src).toContain('@l99-accent')
    expect(src).toContain('@l99-primary-soft')
  })

  test('TwinWeeklyReflection has warm tone in reflection prompt', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/TwinWeeklyReflection.vue')
    expect(src).toContain('给自己 2 分钟安静的时间')
    expect(src).not.toContain('必须')
  })

  test('All Phase C Vue files exist', () => {
    const problemDir = path.resolve(__dirname, '../../src/pages/oj/views/problem')
    expect(fs.existsSync(path.join(problemDir, 'TeachAiCard.vue'))).toBe(true)
    expect(fs.existsSync(path.join(problemDir, 'PredictBeforeCodeCard.vue'))).toBe(true)
  })
})
