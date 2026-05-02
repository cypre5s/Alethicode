/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource (relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('problem editor CM6 loading contract', () => {
  test('Cm5EditorCore uses CodeMirror 6 EditorView instead of Monaco', () => {
    const source = readSource('../../src/components/Cm5EditorCore.vue')

    expect(source).toContain("import { EditorState")
    expect(source).toContain("import { EditorView")
    expect(source).toContain("from '@codemirror/state'")
    expect(source).toContain("from '@codemirror/view'")
    expect(source).toContain("from '@codemirror/lang-python'")
    expect(source).not.toContain('monaco-editor')
    expect(source).not.toContain('@monaco-editor/loader')
  })

  test('vite config does not reference Monaco', () => {
    const source = readSource('../../vite.config.mjs')

    expect(source).not.toContain('monaco-editor')
    expect(source).not.toContain('vite-plugin-static-copy')
  })
})
