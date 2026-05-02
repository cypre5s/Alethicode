/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource(relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('codemirror select binding contract', () => {
  test('oj code editor header selects should use Vue3 modelValue bindings', () => {
    const source = readSource('../../src/pages/oj/components/CodeMirror.vue')

    expect(source).toContain('<Select :model-value="language"')
    expect(source).toContain('@update:modelValue="onLangChange"')

    expect(source).not.toContain('<Select :value="language"')
    expect(source).not.toContain('@on-change="onLangChange"')
  })
})
