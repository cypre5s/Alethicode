/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource (relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('codemirror language completion contract', () => {
  test('oj code editor should keep completion profile synchronized with selected language', () => {
    const source = readSource('../../src/pages/oj/components/CodeMirror.vue')

    expect(source).toContain("completionProfile: ''")
    expect(source).toContain('syncLanguageOptions (language)')
    expect(source).toContain("this.setOption('completionProfile', language)")
    expect(source).toContain('this.syncLanguageOptions(this.language)')
  })

  test('oj code editor should delegate language switching to parent-controlled state', () => {
    const source = readSource('../../src/pages/oj/components/CodeMirror.vue')
    const onLangChangeMatch = source.match(/onLangChange\s*\(newVal\)\s*\{[\s\S]*?\n\s*\},/)
    expect(onLangChangeMatch).not.toBeNull()
    const onLangChangeBlock = onLangChangeMatch ? onLangChangeMatch[0] : ''

    expect(onLangChangeBlock).toContain("this.$emit('changeLang', newVal)")
    expect(onLangChangeBlock).not.toContain('syncLanguageOptions')
  })

  test('shared cm5 core should define explicit four-language completions and plain-text fallback', () => {
    const source = readSource('../../src/components/Cm5EditorCore.vue')

    expect(source).toContain('completeFromList')
    expect(source).toContain('const LANGUAGE_COMPLETION_MAP = {')
    expect(source).toContain('Python3:')
    expect(source).toContain('C:')
    expect(source).toContain("'C++':")
    expect(source).toContain('Java:')
    expect(source).toContain('this._completionCompartment = new Compartment()')
    expect(source).toContain("this._completionCompartment.of(this.resolveCompletionProfile(opts.completionProfile || opts.language || ''))")
    expect(source).toContain("} else if (name === 'completionProfile') {")
    expect(source).toContain('return []')
    expect(source).not.toContain('return python()')
  })
})
