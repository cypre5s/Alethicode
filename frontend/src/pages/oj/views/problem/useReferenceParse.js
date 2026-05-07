/**
 * 从 Unified Chat 原始消息中提取引用 token。
 *
 * 支持的 token 与后端 `ReferenceResolver` 保持一致：`@card:<id>`、
 * `@last_<kind>`、`@courseware:<lpId>`、`@page:<lpId>:<n>`、
 * `@page:<chapter>.<pageNo>`、`@kc:<kcId>`、`@notebook:<entryId>`。
 *
 * `@page` 同时兼容 legacy 全局页号 (`@page:7` / `@page:42:7`) 与二级目录
 * (`@page:1.7` / `@page:42:1.7`)；二级目录的章号是当前课件包内 normalized
 * 文档按 sort_order 排序后的 1-based 序号。
 *
 * 这里只返回原始 token，实际引用解析由后端按当前会话完成。
 */

const CARD_REF = /@card:([A-Za-z0-9_-]+)/g
const SHORTHAND_REF = /@last_([a-z_]+)/g
const COURSEWARE_REF = /@courseware:(\d+)/g
const PAGE_REF = /@page:(?:(\d+):)?(\d+)(?:\.(\d+))?/g
const KC_REF = /@kc:([A-Za-z0-9_.\-]+)/g
const NOTEBOOK_REF = /@notebook:([A-Za-z0-9_\-]+)/g

const ALLOWED_SHORTHANDS = Object.freeze([
  'error', 'visualize', 'ideate', 'guide', 'review', 'post_ac', 'transfer'
])

export function parseReferences(text) {
  const raw = String(text || '')
  if (!raw) return []
  const tokens = []
  const seen = new Set()

  function pushToken(token) {
    if (!seen.has(token)) {
      seen.add(token)
      tokens.push(token)
    }
  }

  CARD_REF.lastIndex = 0
  let m
  while ((m = CARD_REF.exec(raw)) !== null) {
    pushToken(`@card:${m[1]}`)
  }

  SHORTHAND_REF.lastIndex = 0
  while ((m = SHORTHAND_REF.exec(raw)) !== null) {
    const kind = m[1]
    if (!ALLOWED_SHORTHANDS.includes(kind)) continue
    pushToken(`@last_${kind}`)
  }

  COURSEWARE_REF.lastIndex = 0
  while ((m = COURSEWARE_REF.exec(raw)) !== null) {
    pushToken(`@courseware:${m[1]}`)
  }

  PAGE_REF.lastIndex = 0
  while ((m = PAGE_REF.exec(raw)) !== null) {
    const lpPrefix = m[1] ? `${m[1]}:` : ''
    const tail = m[3] ? `${m[2]}.${m[3]}` : m[2]
    pushToken(`@page:${lpPrefix}${tail}`)
  }

  KC_REF.lastIndex = 0
  while ((m = KC_REF.exec(raw)) !== null) {
    pushToken(`@kc:${m[1]}`)
  }

  NOTEBOOK_REF.lastIndex = 0
  while ((m = NOTEBOOK_REF.exec(raw)) !== null) {
    pushToken(`@notebook:${m[1]}`)
  }

  return tokens
}

export default parseReferences
