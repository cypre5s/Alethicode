/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource (relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('L99 Sprint 07 — metacognitive prediction contract', () => {
  test('PredictBeforeCodeCard.vue exists and calls submitMetacogPrediction', () => {
    const src = readSource('../../src/pages/oj/views/problem/PredictBeforeCodeCard.vue')
    expect(src).toContain("name: 'PredictBeforeCodeCard'")
    expect(src).toContain('submitMetacogPrediction')
  })

  test('PredictBeforeCodeCard has predict / submitted / collapsed states', () => {
    const src = readSource('../../src/pages/oj/views/problem/PredictBeforeCodeCard.vue')
    expect(src).toContain('v-if="!collapsed && !submitted"')
    expect(src).toContain('v-else-if="submitted && !collapsed"')
    expect(src).toContain('预测已记录')
    expect(src).toContain('跳过')
  })

  test('PredictBeforeCodeCard has skip option with gentle message', () => {
    const src = readSource('../../src/pages/oj/views/problem/PredictBeforeCodeCard.vue')
    expect(src).toContain('研究表明先预测的人学得更快')
  })

  test('MetacognitiveMapView.vue exists and calls getMetacogMap', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/MetacognitiveMapView.vue')
    expect(src).toContain("name: 'MetacognitiveMapView'")
    expect(src).toContain('getMetacogMap')
  })

  test('MetacognitiveMapView shows min-data gate for < 5 predictions', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/MetacognitiveMapView.vue')
    expect(src).toContain('totalPredicts < 5')
    expect(src).toContain('个预测就能看到地图')
  })

  test('MetacognitiveMapView displays heat bars for misconceptions', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/MetacognitiveMapView.vue')
    expect(src).toContain('mc-map__heat-bar')
    expect(src).toContain('mc-map__heat-fill')
    expect(src).toContain('hotMisconceptions')
  })

  test('twin API has metacog methods', () => {
    const src = readSource('../../src/pages/oj/api/twin.js')
    expect(src).toContain('submitMetacogPrediction')
    expect(src).toContain('getMetacogMap')
  })

  test('PredictBeforeCodeCard uses l99-tokens', () => {
    const src = readSource('../../src/pages/oj/views/problem/PredictBeforeCodeCard.vue')
    expect(src).toContain("@import '~@/styles/l99-tokens.less'")
  })
})
