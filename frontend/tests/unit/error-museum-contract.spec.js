/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource (relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('L99 Sprint 04 — error museum contract', () => {
  test('ErrorMuseumView.vue exists and calls getMuseumPins', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/ErrorMuseumView.vue')
    expect(src).toContain('getMuseumPins')
    expect(src).toContain("name: 'ErrorMuseumView'")
  })

  test('ErrorMuseumView renders 3x3 grid with padding to 9 slots', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/ErrorMuseumView.vue')
    expect(src).toContain('paddedPins')
    expect(src).toContain('result.length < 9')
    expect(src).toContain('grid-template-columns: repeat(3, 240px)')
  })

  test('ErrorMuseumView supports unpin and update annotation', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/ErrorMuseumView.vue')
    expect(src).toContain('handleUnpin')
    expect(src).toContain('handleUpdateAnnotation')
    expect(src).toContain('unpinMuseumMemory')
    expect(src).toContain('updateMuseumPin')
  })

  test('ErrorMuseumExhibit.vue exists with empty and filled states', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/ErrorMuseumExhibit.vue')
    expect(src).toContain("name: 'ErrorMuseumExhibit'")
    expect(src).toContain('em-exhibit--empty')
    expect(src).toContain('钉一个你的错误')
  })

  test('ErrorMuseumExhibit supports inline annotation editing', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/ErrorMuseumExhibit.vue')
    expect(src).toContain('editingAnnotation')
    expect(src).toContain('em-exhibit__textarea')
    expect(src).toContain('maxlength="280"')
    expect(src).toContain('update-annotation')
  })

  test('ErrorMuseumExhibit has hover unpin button', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/ErrorMuseumExhibit.vue')
    expect(src).toContain('em-exhibit__unpin')
    expect(src).toContain('取消钉选')
  })

  test('twin API has all 4 museum methods', () => {
    const src = readSource('../../src/pages/oj/api/twin.js')
    expect(src).toContain('getMuseumPins')
    expect(src).toContain('pinMuseumMemory')
    expect(src).toContain('updateMuseumPin')
    expect(src).toContain('unpinMuseumMemory')
  })

  test('ErrorMuseumExhibit uses l99-tokens', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/ErrorMuseumExhibit.vue')
    expect(src).toContain("@import '~@/styles/l99-tokens.less'")
  })

  test('ErrorMuseumView has responsive breakpoints', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/ErrorMuseumView.vue')
    expect(src).toContain('@media (max-width: 1023px)')
    expect(src).toContain('@media (max-width: 575px)')
  })

  test('ErrorMuseumView has a11y region role', () => {
    const src = readSource('../../src/pages/oj/views/user/twin/ErrorMuseumView.vue')
    expect(src).toContain('role="region"')
    expect(src).toContain('aria-label="错误模式个人馆"')
  })
})
