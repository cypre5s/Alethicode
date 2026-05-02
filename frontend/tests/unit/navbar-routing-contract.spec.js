/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource (relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('navbar routing contract', () => {
  test('NavBar uses vue-router 4 resolve contract without legacy .route access', () => {
    const source = readSource('../../src/pages/oj/components/NavBar.vue')
    expect(source).toContain('const target = this.$router.resolve(route)')
    expect(source).toContain('if (!target || !target.fullPath) return')
    expect(source).toContain('if (target.fullPath === this.$route.fullPath) return')
    expect(source).toContain('this.$router.push(target.fullPath)')
    expect(source).not.toContain('this.$router.resolve(route).route')
  })
})
