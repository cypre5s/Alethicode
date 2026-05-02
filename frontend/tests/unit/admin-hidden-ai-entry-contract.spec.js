/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource (relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('admin hidden ai entry contract', () => {
  test('admin side menu should not render the brand logo card', () => {
    const source = readSource('../../src/pages/admin/components/SideMenu.vue')

    expect(source).not.toContain('<div class="logo">')
    expect(source).not.toContain('Alethicode 管理台')
    expect(source).not.toContain('教学管理中枢')
  })

  test('admin side menu should place ai config under system admin instead of secrets', () => {
    const source = readSource('../../src/pages/admin/components/SideMenu.vue')

    const systemMenuStart = source.indexOf('<el-sub-menu v-if="adminManager" index="system">')
    const secretsMenuStart = source.indexOf('<el-sub-menu v-if="adminManager" index="secrets">')
    const judgeMenuStart = source.indexOf('<el-sub-menu v-if="adminManager" index="judge">')
    const aiConfigEntry = '<el-menu-item index="/secrets/ai">AI 服务配置</el-menu-item>'

    expect(systemMenuStart).toBeGreaterThan(-1)
    expect(secretsMenuStart).toBeGreaterThan(-1)
    expect(judgeMenuStart).toBeGreaterThan(-1)
    expect(source.indexOf(aiConfigEntry, systemMenuStart)).toBeGreaterThan(systemMenuStart)
    expect(source.indexOf(aiConfigEntry, systemMenuStart)).toBeLessThan(secretsMenuStart)
    expect(source.indexOf(aiConfigEntry, secretsMenuStart)).toBeLessThan(0)
    expect(source.indexOf(aiConfigEntry, judgeMenuStart)).toBeLessThan(0)
  })

  test('admin side menu should not expose mcmining or preflight entries', () => {
    const source = readSource('../../src/pages/admin/components/SideMenu.vue')

    expect(source).not.toContain('/mcmining-review')
    expect(source).not.toContain('/preflight-stats')
    expect(source).not.toContain('McMining 审核')
    expect(source).not.toContain('预检帮助率')
  })

  test('admin router should not register mcmining or preflight routes', () => {
    const source = readSource('../../src/pages/admin/router.js')

    expect(source).not.toContain("path: '/mcmining-review'")
    expect(source).not.toContain("name: 'mcmining-review'")
    expect(source).not.toContain("path: '/preflight-stats'")
    expect(source).not.toContain("name: 'preflight-stats'")
  })

  test('e2e replacement config should not include removed admin pages', () => {
    const source = readSource('../e2e/support/replacementConfig.js')

    expect(source).not.toContain('admin-mcmining-review')
    expect(source).not.toContain('/admin/mcmining-review')
    expect(source).not.toContain('admin-preflight-stats')
    expect(source).not.toContain('/admin/preflight-stats')
  })
})
