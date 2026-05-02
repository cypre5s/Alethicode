/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource (relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('setting account localization contract', () => {
  test('settings sidebar no longer exposes security entry', () => {
    const settingsSource = readSource('../../src/pages/oj/views/setting/Settings.vue')
    expect(settingsSource).not.toContain("label: '安全设置'")
    expect(settingsSource).not.toContain("route: '/setting/security'")
  })

  test('settings sidebar resolves teacher identity from account admin type', () => {
    const settingsSource = readSource('../../src/pages/oj/views/setting/Settings.vue')
    expect(settingsSource).toContain("user.admin_type || profile.admin_type || ''")
    expect(settingsSource).toContain("if (adminType === 'Teacher') return '教师'")
  })

  test('setting routes no longer register security setting page', () => {
    const routesSource = readSource('../../src/pages/oj/router/routes.js')
    expect(routesSource).not.toContain("name: 'security-setting'")
    expect(routesSource).not.toContain("path: 'security'")
    expect(routesSource).not.toContain('Setting.SecuritySetting')
  })

  test('account setting form is fully localized in Chinese with aligned label width', () => {
    const accountSource = readSource('../../src/pages/oj/views/setting/children/AccountSetting.vue')
    expect(accountSource).toContain('label="旧密码"')
    expect(accountSource).toContain('label="新密码"')
    expect(accountSource).toContain('label="确认新密码"')
    expect(accountSource).toContain('label="当前密码"')
    expect(accountSource).toContain('label="旧邮箱"')
    expect(accountSource).toContain('label="新邮箱"')
    expect(accountSource).toContain('label-width="120px"')
    expect(accountSource).not.toContain('Old Password')
    expect(accountSource).not.toContain('New Password')
    expect(accountSource).not.toContain('Confirm New Password')
    expect(accountSource).not.toContain('Current Password')
    expect(accountSource).not.toContain('Old Email')
    expect(accountSource).not.toContain('New Email')
    expect(accountSource).not.toContain('Two Factor Auth')
  })
})
