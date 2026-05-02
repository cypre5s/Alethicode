/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource(relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('runtime env usage contract', () => {
  test('known browser runtime files stop reading process.env directly', () => {
    const runtimeFiles = [
      '../../src/store/index.js',
      '../../src/pages/oj/App.vue',
      '../../src/pages/admin/views/Home.vue',
      '../../src/utils/sentry.js'
    ]

    runtimeFiles.forEach((relativePath) => {
      expect(readSource(relativePath)).not.toMatch(/process\.env\./)
    })
  })
})
