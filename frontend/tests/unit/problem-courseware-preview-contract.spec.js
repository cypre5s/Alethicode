/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource (relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('problem courseware preview contract', () => {
  test('problem guide and error diagnosis cards should emit open-courseware-ref for previewable refs', () => {
    const guideSource = readSource('../../src/pages/oj/views/problem/cards/ProblemGuideCard.vue')
    const diagnosisSource = readSource('../../src/pages/oj/views/problem/cards/ErrorDiagnosisCard.vue')

    expect(guideSource).toContain("$emit('open-courseware-ref', ref)")
    expect(guideSource).toContain('isPreviewableCoursewareRef(ref)')
    expect(diagnosisSource).toContain("$emit('open-courseware-ref', ref)")
    expect(diagnosisSource).toContain('isPreviewableCoursewareRef(ref)')
  })

  test('unified agent panel should own the in-place courseware preview dialog and citation loading flow', () => {
    const panelSource = readSource('../../src/pages/oj/views/problem/UnifiedAgentPanel.vue')

    expect(panelSource).toContain("languagePackId: { type: [Number, String], default: null }")
    expect(panelSource).toContain('@open-courseware-ref="handleOpenCoursewareRef($event, item.data.courseware_refs || [])"')
    expect(panelSource).toContain('coursewarePreviewVisible')
    expect(panelSource).toContain('coursewarePreviewRefs')
    expect(panelSource).toContain('selectedCoursewareRef')
    expect(panelSource).toContain('api.getLanguagePackQaCitationPage')
    expect(panelSource).toContain('api.getLanguagePackQaPreviewUrl')
    expect(panelSource).toContain('课件预览')
    expect(panelSource).toContain('新标签打开完整课件')
  })

  test('problem page should pass problem language pack id into unified agent panel', () => {
    const problemSource = readSource('../../src/pages/oj/views/problem/Problem.vue')

    expect(problemSource).toContain(':language-pack-id="problem.language_pack_id || null"')
  })
})
