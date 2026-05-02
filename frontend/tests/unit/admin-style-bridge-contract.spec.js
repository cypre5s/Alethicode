/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource(relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('admin element plus style bridge contract', () => {
  test('admin entry imports elementPlusBridge and bridge covers key control families', () => {
    const entrySource = readSource('../../src/pages/admin/index.js')
    const bridgeSource = readSource('../../src/pages/admin/elementPlusTheme.less')

    expect(entrySource).toContain("import './elementPlusTheme.less'")
    expect(bridgeSource).toContain('--el-color-primary: #2563eb;')
    expect(bridgeSource).toContain('.el-input__wrapper')
    expect(bridgeSource).toContain('.el-table')
    expect(bridgeSource).toContain('.el-pagination')
    expect(bridgeSource).toContain('.el-dialog')
    expect(bridgeSource).toContain('.el-tabs__item')
  })
})
