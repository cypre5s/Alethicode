/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource (relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('problem private AI UI contracts', () => {
  test('success overlay exposes close icon and gates learning summary by AI availability', () => {
    const source = readSource('../../src/pages/oj/views/problem/Problem.vue')
    expect(source).toContain('class="success-dismiss-btn"')
    expect(source).toContain('aria-label="关闭通过弹窗"')
    expect(source).toContain('v-if="isAITutorEnabledForCurrentProblem"')
    expect(source).toContain('查看学习总结')
  })

  test('debug input uses Vue3 modelValue contract instead of legacy change event bindings', () => {
    const source = readSource('../../src/pages/oj/views/problem/CodeEditorPanel.vue')
    expect(source).toContain(':model-value="debugInput"')
    expect(source).toContain('@update:modelValue="onDebugInputChange"')
    expect(source).not.toContain('@input="onDebugInputChange"')
    expect(source).not.toContain('@on-change="onDebugInputChange"')
  })
})
