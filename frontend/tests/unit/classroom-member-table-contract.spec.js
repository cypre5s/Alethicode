/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource(relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('classroom member table contract', () => {
  test('member table user column should not render avatar component', () => {
    const source = readSource('../../src/pages/oj/views/classroom/ClassroomDetail.vue')

    expect(source).not.toContain('Avatar as ViewAvatar')
    expect(source).not.toContain('h(ViewAvatar')
    expect(source).toContain('label="用户"')
  })
})
