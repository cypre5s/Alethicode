/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource (relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('admin language pack filter contract', () => {
  test('AIVariantReview exposes unified language pack filter state and params', () => {
    const source = readSource('../../src/pages/admin/views/general/AIVariantReview.vue')
    expect(source).toContain('selectedLanguagePackId')
    expect(source).toContain('languagePackOptions')
    expect(source).toContain('language_pack_id')
    expect(source).toContain('重置')
    expect(source).toContain('size="small"')
    expect(source).toContain('el-icon-refresh')
  })

  test('KCManagement exposes unified language pack filter state and params', () => {
    const source = readSource('../../src/pages/admin/views/general/KCManagement.vue')
    expect(source).toContain('selectedLanguagePackId')
    expect(source).toContain('languagePackOptions')
    expect(source).toContain('language_pack_id')
    expect(source).toContain('重置')
    expect(source).toContain('size="small"')
    expect(source).toContain('el-icon-refresh')
  })

  test('admin ProblemList exposes unified language pack filter state and params', () => {
    const source = readSource('../../src/pages/admin/views/problem/ProblemList.vue')
    expect(source).toContain('selectedLanguagePackId')
    expect(source).toContain('languagePackOptions')
    expect(source).toContain('language_pack_id')
    expect(source).toContain('重置')
    expect(source).toContain('size="small"')
    expect(source).toContain('el-icon-refresh')
  })

  test('ImportAndExport exposes unified language pack filter state and params', () => {
    const source = readSource('../../src/pages/admin/views/problem/ImportAndExport.vue')
    expect(source).toContain('selectedLanguagePackId')
    expect(source).toContain('languagePackOptions')
    expect(source).toContain('language_pack_id')
    expect(source).toContain('重置')
    expect(source).toContain('size="small"')
    expect(source).toContain('el-icon-refresh')
  })
})
