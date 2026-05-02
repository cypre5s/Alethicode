import { encodeRouteCtx, decodeRouteCtx } from '@/utils/urlCipher'

const ADMIN_LANGUAGE_PACK_STORAGE_KEY = 'admin.current_language_pack_id'

function normalizeLanguagePackId(rawValue) {
  if (rawValue === null || rawValue === undefined) {
    return ''
  }
  const value = String(rawValue).trim()
  if (!value) {
    return ''
  }
  const parsed = Number(value)
  if (!Number.isInteger(parsed) || parsed <= 0) {
    return ''
  }
  return String(parsed)
}

function getPersistedLanguagePackId() {
  try {
    return normalizeLanguagePackId(window.localStorage.getItem(ADMIN_LANGUAGE_PACK_STORAGE_KEY))
  } catch (e) {
    return ''
  }
}

function persistLanguagePackId(languagePackId) {
  const normalized = normalizeLanguagePackId(languagePackId)
  try {
    if (!normalized) {
      window.localStorage.removeItem(ADMIN_LANGUAGE_PACK_STORAGE_KEY)
      return
    }
    window.localStorage.setItem(ADMIN_LANGUAGE_PACK_STORAGE_KEY, normalized)
  } catch (e) {
    // ignore localStorage write errors
  }
}

function listPackIds(packs) {
  if (!Array.isArray(packs)) {
    return []
  }
  return packs
    .map(item => normalizeLanguagePackId(item && item.id))
    .filter(Boolean)
}

function decodeLanguagePackQueryValue(encodedValue) {
  if (!encodedValue) return ''
  const decoded = decodeRouteCtx(encodedValue)
  return normalizeLanguagePackId(decoded.lp || encodedValue)
}

function resolveCurrentLanguagePackId(routeQueryValue, packs) {
  const packIds = new Set(listPackIds(packs))
  if (packIds.size === 0) {
    persistLanguagePackId('')
    return ''
  }

  const routeLanguagePackId = decodeLanguagePackQueryValue(routeQueryValue)
  if (routeLanguagePackId && packIds.has(routeLanguagePackId)) {
    persistLanguagePackId(routeLanguagePackId)
    return routeLanguagePackId
  }

  const persistedLanguagePackId = getPersistedLanguagePackId()
  if (persistedLanguagePackId && packIds.has(persistedLanguagePackId)) {
    persistLanguagePackId(persistedLanguagePackId)
    return persistedLanguagePackId
  }

  const firstPackId = listPackIds(packs)[0]
  persistLanguagePackId(firstPackId)
  return firstPackId
}

function appendLanguagePackQuery(routeQuery, languagePackId) {
  const nextQuery = Object.assign({}, routeQuery || {})
  const normalized = normalizeLanguagePackId(languagePackId)
  if (normalized) {
    nextQuery.language_pack_id = encodeRouteCtx({ lp: normalized })
  } else {
    delete nextQuery.language_pack_id
  }
  return nextQuery
}

export {
  normalizeLanguagePackId,
  getPersistedLanguagePackId,
  persistLanguagePackId,
  resolveCurrentLanguagePackId,
  appendLanguagePackQuery
}

