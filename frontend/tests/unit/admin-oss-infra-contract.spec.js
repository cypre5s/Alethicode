/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource (relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('admin oss infra contract', () => {
  test('infra page should expose temporal, unleash, nats and langfuse sections', () => {
    const source = readSource('../../src/pages/admin/views/general/SecretsInfra.vue')

    expect(source).toContain('Temporal')
    expect(source).toContain('Unleash')
    expect(source).toContain('NATS JetStream')
    expect(source).toContain('Langfuse')
    expect(source).toContain('FSRS')
  })

  test('admin api should expose ai infra overview endpoint', () => {
    const source = readSource('../../src/pages/admin/api.js')

    expect(source).toContain("ajax('admin/ai/infra/overview', 'get'")
  })

  test('admin api should expose language pack pipeline job endpoints', () => {
    const source = readSource('../../src/pages/admin/api.js')

    expect(source).toContain('startLanguagePackPipelineJob')
    expect(source).toContain('/pipeline-jobs')
    expect(source).toContain('/cancel')
    expect(source).toContain('/retry')
  })

  test('language pack init page should drive batch execution through pipeline jobs', () => {
    const source = readSource('../../src/pages/admin/views/general/LanguagePackInit.vue')

    expect(source).toContain('startLanguagePackPipelineJob')
    expect(source).toContain('getLanguagePackPipelineJob')
    expect(source).toContain('cancelLanguagePackPipelineJob')
    expect(source).toContain('retryLanguagePackPipelineJob')
    expect(source).toContain('pipelineJob')
  })
})
