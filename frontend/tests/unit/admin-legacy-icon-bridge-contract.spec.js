/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource(relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('admin legacy icon bridge contract', () => {
  test('admin entry should install legacy icon bridge and bridge should cover critical legacy icon names', () => {
    const entrySource = readSource('../../src/pages/admin/index.js')
    const bridgeSource = readSource('../../src/pages/admin/legacyIconBridge.js')
    const styleSource = readSource('../../src/pages/admin/style.less')

    expect(entrySource).toContain("import { installLegacyIconBridge } from './legacyIconBridge'")
    expect(entrySource).toContain('installLegacyIconBridge(app)')

    expect(bridgeSource).toContain("'el-icon-search': 'el-icon-search'")
    expect(bridgeSource).toContain("'el-icon-refresh': 'el-icon-refresh'")
    expect(bridgeSource).toContain("'el-icon-fa-edit': 'el-icon-fa-edit'")
    expect(bridgeSource).toContain("'el-icon-fa-trash': 'el-icon-fa-trash'")

    expect(styleSource).toContain('.el-icon-search:before')
    expect(styleSource).toContain('.el-icon-refresh:before')
    expect(styleSource).toContain('.el-icon-plus:before')
  })
})
