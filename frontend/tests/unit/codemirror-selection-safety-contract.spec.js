/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource (relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('codemirror selection safety contract', () => {
  test('shared cm5 core should expose command-driven document APIs', () => {
    const source = readSource('../../src/components/Cm5EditorCore.vue')

    expect(source).toContain('setDocument (text, config = {})')
    expect(source).toContain('getDocument ()')
    expect(source).toContain('appendCode (text)')
    expect(source).toContain('insertCodeAtCursor (text)')
    expect(source).toContain('replaceLines (startLine, endLine, text)')
  })

  test('shared cm5 core should rebuild documents via CM6 dispatch and selection', () => {
    const source = readSource('../../src/components/Cm5EditorCore.vue')

    expect(source).toContain('this.editor.dispatch')
    expect(source).toContain('changes: { from: 0, to: this.editor.state.doc.length, insert: nextText }')
    expect(source).toContain('selection: { anchor:')
    expect(source).not.toContain('beforeSelectionChange')
    expect(source).not.toContain('mousedown')
    expect(source).not.toContain('display.')
  })

  test('shared cm5 core should use CM6 requestMeasure for layout refresh', () => {
    const source = readSource('../../src/components/Cm5EditorCore.vue')

    expect(source).toContain('requestMeasure')
  })

  test('oj editor wrapper should use command-driven cm5 core instead of bridge value sync', () => {
    const source = readSource('../../src/pages/oj/components/CodeMirror.vue')

    expect(source).toContain("import Cm5EditorCore from '@/components/Cm5EditorCore.vue'")
    expect(source).toContain('<Cm5EditorCore')
    expect(source).toContain(':initial-value="initialValue"')
    expect(source).toContain('@change="onEditorCodeChange"')
    expect(source).not.toContain('CodeMirrorBridge')
    expect(source).not.toContain('<codemirror :value=')
  })

  test('problem editor panel should initialize editor once instead of binding whole document as value prop', () => {
    const source = readSource('../../src/pages/oj/views/problem/CodeEditorPanel.vue')

    expect(source).toContain(':initial-value="code"')
    expect(source).toContain('@change="onCodeChange"')
    expect(source).not.toContain(':value="code"')
    expect(source).not.toContain('@update:value=')
  })
})
