/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource(relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('OJ language pack QA contract', () => {
  test('OJ api.js exposes language pack QA endpoints', () => {
    const apiIndex = readSource('../../src/pages/oj/api.js')
    const source = readSource('../../src/pages/oj/api/languagePack.js')
    expect(apiIndex).toContain("import languagePack from './api/languagePack'")
    expect(apiIndex).toContain('...languagePack')
    expect(source).toContain('getLanguagePackQaPacks')
    expect(source).toContain('createLanguagePackQaSession')
    expect(source).toContain('getLanguagePackQaSessions')
    expect(source).toContain('getLanguagePackQaMessages')
    expect(source).toContain('sendLanguagePackQaMessage')
    expect(source).toContain('submitLanguagePackQaFeedback')
    expect(source).toContain('getLanguagePackQaCitationPage')
    expect(source).toContain('getLanguagePackQaPreviewUrl')
  })

  test('preview helper generates path-based PDF URLs so page fragments reach the viewer', () => {
    const source = readSource('../../src/pages/oj/api/languagePack.js')
    expect(source).toContain('`/api/language-pack-qa/packs/${languagePackId}/documents/${documentId}/preview`')
    expect(source).not.toContain('/api/language-pack-qa/preview?ctx=')
  })

  test('OJ routes include language-pack-qa page', () => {
    const source = readSource('../../src/pages/oj/router/routes.js')
    expect(source).toContain("name: 'language-pack-qa'")
    expect(source).toContain("path: '/language-pack-qa'")
    expect(source).toContain('LanguagePackQaPage')
  })

  test('NavBar includes independent language pack QA entry', () => {
    const source = readSource('../../src/pages/oj/components/NavBar.vue')
    expect(source).toContain('index="/language-pack-qa"')
    expect(source).toContain('课件问答')
  })

  test('LanguagePackQaPage exists and uses dedicated QA APIs', () => {
    const source = readSource('../../src/pages/oj/views/languagepack/LanguagePackQaPage.vue')
    expect(source).toContain("name: 'LanguagePackQaPage'")
    expect(source).toContain('api.getVisibleLanguagePackList()')
    expect(source).toContain('api.getLanguagePackQaPacks()')
    expect(source).toContain('api.createLanguagePackQaSession')
    expect(source).toContain('api.getLanguagePackQaSessions')
    expect(source).toContain('api.getLanguagePackQaMessages')
    expect(source).toContain('api.sendLanguagePackQaMessage')
    expect(source).toContain('api.submitLanguagePackQaFeedback')
    expect(source).toContain('api.getLanguagePackQaCitationPage')
  })

  test('LanguagePackQaPage warns users not to ask OJ problem-solving questions here', () => {
    const source = readSource('../../src/pages/oj/views/languagepack/LanguagePackQaPage.vue')
    expect(source).toContain('不要在这里问 OJ 题目')
    expect(source).toContain('请回到题目页 AI')
    expect(source).toContain('looksLikeOjProblemQuestion')
    expect(source).toContain('notify.warning(this.ojQuestionGuardMessage)')
  })

  test('LanguagePackQaPage distinguishes visible packs from QA-ready packs', () => {
    const source = readSource('../../src/pages/oj/views/languagepack/LanguagePackQaPage.vue')
    expect(source).toContain('当前课程内容包暂不可问答')
    expect(source).toContain('尚未完成问答索引')
    expect(source).toContain('packOptionLabel')
    expect(source).toContain('currentPackIsQaReady')
  })

  test('LanguagePackQaPage hides answer video generation entry', () => {
    const source = readSource('../../src/pages/oj/views/languagepack/LanguagePackQaPage.vue')
    expect(source).not.toContain('生成讲解视频')
    expect(source).not.toContain('qa-beta-tag')
    expect(source).toContain('查看视频')
  })

  test('language pack views export the QA page', () => {
    const source = readSource('../../src/pages/oj/views/languagepack/index.js')
    expect(source).toContain('LanguagePackQaPage')
  })
})
