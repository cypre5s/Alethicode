/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource (relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('problem editor language and icon contracts', () => {
  test('problem view should not hard-code Python as the default language preference', () => {
    const source = readSource('../../src/pages/oj/views/problem/Problem.vue')
    expect(source).toContain('pickDefaultLanguage (languages)')
    expect(source).not.toContain("const preferredLanguages = ['Python3', 'Python2', 'Python']")
    expect(source).toContain('this.language = this.pickDefaultLanguage(this.problem.languages)')
    expect(source).not.toContain('problem.languages = Array.isArray(problem.languages) ? problem.languages.sort() : []')
  })

  test('problem view should confirm language switch when AI tutor chat already has content and clear workflow context', () => {
    const source = readSource('../../src/pages/oj/views/problem/Problem.vue')
    expect(source).toContain('hasAiTutorConversationContent ()')
    expect(source).toContain('切换后将清空当前 AI 导学对话记录，是否继续？')
    expect(source).toContain('await this.clearAiConversationForLanguageSwitch()')
    expect(source).toContain('this.commitLanguageSwitch(newLang, { clearAiConversation: true })')
  })

  test('problem view keeps daylight theme as the startup default instead of restoring stale local theme', () => {
    const source = readSource('../../src/pages/oj/views/problem/Problem.vue')

    expect(source).toContain("theme: 'solarized'")
    expect(source).not.toContain('vm.theme = problemCode.theme')
    expect(source).not.toContain('theme: this.theme')
  })

  test('code editor header keeps visible reset and upload icons with aria labels', () => {
    const source = readSource('../../src/pages/oj/components/CodeMirror.vue')
    expect(source).toContain("default: ''")
    expect(source).toContain("theme: 'solarized'")
    expect(source).toContain('aria-label="重置代码"')
    expect(source).toContain('aria-label="上传代码文件"')
    expect(source).toContain('<Refresh />')
    expect(source).toContain('<Upload />')
    expect(source).not.toContain('<Icon type="ios-refresh"')
    expect(source).not.toContain('<Icon type="ios-cloud-upload-outline"')
  })
})
