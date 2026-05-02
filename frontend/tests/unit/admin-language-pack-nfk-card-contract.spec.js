/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource (relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('LanguagePackNfkCard contract', () => {
  const cardSource = readSource('../../src/pages/admin/components/LanguagePackNfkCard.vue')
  const apiSource = readSource('../../src/pages/admin/api.js')
  const registrySource = readSource('../../src/pages/admin/index.js')
  const hostSource = readSource('../../src/pages/admin/views/general/LanguagePackInit.vue')

  test('card template should render readiness metrics and download button', () => {
    expect(cardSource).toContain('class="nfk-card"')
    expect(cardSource).toContain('readiness.readiness_level')
    expect(cardSource).toContain('readiness.student_count')
    expect(cardSource).toContain('readiness.problem_count')
    expect(cardSource).toContain('readiness.kc_count')
    expect(cardSource).toContain('readiness.interaction_count')
    expect(cardSource).toContain('readiness.kc_coverage')
    expect(cardSource).toContain('下载训练数据 CSV')
  })

  test('card logic should wire to readiness api and download helper', () => {
    expect(cardSource).toContain('api.getNfkTrainingReadiness(')
    expect(cardSource).toContain('api.nfkTrainingDataDownloadUrl(')
    expect(cardSource).toContain("window.open(url, '_blank', 'noopener')")
    expect(cardSource).toContain('downloadingCsv')
    expect(cardSource).toContain(':loading="downloadingCsv"')
    expect(cardSource).toContain('setTimeout(() => {')
  })

  test('card should react to language pack id changes via watcher', () => {
    expect(cardSource).toContain('languagePackId')
    expect(cardSource).toMatch(/immediate:\s*true/)
  })

  test('api module should expose NFK readiness + download helpers', () => {
    expect(apiSource).toContain("ajax('admin/nfk/training-data/readiness', 'get'")
    expect(apiSource).toContain('nfkTrainingDataDownloadUrl')
    expect(apiSource).toContain('/api/admin/nfk/training-data/export?language_pack_id=')
  })

  test('admin bootstrap should globally register LanguagePackNfkCard', () => {
    expect(registrySource).toContain("import LanguagePackNfkCard from './components/LanguagePackNfkCard.vue'")
    expect(registrySource).toContain('app.component(LanguagePackNfkCard.name, LanguagePackNfkCard)')
  })

  test('LanguagePackInit should mount the NFK tab against the selected pack', () => {
    expect(hostSource).toContain('<el-tab-pane label="NFK 数据" name="nfk">')
    expect(hostSource).toContain('<LanguagePackNfkCard')
    expect(hostSource).toContain(':language-pack-id="selectedTask && selectedTask.language_pack ? selectedTask.language_pack.id : null"')
  })
})
