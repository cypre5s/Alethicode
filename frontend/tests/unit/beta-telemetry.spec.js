/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource(relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('Beta telemetry source contract', () => {
  test('betaTelemetry.js exports init / record / getRecent / reportApiError / flush / flushSync', () => {
    const source = readSource('../../src/utils/betaTelemetry.js')
    expect(source).toContain('export function initBetaTelemetry')
    expect(source).toContain('export function recordEvent')
    expect(source).toContain('export function getRecentEvents')
    expect(source).toContain('export function reportApiError')
    expect(source).toContain('export async function flush')
    expect(source).toContain('export function flushSync')
  })

  test('flushSync uses navigator.sendBeacon to /api/beta/telemetry/events', () => {
    const source = readSource('../../src/utils/betaTelemetry.js')
    expect(source).toContain('navigator.sendBeacon')
    expect(source).toContain("'/api/beta/telemetry/events'")
  })

  test('record event payload is sanitized (string trim 500, JSON-safe deep copy)', () => {
    const source = readSource('../../src/utils/betaTelemetry.js')
    expect(source).toContain('sanitizePayload')
    expect(source).toContain('trimText(value, 500)')
    expect(source).toContain('JSON.parse(JSON.stringify(value))')
  })

  test('flush threshold is at most 20 events per batch and 5s interval', () => {
    const source = readSource('../../src/utils/betaTelemetry.js')
    expect(source).toContain('MAX_BATCH = 20')
    expect(source).toContain('FLUSH_INTERVAL_MS = 5000')
  })

  test('frontend_error listener captures both error event and unhandledrejection', () => {
    const source = readSource('../../src/utils/betaTelemetry.js')
    expect(source).toContain("'error'")
    expect(source).toContain("'unhandledrejection'")
    expect(source).toContain("'frontend_error'")
  })

  test('reportApiError trims message to 200 chars to keep telemetry payload small', () => {
    const source = readSource('../../src/utils/betaTelemetry.js')
    expect(source).toContain('reportApiError')
    expect(source).toContain('200')
    expect(source).toContain("'api_error'")
  })

  test('RECENT cap = 50 to bound memory across long sessions', () => {
    const source = readSource('../../src/utils/betaTelemetry.js')
    expect(source).toContain('MAX_RECENT = 50')
  })

  test('initBetaTelemetry is idempotent and ignores missing apiClient', () => {
    const source = readSource('../../src/utils/betaTelemetry.js')
    expect(source).toContain('if (initialized) return')
    expect(source).toContain('if (!apiClient)')
  })

  test('init accepts isAuthenticated gate so anonymous visitors do not hit /api/beta', () => {
    const source = readSource('../../src/utils/betaTelemetry.js')
    expect(source).toContain('isAuthenticated')
    expect(source).toMatch(/function\s+isAuthed\s*\(/)
  })

  test('recordEvent / flush / flushSync skip network when not authenticated', () => {
    const source = readSource('../../src/utils/betaTelemetry.js')
    const guards = source.match(/if \(!isAuthed\(\)\) return/g) || []
    expect(guards.length).toBeGreaterThanOrEqual(3)
    expect(source).toMatch(/export function recordEvent[\s\S]+?if \(!isAuthed\(\)\) return[\s\S]+?QUEUE\.push/)
    expect(source).toMatch(/export async function flush[\s\S]+?if \(!isAuthed\(\)\) return/)
    expect(source).toMatch(/export function flushSync[\s\S]+?if \(!isAuthed\(\)\) return[\s\S]+?navigator\.sendBeacon/)
  })
})

describe('OJ index.js wires telemetry + Web Vitals + auth gate', () => {
  test('index.js calls initBetaTelemetry and registers all five Web Vitals', () => {
    const source = readSource('../../src/pages/oj/index.js')
    expect(source).toContain('initBetaTelemetry')
    expect(source).toContain('onCLS')
    expect(source).toContain('onFCP')
    expect(source).toContain('onINP')
    expect(source).toContain('onLCP')
    expect(source).toContain('onTTFB')
    expect(source).toContain('reportBetaWebVital')
  })

  test('web-vitals sender is gated by isAuthenticated and never leaves a floating promise', () => {
    const source = readSource('../../src/pages/oj/index.js')
    expect(source).toContain('isAuthenticated: isAuthenticatedGetter')
    expect(source).toContain('if (!isAuthenticatedGetter()) return')
    expect(source).toMatch(/Promise\.resolve\(api\.reportBetaWebVital[\s\S]+?\)\.catch/)
  })
})

describe('OJ shared ajax respects silent flag on 401 to avoid login-modal flicker', () => {
  test('silent: true bypasses login modal dispatch and notify.error on http errors', () => {
    const source = readSource('../../src/pages/oj/api/shared.js')
    expect(source).toMatch(/}, res => {[\s\S]+?reject\(res\)[\s\S]+?if\s*\(silent\)\s*{[\s\S]+?return[\s\S]+?}/)
    expect(source).toContain("'login'")
  })
})

describe('httpClient.js response interceptor reports api_error', () => {
  test('error path forwards url/status/message into reportApiError', () => {
    const source = readSource('../../src/api/httpClient.js')
    expect(source).toContain('axios.interceptors.response.use')
    expect(source).toContain('reportApiError')
    expect(source).toContain('beta/telemetry/events')
    expect(source).toContain('beta/telemetry/web-vitals')
  })
})

describe('Submit/debug code key click telemetry', () => {
  test('useSubmission.js records feature_click on submitCode and debugCode', () => {
    const source = readSource('../../src/composables/problem/useSubmission.js')
    expect(source).toContain("recordBetaEvent")
    expect(source).toContain("'feature_click'")
    expect(source).toContain("name: 'submit_code'")
    expect(source).toContain("name: 'debug_code'")
  })
})
