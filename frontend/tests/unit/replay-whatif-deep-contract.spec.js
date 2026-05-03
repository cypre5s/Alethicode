/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource (relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('L99 Sprint 10+11 deep — code replay & what-if branch contract', () => {
  test('CodeReplayPlayer.vue exists with full playback controls', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/CodeReplayPlayer.vue')
    expect(src).toContain("name: 'CodeReplayPlayer'")
    expect(src).toContain('prevFrame')
    expect(src).toContain('nextFrame')
    expect(src).toContain('togglePlay')
    expect(src).toContain('startPlay')
    expect(src).toContain('stopPlay')
    expect(src).toContain('seekFrame')
  })

  test('CodeReplayPlayer has problem selector with frame count', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/CodeReplayPlayer.vue')
    expect(src).toContain('selectedProblemId')
    expect(src).toContain('cr-player__select')
    expect(src).toContain('frame_count')
  })

  test('CodeReplayPlayer shows code in mono font pre block', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/CodeReplayPlayer.vue')
    expect(src).toContain('cr-player__code')
    expect(src).toContain('currentCode')
    expect(src).toContain('@l99-font-mono')
  })

  test('CodeReplayPlayer has slider scrubber with range input', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/CodeReplayPlayer.vue')
    expect(src).toContain('type="range"')
    expect(src).toContain('cr-slider')
    expect(src).toContain('cr-frame-label')
  })

  test('CodeReplayPlayer displays stats (duration, chars, lines)', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/CodeReplayPlayer.vue')
    expect(src).toContain('duration_seconds')
    expect(src).toContain('total_chars_added')
    expect(src).toContain('total_chars_deleted')
    expect(src).toContain('max_line_count')
  })

  test('CodeReplayPlayer cleans up timer on unmount', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/CodeReplayPlayer.vue')
    expect(src).toContain('beforeUnmount')
    expect(src).toContain('stopPlay')
    expect(src).toContain('clearInterval')
  })

  test('WhatIfBranchView.vue exists with AC/WA simulation buttons', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/WhatIfBranchView.vue')
    expect(src).toContain("name: 'WhatIfBranchView'")
    expect(src).toContain("simulate('ac')")
    expect(src).toContain("simulate('wa')")
    expect(src).toContain('getWhatIfBranch')
  })

  test('WhatIfBranchView shows KC delta with color coding', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/WhatIfBranchView.vue')
    expect(src).toContain('wi-kc-delta--pos')
    expect(src).toContain('wi-kc-delta--neg')
    expect(src).toContain('current_mastery')
    expect(src).toContain('simulated_mastery')
    expect(src).toContain('delta')
  })

  test('WhatIfBranchView has directional arrows based on delta', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/WhatIfBranchView.vue')
    expect(src).toContain('wi-kc-arrow--up')
    expect(src).toContain('wi-kc-arrow--down')
  })

  test('twin API has all replay and what-if methods', () => {
    const src = readSource('../../src/pages/oj/api/twin.js')
    expect(src).toContain('getCodeReplayEvents')
    expect(src).toContain('getWhatIfBranch')
    expect(src).toContain("'twin/replay/events'")
    expect(src).toContain("'twin/what-if'")
  })

  test('Both components use l99-tokens', () => {
    for (const file of ['CodeReplayPlayer.vue', 'WhatIfBranchView.vue']) {
      const src = readSource(`../../src/pages/oj/views/user/twin/${file}`)
      expect(src).toContain("@import '~@/styles/l99-tokens.less'")
    }
  })

  test('Both components have a11y region roles', () => {
    const replay = readSource('../../src/pages/oj/views/user/twin/CodeReplayPlayer.vue')
    expect(replay).toContain('role="region"')
    expect(replay).toContain('aria-label="代码重放播放器"')

    const whatif = readSource('../../src/pages/oj/views/user/twin/WhatIfBranchView.vue')
    expect(whatif).toContain('role="region"')
    expect(whatif).toContain('aria-label="What-If 分叉模拟"')
  })
})
