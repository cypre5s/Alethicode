/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource (relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('problem debug input contract', () => {
  test('code editor panel should bind Input with modelValue instead of legacy value events', () => {
    const source = readSource('../../src/pages/oj/views/problem/CodeEditorPanel.vue')

    expect(source).toContain(':model-value="captchaCode"')
    expect(source).toContain('@update:modelValue="onCaptchaInput"')
    expect(source).toContain(':model-value="debugInput"')
    expect(source).toContain('@update:modelValue="onDebugInputChange"')
    expect(source).toContain(':model-value="debugOutput"')
    expect(source).not.toContain('<Input :value="captchaCode" @on-change="onCaptchaInput"')
    expect(source).not.toContain(':value="debugInput"')
    expect(source).not.toContain('@input="onDebugInputChange"')
    expect(source).not.toContain(':value="debugOutput"')
  })
})
