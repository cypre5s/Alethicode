/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource (relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('oj loading contract', () => {
  test('oj entry should wire ui bridge loading handlers and gate analytics to production', () => {
    const source = readSource('../../src/pages/oj/index.js')

    expect(source).toContain('$loadingStart')
    expect(source).toContain('$loadingFinish')
    expect(source).toContain('ElLoading.service')
    expect(source).toContain('loadingInstance.close()')
    expect(source).toMatch(/if\s*\(FRONTEND_ENV\.isProduction\)/)
  })

  test('oj router should close loading on success and error paths', () => {
    const source = readSource('../../src/pages/oj/router/index.js')

    expect(source).toMatch(/router\.beforeEach\(async/)
    expect(source).toContain('try {')
    expect(source).toContain('catch (error)')
    expect(source).toContain('router.onError(() => {')
    expect(source).toContain('notify.loadingFinish()')
  })

  test('oj first paint should not depend on remote google fonts', () => {
    const source = readSource('../../index.html')

    expect(source).not.toContain('fonts.googleapis.com')
  })
})
