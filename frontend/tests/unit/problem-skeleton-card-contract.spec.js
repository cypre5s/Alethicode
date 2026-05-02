/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource (relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('problem skeleton card contract', () => {
  test('skeleton card should use a native full-width button and keep append insertion event', () => {
    const source = readSource('../../src/pages/oj/views/problem/cards/SkeletonCodeCard.vue')

    expect(source).toContain('<button')
    expect(source).toContain('type="button"')
    expect(source).not.toContain('<Button')
    expect(source).toContain("@click.stop.prevent=\"handleInsertClick\"")
    expect(source).toContain('class="skeleton-btn-primary"')
    expect(source).toContain("emits: ['insert-code']")
    expect(source).toContain("this.$emit('insert-code', { code: this.data.skeleton, position: 'append' })")
  })

  test('skeleton code block should expand naturally without internal vertical scrolling', () => {
    const source = readSource('../../src/pages/oj/views/problem/cards/SkeletonCodeCard.vue')

    expect(source).not.toMatch(/max-height\s*:/)
    expect(source).not.toMatch(/overflow-y\s*:\s*auto/)
  })

  test('skeleton card header should not show a stage badge', () => {
    const source = readSource('../../src/pages/oj/views/problem/cards/SkeletonCodeCard.vue')

    expect(source).not.toContain('sk-stage-badge')
    expect(source).not.toContain('阶段 {{ stageLabel }}')
    expect(source).not.toContain('stageLabel')
  })
})
