/**
 * Parse Unified Chat reference tokens out of a raw chat message.
 *
 * Supported tokens (mirrors backend ReferenceResolver):
 *   - @card:<id>     – explicit card anchor, e.g. @card:C-V-001
 *   - @last_<kind>   – shorthand: @last_error / @last_visualize / @last_ideate /
 *                      @last_guide / @last_review / @last_post_ac / @last_transfer
 *
 * Returns the raw token list (not the resolved cards). The backend resolves them
 * against the current session via /internal/ai-tutor/sessions/{id}/references/resolve.
 */

const CARD_REF = /@card:([A-Za-z0-9_-]+)/g
const SHORTHAND_REF = /@last_([a-z_]+)/g

const ALLOWED_SHORTHANDS = Object.freeze([
  'error', 'visualize', 'ideate', 'guide', 'review', 'post_ac', 'transfer'
])

export function parseReferences(text) {
  const raw = String(text || '')
  if (!raw) return []
  const tokens = []
  const seen = new Set()

  CARD_REF.lastIndex = 0
  let m
  while ((m = CARD_REF.exec(raw)) !== null) {
    const token = `@card:${m[1]}`
    if (!seen.has(token)) {
      seen.add(token)
      tokens.push(token)
    }
  }

  SHORTHAND_REF.lastIndex = 0
  while ((m = SHORTHAND_REF.exec(raw)) !== null) {
    const kind = m[1]
    if (!ALLOWED_SHORTHANDS.includes(kind)) continue
    const token = `@last_${kind}`
    if (!seen.has(token)) {
      seen.add(token)
      tokens.push(token)
    }
  }

  return tokens
}

export default parseReferences
