/* eslint-env jest */

const fs = require('fs')
const path = require('path')

const ojRoot = path.resolve(__dirname, '../../src/pages/oj')
const sourceFiles = []

function collectSourceFiles (dir) {
  const entries = fs.readdirSync(dir, { withFileTypes: true })
  for (const entry of entries) {
    const fullPath = path.join(dir, entry.name)
    if (entry.isDirectory()) {
      collectSourceFiles(fullPath)
      continue
    }
    if (/\.(vue|js)$/.test(entry.name)) {
      sourceFiles.push(fullPath)
    }
  }
}

function collectLegacyRenderViolations (source) {
  const lines = source.split(/\r?\n/)
  const violations = []
  let inRender = false
  let braceBalance = 0

  for (let index = 0; index < lines.length; index++) {
    const line = lines[index]

    if (/render\s*:\s*\(h\s*,\s*params\s*\)\s*=>/.test(line) || /render\s*:\s*function\s*\(h\s*,\s*params\s*\)/.test(line)) {
      inRender = true
      braceBalance = (line.match(/\{/g) || []).length - (line.match(/\}/g) || []).length
      continue
    }

    if (!inRender) {
      continue
    }

    const lineNumber = index + 1
    const trimmedLine = line.trim()

    if (/h\(\s*'(Button|Tag|Icon|Avatar)'/.test(line)) {
      violations.push(`${lineNumber}: component-string -> ${trimmedLine}`)
    }
    if (/\bprops\s*:\s*\{/.test(line)) {
      violations.push(`${lineNumber}: legacy-props -> ${trimmedLine}`)
    }
    if (/\bon\s*:\s*\{/.test(line)) {
      violations.push(`${lineNumber}: legacy-on -> ${trimmedLine}`)
    }
    if (/\bnativeOn\s*:/.test(line)) {
      violations.push(`${lineNumber}: nativeOn -> ${trimmedLine}`)
    }

    braceBalance += (line.match(/\{/g) || []).length - (line.match(/\}/g) || []).length
    if (braceBalance <= 0) {
      inRender = false
    }
  }

  return violations
}

describe('oj custom render contract', () => {
  test('render functions should not keep Vue2 component string or props/on config', () => {
    collectSourceFiles(ojRoot)

    const problems = []
    for (const filePath of sourceFiles.sort()) {
      const source = fs.readFileSync(filePath, 'utf8')
      const violations = collectLegacyRenderViolations(source)
      if (violations.length > 0) {
        problems.push(`${path.relative(path.resolve(__dirname, '../..'), filePath)}\n${violations.join('\n')}`)
      }
    }

    expect(problems).toEqual([])
  })
})
