/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource (relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('admin prune test case contract', () => {
  test('PruneTestCase formats timestamps locally instead of calling a missing global filter', () => {
    const source = readSource('../../src/pages/admin/views/general/PruneTestCase.vue')
    expect(source).toContain('formatTimestamp (value)')
    expect(source).toContain("return moment.unix(value).format('YYYY-MM-DD HH:mm:ss')")
    expect(source).toContain('{{ formatTimestamp(row.create_time) }}')
    expect(source).not.toContain('$filters.timestampFormat')
  })
})
