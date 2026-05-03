/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource (relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('L99 Phase D — Portable Twin contract', () => {
  test('S19: PublicTwinProfilePage.vue exists with hero + museum + footer', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/PublicTwinProfilePage.vue')
    expect(src).toContain("name: 'PublicTwinProfilePage'")
    expect(src).toContain('ptp-hero')
    expect(src).toContain('ptp-museum-grid')
    expect(src).toContain('Powered by')
    expect(src).toContain('你也来养一只学习孪生')
  })

  test('S19: PublicTwinProfilePage handles not-found state gracefully', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/PublicTwinProfilePage.vue')
    expect(src).toContain('notFound')
    expect(src).toContain('找不到这个孪生')
    expect(src).toContain('可能还没公开')
  })

  test('S19: PublicTwinProfilePage shows persona text and museum conditionally', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/PublicTwinProfilePage.vue')
    expect(src).toContain('v-if="profile.persona_text"')
    expect(src).toContain('v-if="profile.museum && profile.museum.length > 0"')
  })

  test('twin API has all Phase D methods', () => {
    const src = readSource('../../src/pages/oj/api/twin.js')
    expect(src).toContain('getPublicProfile')
    expect(src).toContain('updateTwinPrivacy')
    expect(src).toContain('generateSemesterReport')
    expect(src).toContain('downloadSemesterReportPdf')
    expect(src).toContain('getCredentials')
    expect(src).toContain('generateCredential')
    expect(src).toContain('exportTwinDump')
  })

  test('PublicTwinProfilePage uses l99-tokens', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/PublicTwinProfilePage.vue')
    expect(src).toContain("@import '~@/styles/l99-tokens.less'")
  })

  test('PublicTwinProfilePage has warm and inviting UI copy', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/PublicTwinProfilePage.vue')
    expect(src).toContain("关于 ta 的学习")
    expect(src).toContain("ta 的错误博物馆")
    expect(src).not.toContain('系统')
  })

  test('PublicTwinProfilePage shows museum cards with annotations', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/PublicTwinProfilePage.vue')
    expect(src).toContain('ptp-museum-card__summary')
    expect(src).toContain('ptp-museum-card__annotation')
  })
})
