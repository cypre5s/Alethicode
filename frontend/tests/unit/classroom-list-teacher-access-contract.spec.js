/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource(relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('classroom list teacher access contract', () => {
  test('Teacher admin_type should be treated as classroom creator', () => {
    const source = readSource('../../src/pages/oj/views/classroom/ClassroomList.vue')

    expect(source).toContain("['teacher', 'admin'].includes(profileRole)")
    expect(source).toContain("['teacher', 'admin'].includes(adminType)")
  })
})
