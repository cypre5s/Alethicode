/**
 * Composer 持久化层：草稿与历史输入用 localStorage 存活。
 *
 * scope 隔离 `tutor:<problemId>` 与 `qa:<lpId>`；localStorage 失败只告警，不阻断输入。
 */

const KEY_PREFIX = 'alethicode.composer'
const HISTORY_LIMIT = 50

function safeStorage() {
  try {
    if (typeof window === 'undefined' || !window.localStorage) return null
    return window.localStorage
  } catch {
    return null
  }
}

function draftKey(scope) {
  return `${KEY_PREFIX}.draft.${scope || 'default'}`
}

function historyKey(scope) {
  return `${KEY_PREFIX}.history.${scope || 'default'}`
}

export function readDraft(scope) {
  const ls = safeStorage()
  if (!ls) return ''
  try {
    const raw = ls.getItem(draftKey(scope))
    return raw == null ? '' : String(raw)
  } catch (err) {
    console.warn('[composerStorage] readDraft failed:', err && err.message)
    return ''
  }
}

export function writeDraft(scope, value) {
  const ls = safeStorage()
  if (!ls) return
  try {
    if (value == null || value === '') {
      ls.removeItem(draftKey(scope))
    } else {
      ls.setItem(draftKey(scope), String(value))
    }
  } catch (err) {
    console.warn('[composerStorage] writeDraft failed:', err && err.message)
  }
}

export function readHistory(scope) {
  const ls = safeStorage()
  if (!ls) return []
  try {
    const raw = ls.getItem(historyKey(scope))
    if (!raw) return []
    const parsed = JSON.parse(raw)
    if (!Array.isArray(parsed)) return []
    return parsed.filter(item => typeof item === 'string' && item.length)
  } catch (err) {
    console.warn('[composerStorage] readHistory failed:', err && err.message)
    return []
  }
}

export function pushHistory(scope, value) {
  const text = typeof value === 'string' ? value.trim() : ''
  if (!text) return readHistory(scope)
  const ls = safeStorage()
  const current = readHistory(scope)
  const deduped = current.filter(item => item !== text)
  deduped.push(text)
  const trimmed = deduped.length > HISTORY_LIMIT ? deduped.slice(-HISTORY_LIMIT) : deduped
  if (ls) {
    try {
      ls.setItem(historyKey(scope), JSON.stringify(trimmed))
    } catch (err) {
      console.warn('[composerStorage] pushHistory failed:', err && err.message)
    }
  }
  return trimmed
}

export function clearScope(scope) {
  const ls = safeStorage()
  if (!ls) return
  try {
    ls.removeItem(draftKey(scope))
    ls.removeItem(historyKey(scope))
  } catch (err) {
    console.warn('[composerStorage] clearScope failed:', err && err.message)
  }
}

export const __INTERNAL__ = {
  KEY_PREFIX,
  HISTORY_LIMIT,
  draftKey,
  historyKey
}
