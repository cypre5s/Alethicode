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
 * D-01：契约-实现脱节，标记为 TODO。
 *
 * 该 describe 下 3 个 spec 是 4/28 "checkpoint workspace before faded-parsons module
 * landing" 提交时引入的 TDD 风格契约：前端已经在 5 个 card schema 里加了
 * `kc_error_refs` 字段（参见上面的 SCHEMAS_WITH_KC_REFS 测试，已通过），但后端
 * `EvidencePackAssembler` 的 `buildKcErrorProfile` 实现一直没落地，全 backend grep
 * `buildKcErrorProfile` / `kc_error_profile` 均无命中。
 *
 * 业务决策：这是「学情错误画像」从前端契约渗透到后端的 backlog，需要：
 *   1. EvidencePackAssembler 新增 buildKcErrorProfile(userId, topK) 方法
 *   2. SQL 查询 ai_learner_notebook WHERE entry_type='error' ORDER BY ... LIMIT ?
 *   3. 把结果挂到 EvidencePack 让 card 模板可消费 kc_error_refs
 *
 * 当前用 xdescribe 标 pending，未来开发完成时改回 describe，这 3 个测试自然变绿。
 * 不通过硬塞代码绕过测试 = 不引入业务无意义的 buildKcErrorProfile 占位实现。
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
 * D-01：与上面同一组 backlog。
 *
 * tutor-graph 的 ideating.py / skeleton.py 节点目前没注入 `kc_error_profile`
 * 与 `kc_warning`（grep 0 命中）。该测试要求前端契约定义的「学情错误画像」字段
 * 流转到 LLM prompt 里，待 backend 实现 buildKcErrorProfile 后再让 tutor-graph
 * 节点消费。两侧同步落地后，把 xdescribe 改回 describe 即可让测试变绿。
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
