/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource(relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('classroom collaboration hidden contract', () => {
  test('classroom detail and router should not expose collaboration entry', () => {
    const detailSource = readSource('../../src/pages/oj/views/classroom/ClassroomDetail.vue')
    const routesSource = readSource('../../src/pages/oj/router/routes.js')

    expect(detailSource).not.toContain('label="协作编程"')
    expect(routesSource).not.toContain("path: '/classroom/collab'")
    expect(routesSource).not.toContain("name: 'collaborative-coding'")
  })
})
