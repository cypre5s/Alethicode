/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource(relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

function walkFiles(dir) {
  const entries = fs.readdirSync(dir, { withFileTypes: true })
  return entries.flatMap(entry => {
    const fullPath = path.join(dir, entry.name)
    if (entry.isDirectory()) {
      return walkFiles(fullPath)
    }
    return [fullPath]
  })
}

describe('codemirror runtime contract', () => {
  test('admin and oj editor components should use shared cm5 core rather than legacy wrappers', () => {
    const adminSource = readSource('../../src/pages/admin/components/CodeMirror.vue')
    const ojSource = readSource('../../src/pages/oj/components/CodeMirror.vue')
    const coreSource = readSource('../../src/components/Cm5EditorCore.vue')

    expect(adminSource).not.toMatch(/vue-codemirror-lite/)
    expect(ojSource).not.toMatch(/vue-codemirror-lite/)
    expect(adminSource).toMatch(/Cm5EditorCore/)
    expect(ojSource).toMatch(/Cm5EditorCore/)
    expect(coreSource).toMatch(/import.*EditorView.*from\s+'@codemirror\/view'/)
  })

  test('legacy bridge should not remain referenced by active editor codepaths', () => {
    const adminSource = readSource('../../src/pages/admin/components/CodeMirror.vue')
    const ojSource = readSource('../../src/pages/oj/components/CodeMirror.vue')

    expect(adminSource).not.toMatch(/CodeMirrorBridge/)
    expect(ojSource).not.toMatch(/CodeMirrorBridge/)
  })

  test('legacy bridge file and unused wrapper dependency should be fully removed', () => {
    const bridgePath = path.resolve(__dirname, '../../src/components/CodeMirrorBridge.vue')
    const packageJson = JSON.parse(readSource('../../package.json'))
    const sourceRoot = path.resolve(__dirname, '../../src')
    const sourceFiles = walkFiles(sourceRoot)
      .filter(filePath => /\.(js|vue)$/.test(filePath))

    expect(fs.existsSync(bridgePath)).toBe(false)
    expect(packageJson.dependencies['vue-codemirror-lite']).toBeUndefined()

    sourceFiles.forEach(filePath => {
      const source = fs.readFileSync(filePath, 'utf8')
      expect(source).not.toMatch(/CodeMirrorBridge/)
      expect(source).not.toMatch(/vue-codemirror-lite/)
    })
  })

  test('shared cm5 runtime should declare codemirror as a direct frontend dependency', () => {
    const packageJson = JSON.parse(readSource('../../package.json'))

    expect(packageJson.dependencies.codemirror).toBeDefined()
  })

  test('shared cm5 runtime should define a dedicated light syntax palette for token colors', () => {
    const coreSource = readSource('../../src/components/Cm5EditorCore.vue')

    expect(coreSource).toMatch(/HighlightStyle\.define\(/)
    expect(coreSource).toMatch(/tags\.keyword/)
    expect(coreSource).toMatch(/tags\.string/)
    expect(coreSource).toMatch(/tags\.comment/)
    expect(coreSource).toMatch(/syntaxHighlighting\(solarizedLightHighlightStyle\)/)
  })
})
