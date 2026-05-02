/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource (relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('problem editor skeleton insertion contract', () => {
  test('problem view should delegate skeleton insertion to editor appendCode helper', () => {
    const source = readSource('../../src/pages/oj/views/problem/Problem.vue')

    expect(source).toMatch(/insertSkeletonToEditor/)
    expect(source).toMatch(/editorRef\.appendCode\(skeletonText\)/)
    expect(source).toMatch(/buildAppendedEditorDocument/)
    expect(source).toMatch(/this\.setEditorDocument\(this\.buildAppendedEditorDocument\(this\.code, skeletonText\)\)/)
  })

  test('oj editor should move caret to a writable position after appending skeleton code', () => {
    const source = readSource('../../src/components/Cm5EditorCore.vue')

    expect(source).toMatch(/findPreferredAppendCursor/)
    expect(source).toMatch(/TODO\/i/)
    expect(source).toContain("const trailingNewline = /\\n$/.test(nextText) ? '' : '\\n'")
    expect(source).toMatch(/const preferredCursor = this\.findPreferredAppendCursor\(nextText, prefixText\)/)
    expect(source).toMatch(/selection: \{ anchor: lineInfo\.from \}/)
    expect(source).toMatch(/EditorView\.scrollIntoView/)
  })

  test('problem view should keep skeleton insertion aligned with editor writable-cursor behavior', () => {
    const source = readSource('../../src/pages/oj/views/problem/Problem.vue')

    expect(source).toMatch(/insertSkeletonToEditor/)
    expect(source).toMatch(/if \(position === 'append'\) \{\s*this\.insertSkeletonToEditor\(code\)/)
    expect(source).toMatch(/this\.\$nextTick\(\(\) => \{/)
  })
})
