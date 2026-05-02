const fs = require('fs')
const path = require('path')
const babel = require('@babel/core')

function loadLearningEventsTransportModule() {
  const filePath = path.resolve(__dirname, '../../src/utils/learningEventsTransport.js')
  const source = fs.readFileSync(filePath, 'utf8')
  const transformed = babel.transformSync(source, {
    filename: filePath,
    presets: [require.resolve('@babel/preset-env')]
  })
  const module = { exports: {} }
  // eslint-disable-next-line no-new-func
  const fn = new Function('module', 'exports', 'require', transformed.code)
  fn(module, module.exports, require)
  return module.exports
}

describe('learning events transport', () => {
  const originalFetch = global.fetch

  beforeEach(() => {
    document.cookie = 'csrftoken=test-csrf-token'
    global.fetch = jest.fn(() => Promise.resolve({ ok: true }))
  })

  afterEach(() => {
    global.fetch = originalFetch
    document.cookie = 'csrftoken='
  })

  test('posts batch with keepalive fetch and csrf header', async () => {
    const { postLearningEventsKeepalive } = loadLearningEventsTransportModule()
    const events = [{ event_type: 'problem_closed', problem_id: 1001 }]

    await postLearningEventsKeepalive(events)

    expect(global.fetch).toHaveBeenCalledTimes(1)
    expect(global.fetch).toHaveBeenCalledWith(
      '/api/ai/learning-events/batch',
      expect.objectContaining({
        method: 'POST',
        keepalive: true,
        credentials: 'same-origin',
        headers: expect.objectContaining({
          'Content-Type': 'application/json',
          'X-CSRFToken': 'test-csrf-token'
        }),
        body: JSON.stringify({ events })
      })
    )
  })

  test('falls back when keepalive fetch is unavailable', async () => {
    const { postLearningEventsKeepalive } = loadLearningEventsTransportModule()
    const fallback = jest.fn(() => Promise.resolve())
    global.fetch = undefined

    await postLearningEventsKeepalive([{ event_type: 'problem_closed' }], fallback)

    expect(fallback).toHaveBeenCalledTimes(1)
  })
})
