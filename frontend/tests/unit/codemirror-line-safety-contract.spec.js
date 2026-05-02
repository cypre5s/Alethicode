/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource(relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('codemirror line safety contract', () => {
  test('oj code editor should validate line-based coordinates before touching CodeMirror', () => {
    const source = readSource('../../src/pages/oj/components/CodeMirror.vue')

    expect(source).toMatch(/replaceLines[\s\S]*Number\.isInteger\(startLineNumber\)/)
    expect(source).toMatch(/replaceLines[\s\S]*Number\.isInteger\(endLineNumber\)/)
    expect(source).toMatch(/highlightErrorLines[\s\S]*Number\.isInteger\(rawLineNumber\)/)
    expect(source).toMatch(/applyAntiPatterns[\s\S]*getValidOneBasedLineNumber/)
  })

  test('ast hotspot navigation should validate destination line before setCursor', () => {
    const source = readSource('../../src/composables/problem/useAstVisualization.js')

    expect(source).toMatch(/Number\.isInteger\(lineNumberInt\)/)
    expect(source).toMatch(/core\.setCursor\(line, 0\)/)
  })

  test('skeleton insertion should position cursor via CM6 selection dispatch', () => {
    const coreSource = readSource('../../src/components/Cm5EditorCore.vue')
    const problemSource = readSource('../../src/pages/oj/views/problem/Problem.vue')

    expect(coreSource).toMatch(/findPreferredAppendCursor/)
    expect(coreSource).toMatch(/selection: \{ anchor:/)
    expect(coreSource).toMatch(/EditorView\.scrollIntoView/)

    expect(problemSource).toMatch(/editorRef\.appendCode\(skeletonText\)/)
  })

  test('problem page should refresh editor layout through the component wrapper instead of raw editor.refresh', () => {
    const problemSource = readSource('../../src/pages/oj/views/problem/Problem.vue')
    const editorSource = readSource('../../src/pages/oj/components/CodeMirror.vue')

    expect(problemSource).toContain('refreshEditorLayout')
    expect(problemSource).not.toContain('editorRef.editor.refresh()')
    expect(editorSource).toContain('refreshEditorLayout ()')
  })
})
