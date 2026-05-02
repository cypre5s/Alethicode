/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource(relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('practice heatmap tooltip contract', () => {
  const source = readSource('../../src/pages/oj/components/skillProfile/PracticeHeatmap.vue')

  test('PracticeHeatmap should use element-plus tooltip component name', () => {
    expect(source).toContain('<el-tooltip')
    expect(source).not.toContain('<Tooltip')
    expect(source).not.toContain('</Tooltip>')
  })

  test('tooltip should hide quickly to avoid hover ghosting', () => {
    expect(source).toContain(':hide-after="0"')
    expect(source).toContain(':enterable="false"')
    expect(source).toContain(':persistent="false"')
    expect(source).toContain('transition=""')
  })

  test('weekday labels should reserve 7 rows to align with heatmap grid', () => {
    expect(source).toContain('v-for="(label, index) in weekdayRows"')
    expect(source).toContain("return ['Mon', '', 'Wed', '', 'Fri', '', '']")
  })
})
