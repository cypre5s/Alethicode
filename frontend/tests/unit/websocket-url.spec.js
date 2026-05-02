/* eslint-env jest */

const fs = require('fs')
const path = require('path')
const babel = require('@babel/core')

function loadWebsocketUrlModule(windowMock) {
  const filePath = path.resolve(__dirname, '../../src/utils/websocketUrl.js')
  const source = fs.readFileSync(filePath, 'utf8')
  const transformed = babel.transformSync(source, {
    filename: filePath,
    presets: [require.resolve('@babel/preset-env')]
  })
  const module = { exports: {} }
  // eslint-disable-next-line no-new-func
  const fn = new Function('module', 'exports', 'require', 'window', transformed.code)
  fn(module, module.exports, require, windowMock)
  return module.exports
}

describe('websocket URL contract', () => {
  test('builds same-origin ws url for workflow on http page', () => {
    const { buildWebSocketUrl } = loadWebsocketUrlModule({
      location: {
        protocol: 'http:',
        host: 'localhost:8080'
      }
    })

    expect(buildWebSocketUrl('/ws/workflow/abc')).toBe('ws://localhost:8080/ws/workflow/abc')
  })

  test('builds same-origin wss url for workflow on https page', () => {
    const { buildWebSocketUrl } = loadWebsocketUrlModule({
      location: {
        protocol: 'https:',
        host: 'oj.example.com'
      }
    })

    expect(buildWebSocketUrl('ws/workflow/secure')).toBe('wss://oj.example.com/ws/workflow/secure')
  })

  test('builds classroom collab path without trailing slash', () => {
    const { buildClassroomCollabWebSocketPath } = loadWebsocketUrlModule({
      location: {
        protocol: 'http:',
        host: 'localhost:8080'
      }
    })

    expect(buildClassroomCollabWebSocketPath('session-42')).toBe('/ws/classroom/collab/session-42')
  })
})
