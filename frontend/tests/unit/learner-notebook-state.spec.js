const fs = require('fs')
const path = require('path')
const babel = require('@babel/core')

function loadLearnerNotebookStateModule() {
  const filePath = path.resolve(__dirname, '../../src/pages/oj/views/user/learnerNotebookState.js')
  const source = fs.readFileSync(filePath, 'utf8')
  const transformed = babel.transformSync(source, {
    filename: filePath,
    presets: [require.resolve('@babel/preset-env')]
  })
  const module = { exports: {} }
  // eslint-disable-next-line no-new-func
  const fn = new Function('module', 'exports', 'require', transformed.code)
  fn(module, module.exports, require)
  return module.exports
}

const { toggleExpandedGroup, forceExpandGroup } = loadLearnerNotebookStateModule()

describe('Learner notebook group expansion state', () => {
  test('toggleExpandedGroup toggles existing key value', () => {
    const state = { '1|Python3': true }
    const next = toggleExpandedGroup(state, '1|Python3')
    expect(next).toEqual({ '1|Python3': false })
    expect(next).not.toBe(state)
  })

  test('toggleExpandedGroup creates missing key with true', () => {
    const state = {}
    const next = toggleExpandedGroup(state, '2|Java')
    expect(next).toEqual({ '2|Java': true })
    expect(next).not.toBe(state)
  })

  test('forceExpandGroup forces key to true', () => {
    const state = { '3|C++': false }
    const next = forceExpandGroup(state, '3|C++')
    expect(next).toEqual({ '3|C++': true })
    expect(next).not.toBe(state)
  })
})
