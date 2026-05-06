/**
 * Parse Unified Chat reference tokens out of a raw chat message.
 *
 * Supported tokens (mirrors backend ReferenceResolver):
 *   - @card:<id>            – explicit card anchor, e.g. @card:C-V-001
 *   - @last_<kind>          – shorthand: @last_error / @last_visualize / @last_ideate /
 *                             @last_guide / @last_review / @last_post_ac / @last_transfer
 *   - @courseware:<lpId>    – language pack reference for in-place RAG
 *   - @page:<lpId>:<n>      – language pack page (lpId optional in courseware QA page)
 *   - @kc:<kcId>            – knowledge concept node reference
 *   - @notebook:<entryId>   – LearnerNotebook entry reference
 *
 * Returns the raw token list (not the resolved cards). The backend resolves them
 * against the current session via /internal/ai-tutor/sessions/{id}/references/resolve.
 */

const CARD_REF = /@card:([A-Za-z0-9_-]+)/g
const SHORTHAND_REF = /@last_([a-z_]+)/g
const COURSEWARE_REF = /@courseware:(\d+)/g
const PAGE_REF = /@page:(?:(\d+):)?(\d+)/g
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
    pushToken(m[1] ? `@page:${m[1]}:${m[2]}` : `@page:${m[2]}`)
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
