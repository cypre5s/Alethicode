/* eslint-env jest */

const fs = require('fs')
const path = require('path')

const repoRoot = path.resolve(__dirname, '../../..')

function readSource(relativePath) {
  return fs.readFileSync(path.resolve(repoRoot, relativePath), 'utf-8')
}

describe('AI generated problems KC options contract', () => {
  it('exposes GET kc-options endpoint on backend controller', () => {
    const source = readSource('backend/src/main/java/com/alethicode/controller/classroom/ClassroomAiProblemController.java')
    expect(source).toContain('/api/classroom/{classroomId}/ai/generated-problems/kc-options')
    expect(source).toContain('aiGenerationKcOptions')
  })

  it('domain service forwards kc-options call to ClassroomKcResolver', () => {
    const interfaceSource = readSource('backend/src/main/java/com/alethicode/service/classroom/ClassroomAiProblemDomainService.java')
    expect(interfaceSource).toContain('aiGenerationKcOptions')

    const implSource = readSource('backend/src/main/java/com/alethicode/service/classroom/ClassroomAiProblemService.java')
    expect(implSource).toContain('classroomKcResolver.resolveLanguagePackId')
    expect(implSource).toContain('classroomKcResolver.listKcOptionsTree')
  })

  it('frontend api module exposes getAIGeneratedKcOptions wrapper', () => {
    const ojApi = readSource('frontend/src/pages/oj/api/classroom.js')
    expect(ojApi).toContain('getAIGeneratedKcOptions')
    expect(ojApi).toContain('ai/generated-problems/kc-options')

    const aggregator = readSource('frontend/src/api/modules/classroom.js')
    expect(aggregator).toContain('getAIGeneratedKcOptions')
  })

  it('AIGeneratedProblems.vue uses cascade selector and prefer_strategy radio', () => {
    const source = readSource('frontend/src/pages/oj/views/classroom/AIGeneratedProblems.vue')
    expect(source).toContain('el-cascader')
    expect(source).toContain('selectedKcCascade')
    expect(source).toContain('prefer_strategy')
    expect(source).toContain('lp_first')
    expect(source).toContain('llm_first')
    expect(source).toContain('lp_only')
    expect(source).toContain('llm_only')
    expect(source).toContain('showKcLabelModal')
    expect(source).not.toContain('target_kc_names')
  })

  it('publish flow forces KC labelling when target_kc_ids is empty', () => {
    const source = readSource('frontend/src/pages/oj/views/classroom/AIGeneratedProblems.vue')
    expect(source).toContain('publishProblem')
    expect(source).toContain('confirmKcLabel')
    expect(source).toContain('target_kc_ids')
    expect(source).toContain('cancelKcLabel')
  })
})

describe('Phase A backend schema migration V81', () => {
  it('V81 migration adds target_kc_ids and source_strategy columns', () => {
    const source = readSource('backend/src/main/resources/db/migration/V81__classroom_ai_problem_kc_link.sql')
    expect(source).toContain('target_kc_ids JSONB')
    expect(source).toContain("source_strategy")
    expect(source).toContain('lesson_llm')
    expect(source).toContain('lp_kc_pick')
  })
})
