/**
 * 公测前端遥测客户端。
 *
 * 设计要点：
 *   - 单例。`initBetaTelemetry({ apiClient, router })` 由 OJ 端 `index.js` 启动时调用一次。
 *   - 事件队列每 5 秒或满 20 条 flush 一次；关闭页面时通过 `navigator.sendBeacon` 兜底。
 *   - 同步保留最近 50 条到 RECENT 队列，反馈表单提交时附在 `recentActions` 字段。
 *   - 严禁记录代码全文 / 聊天对话 / 密码 / token；前端错误只截 message 前 500 字。
 *   - 上报失败 silent，不影响主流程。
 */

const QUEUE = []
const RECENT = []
const MAX_BATCH = 20
const MAX_RECENT = 50
const FLUSH_INTERVAL_MS = 5000

let api = null
let initialized = false
let timer = null
let authGate = null

export function initBetaTelemetry({ apiClient, router, isAuthenticated } = {}) {
  if (initialized) return
  if (!apiClient) {
    return
  }
  initialized = true
  api = apiClient
  authGate = typeof isAuthenticated === 'function' ? isAuthenticated : null

  if (router && typeof router.afterEach === 'function') {
    router.afterEach((to) => {
      try {
        recordEvent('page_view', { route: to && to.fullPath ? to.fullPath : '' })
      } catch (_) { /* silent */ }
    })
  }

  if (typeof window !== 'undefined') {
    window.addEventListener('error', (event) => {
      try {
        recordEvent('frontend_error', {
          message: trimText(event && event.message ? String(event.message) : '', 500),
          src: trimText(event && event.filename ? String(event.filename) : '', 200),
          line: event ? event.lineno : null,
          col: event ? event.colno : null
        })
      } catch (_) { /* silent */ }
    })
    window.addEventListener('unhandledrejection', (event) => {
      try {
        const reason = event && event.reason
        const message = reason && reason.message ? String(reason.message) : String(reason)
        recordEvent('frontend_error', {
          type: 'unhandled_rejection',
          message: trimText(message, 500)
        })
      } catch (_) { /* silent */ }
    })
    window.addEventListener('beforeunload', flushSync)
  }

  timer = setInterval(flush, FLUSH_INTERVAL_MS)
}

export function recordEvent(eventType, payload) {
  const event = {
    eventType,
    route: currentRoute(),
    payload: sanitizePayload(payload),
    occurredAt: new Date().toISOString()
  }
  RECENT.push(event)
  if (RECENT.length > MAX_RECENT) RECENT.shift()
  if (!isAuthed()) return
  QUEUE.push(event)
  if (QUEUE.length >= MAX_BATCH) flush()
}

export function getRecentEvents(n) {
  const count = Number.isFinite(n) ? n : 20
  if (count <= 0) return []
  return RECENT.slice(-count)
}

export function reportApiError(url, status, message) {
  recordEvent('api_error', {
    url: typeof url === 'string' ? trimText(url, 200) : '',
    status,
    message: trimText(message == null ? '' : String(message), 200)
  })
}

export async function flush() {
  if (!QUEUE.length || !api || typeof api.reportBetaTelemetryBatch !== 'function') return
  if (!isAuthed()) return
  const batch = QUEUE.splice(0, MAX_BATCH)
  try {
    await api.reportBetaTelemetryBatch(batch)
  } catch (_) { /* silent */ }
}

export function flushSync() {
  if (!QUEUE.length) return
  if (!isAuthed()) return
  if (typeof navigator === 'undefined' || typeof navigator.sendBeacon !== 'function') return
  const events = QUEUE.splice(0)
  try {
    const blob = new Blob(
      [JSON.stringify({ events })],
      { type: 'application/json' }
    )
    navigator.sendBeacon('/api/beta/telemetry/events', blob)
  } catch (_) { /* silent */ }
}

export function _resetForTest() {
  QUEUE.length = 0
  RECENT.length = 0
  api = null
  initialized = false
  authGate = null
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

function isAuthed() {
  if (typeof authGate !== 'function') return true
  try {
    return !!authGate()
  } catch (_) {
    return false
  }
}

function currentRoute() {
  if (typeof window === 'undefined' || !window.location) return ''
  const path = window.location.pathname || ''
  const search = window.location.search || ''
  return path + search
}

function trimText(text, max) {
  if (typeof text !== 'string') return ''
  return text.length > max ? text.slice(0, max) : text
}

function sanitizePayload(payload) {
  if (payload == null || typeof payload !== 'object') return {}
  const out = {}
  for (const key of Object.keys(payload)) {
    const value = payload[key]
    if (value == null) {
      out[key] = value
    } else if (typeof value === 'string') {
      out[key] = trimText(value, 500)
    } else if (typeof value === 'number' || typeof value === 'boolean') {
      out[key] = value
    } else if (Array.isArray(value) || typeof value === 'object') {
      try {
        out[key] = JSON.parse(JSON.stringify(value))
      } catch (_) {
        out[key] = null
      }
    }
  }
  return out
}
