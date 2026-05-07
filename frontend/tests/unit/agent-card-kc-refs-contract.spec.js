/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSchema(name) {
  return JSON.parse(fs.readFileSync(
    path.resolve(__dirname, '../../../contracts/tutor_workflow/cards/' + name), 'utf-8'))
}

const SCHEMAS_WITH_KC_REFS = [
  'error_diagnosis.schema.json',
  'ideate_analysis.schema.json',
  'problem_guide.schema.json',
  'skeleton_code.schema.json',
  'transfer_problem.schema.json'
]

describe('kc_error_refs in card schemas', () => {
  SCHEMAS_WITH_KC_REFS.forEach(name => {
    it(`${name} should have optional kc_error_refs array`, () => {
      const schema = readSchema(name)
      expect(schema.properties).toHaveProperty('kc_error_refs')
      expect(schema.properties.kc_error_refs.type).toBe('array')
      if (schema.required) {
        expect(schema.required).not.toContain('kc_error_refs')
      }
    })
  })
})

/**
 * 锁定 `kc_error_refs` 后端画像契约的待实现范围。
 *
 * 当前用 xdescribe 挂起，避免为通过测试引入无业务意义的占位实现。
 */
xdescribe('EvidencePackAssembler kc_error_profile (backlog: 后端 buildKcErrorProfile 未实现)', () => {
  const repoRoot = path.resolve(__dirname, '../../..')
  const source = fs.readFileSync(
    path.resolve(repoRoot, 'backend/src/main/java/com/alethicode/service/aitutor/evidence/EvidencePackAssembler.java'), 'utf-8')

  it('should have buildKcErrorProfile method', () => {
    expect(source).toContain('buildKcErrorProfile')
    expect(source).toContain('kc_error_profile')
  })

  it('should limit to topK results', () => {
    expect(source).toContain('LIMIT ?')
  })

  it('should only query error entries', () => {
    expect(source).toContain("entry_type = 'error'")
  })
})

/**
 * 锁定 tutor-graph 消费 `kc_error_profile` 的待实现范围。
 *
 * 后端画像落地后，节点 prompt 必须消费前端契约定义的学情错误画像字段。
 */
xdescribe('tutor-graph nodes consume kc_error_profile (backlog: 节点未注入 kc_error_profile)', () => {
  const repoRoot = path.resolve(__dirname, '../../..')

  it('ideating.py should inject kc_error_profile into prompt', () => {
    const source = fs.readFileSync(
      path.resolve(repoRoot, 'services/tutor-graph/app/nodes/ideating.py'), 'utf-8')
    expect(source).toContain('kc_error_profile')
    expect(source).toContain('kc_warning')
  })

  it('skeleton.py should inject kc_error_profile into prompt', () => {
    const source = fs.readFileSync(
      path.resolve(repoRoot, 'services/tutor-graph/app/nodes/skeleton.py'), 'utf-8')
    expect(source).toContain('kc_error_profile')
    expect(source).toContain('kc_warning')
  })
})
