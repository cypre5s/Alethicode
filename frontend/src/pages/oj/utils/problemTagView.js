const TYPE_TAG_PREFIX = 'type:'
const KC_TAG_PREFIX = 'kc:'
const KC_DOUBLE_COLON_PREFIX = 'kc::'

function normalizeRawTag(tag) {
  if (tag === null || tag === undefined) {
    return ''
  }
  return String(tag).trim()
}

export function isTypeTag(tag) {
  const rawName = normalizeRawTag(tag).toLowerCase()
  return rawName.startsWith(TYPE_TAG_PREFIX)
}

export function toTagDisplayName(tag) {
  const rawName = normalizeRawTag(tag)
  const lowerName = rawName.toLowerCase()
  if (lowerName.startsWith(KC_DOUBLE_COLON_PREFIX)) {
    return rawName.slice(KC_DOUBLE_COLON_PREFIX.length)
  }
  if (lowerName.startsWith(KC_TAG_PREFIX)) {
    return rawName.slice(KC_TAG_PREFIX.length)
  }
  return rawName
}

export function toDisplayTag(tag) {
  const rawName = normalizeRawTag(tag)
  return {
    rawName,
    displayName: toTagDisplayName(rawName)
  }
}

export function normalizeDisplayTags(tags) {
  if (!Array.isArray(tags)) {
    return []
  }
  const seen = new Set()
  const list = []
  for (const tag of tags) {
    const rawName = normalizeRawTag(tag)
    if (!rawName || isTypeTag(rawName) || seen.has(rawName)) {
      continue
    }
    seen.add(rawName)
    list.push(toDisplayTag(rawName))
  }
  return list
}
