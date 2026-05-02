import {
  TELEMETRY_EVENT_TYPES,
  ALLOWED_TAXONOMIES,
  CATEGORY_ALIAS_MAP,
  CATEGORY_LABEL_MAP,
  LANG_CLASS_MAP,
  TAG_CLASS_MAP
} from './notebookConstants.js'

export function isTelemetryEventType(rawType) {
  const key = (rawType || '').toString().trim()
  return TELEMETRY_EVENT_TYPES.has(key)
}

export function normalizeErrorTaxonomy(rawType) {
  const raw = (rawType || '').toString().trim()
  if (!raw) return 'unknown'
  const key = raw.toLowerCase().replace(/\s+/g, '_')
  if (isTelemetryEventType(key)) return ''
  const normalized = CATEGORY_ALIAS_MAP[key] || key
  return ALLOWED_TAXONOMIES.has(normalized) ? normalized : 'unknown'
}

export function getCategoryLabel(type) {
  const key = normalizeErrorTaxonomy(type) || 'unknown'
  return CATEGORY_LABEL_MAP[key] || '未分类'
}

export function getLangClass(lang) {
  return LANG_CLASS_MAP[lang] || 'lang-c'
}

export function getTagClass(type) {
  const key = normalizeErrorTaxonomy(type) || 'unknown'
  return TAG_CLASS_MAP[key] || 'tag-unknown'
}

export function formatTime(value) {
  if (!value) return '-'
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return value
  return d.toLocaleString()
}

/** 用 yyyy-mm-dd 作为日历分组 key（按学生本地时区）。 */
export function toLocalDateKey(value) {
  if (!value) return ''
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return ''
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

export function normalizeEntries(items) {
  const list = Array.isArray(items) ? items : []
  return list.map((item, idx) => {
    const normalizedTaxonomy = normalizeErrorTaxonomy(item && item.error_taxonomy)
    if (!normalizedTaxonomy) return null
    return Object.assign({}, item, {
      id: item && item.id ? item.id : ('entry-' + idx),
      error_taxonomy: normalizedTaxonomy
    })
  }).filter(Boolean)
}
