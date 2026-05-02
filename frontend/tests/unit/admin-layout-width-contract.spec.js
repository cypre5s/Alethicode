/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource(relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('admin layout width contract', () => {
  test('admin main area should reserve side menu width without page overflow risk', () => {
    const homeSource = readSource('../../src/pages/admin/views/Home.vue')

    expect(homeSource).toContain('width: calc(100% - 240px);')
    expect(homeSource).toContain('max-width: calc(100% - 240px);')
    expect(homeSource).toContain('min-width: 0;')
  })
})
