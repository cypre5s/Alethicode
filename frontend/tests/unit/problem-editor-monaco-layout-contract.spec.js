/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource (relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('problem editor CM6 layout contract', () => {
  test('Cm5EditorCore uses CM6 EditorView with requestMeasure for layout refresh', () => {
    const source = readSource('../../src/components/Cm5EditorCore.vue')

    expect(source).toContain('requestMeasure')
    expect(source).toContain("new EditorView")
    expect(source).not.toContain('automaticLayout')
    expect(source).not.toContain('monaco')
  })
})
