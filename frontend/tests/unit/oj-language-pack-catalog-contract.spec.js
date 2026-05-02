/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource (relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('OJ language pack catalog contract', () => {
  test('LanguagePackCatalog.vue exists and has correct component name', () => {
    const source = readSource('../../src/pages/oj/views/languagepack/LanguagePackCatalog.vue')
    expect(source).toContain("name: 'LanguagePackCatalog'")
  })

  test('catalog page calls getLanguagePackList API on mount', () => {
    const source = readSource('../../src/pages/oj/views/languagepack/LanguagePackCatalog.vue')
    expect(source).toContain('api.getLanguagePackList()')
  })

  test('catalog page calls getLanguagePackDetail API for detail view', () => {
    const source = readSource('../../src/pages/oj/views/languagepack/LanguagePackCatalog.vue')
    expect(source).toContain('api.getLanguagePackDetail(packId)')
  })

  test('catalog page navigates to problem list with language_pack_id filter', () => {
    const source = readSource('../../src/pages/oj/views/languagepack/LanguagePackCatalog.vue')
    expect(source).toContain("name: 'problem-list'")
    expect(source).toContain('language_pack_id')
  })

  test('OJ api.js exposes language pack query endpoints', () => {
    const source = readSource('../../src/pages/oj/api.js')
    expect(source).toContain('getLanguagePackList')
    expect(source).toContain('getLanguagePackDetail')
    expect(source).toContain('getLanguagePackDocuments')
    expect(source).toContain('getLanguagePackChapters')
    expect(source).toContain('getLanguagePackPagePreview')
  })

  test('OJ routes include language-pack-catalog', () => {
    const source = readSource('../../src/pages/oj/router/routes.js')
    expect(source).toContain("name: 'language-pack-catalog'")
    expect(source).toContain("path: '/language-packs'")
    expect(source).toContain('LanguagePackCatalog')
  })

  test('NavBar includes language pack navigation entry', () => {
    const source = readSource('../../src/pages/oj/components/NavBar.vue')
    expect(source).toContain('index="/language-packs"')
    expect(source).toContain('课件问答助手')
  })

  test('ProblemList supports language_pack_id filter', () => {
    const source = readSource('../../src/pages/oj/views/problem/ProblemList.vue')
    expect(source).toContain('language_pack_id')
    expect(source).toContain('filterByLanguagePack')
    expect(source).toContain('languagePackLabel')
    expect(source).toContain('loadLanguagePacks')
  })

  test('views index.js exports LanguagePackCatalog', () => {
    const source = readSource('../../src/pages/oj/views/index.js')
    expect(source).toContain("export * from './languagepack'")
  })
})
