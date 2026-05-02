/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource(relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('profile setting avatar upload contract', () => {
  test('avatar upload should use the shared oj api module instead of legacy this.$http', () => {
    const profileSettingSource = readSource('../../src/pages/oj/views/setting/children/ProfileSetting.vue')
    const apiSource = readSource('../../src/pages/oj/api.js')

    expect(profileSettingSource).not.toContain('this.$http(')
    expect(profileSettingSource).toContain('api.uploadAvatar(form)')

    expect(apiSource).toContain('uploadAvatar(formData)')
    expect(apiSource).toContain("return ajax('upload-avatar', 'post', {")
    expect(apiSource).toContain('data: formData')
  })
})
