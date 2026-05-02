/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource (relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('admin ai teaching access contract', () => {
  test('teacher should keep ai teaching entry in side menu', () => {
    const source = readSource('../../src/pages/admin/components/SideMenu.vue')

    expect(source).toContain('canAccessAiTeaching ()')
    expect(source).toContain('return this.isAdminRole')
  })

  test('teacher should not be denied ai teaching routes in admin router', () => {
    const source = readSource('../../src/pages/admin/router.js')

    expect(source).toContain('const TEACHER_DENIED_ROUTE_NAMES = new Set([')
    expect(source).not.toContain("\n  'ai-variant-review',")
    expect(source).not.toContain("\n  'kc-management',")
    expect(source).not.toContain("\n  'language-pack-init',")
  })

  test('ai teaching admin pages should load published language packs instead of learner-visible packs', () => {
    const aiVariantSource = readSource('../../src/pages/admin/views/general/AIVariantReview.vue')
    const kcSource = readSource('../../src/pages/admin/views/general/KCManagement.vue')
    const languagePackInitSource = readSource('../../src/pages/admin/views/general/LanguagePackInit.vue')

    expect(aiVariantSource).toContain('api.getPublishedLanguagePacks()')
    expect(kcSource).toContain('api.getPublishedLanguagePacks()')
    expect(languagePackInitSource).toContain('api.getPublishedLanguagePacks()')
    expect(aiVariantSource).not.toContain('api.getVisibleLanguagePacks()')
    expect(kcSource).not.toContain('api.getVisibleLanguagePacks()')
    expect(languagePackInitSource).not.toContain('api.getVisibleLanguagePacks()')
  })
})
