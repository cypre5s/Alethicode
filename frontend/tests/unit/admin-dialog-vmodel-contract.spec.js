/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource (relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('admin dialog v-model contract', () => {
  test('KC management and AI variant review dialogs should use Vue3 modelValue binding', () => {
    const kcSource = readSource('../../src/pages/admin/views/general/KCManagement.vue')
    const aiSource = readSource('../../src/pages/admin/views/general/AIVariantReview.vue')

    expect(kcSource).not.toContain('v-model:visible')
    expect(aiSource).not.toContain('v-model:visible')

    expect(kcSource).toContain('v-model="editVisible"')
    expect(kcSource).toContain('v-model="problemsVisible"')
    expect(aiSource).toContain('v-model="previewVisible"')
  })
})
