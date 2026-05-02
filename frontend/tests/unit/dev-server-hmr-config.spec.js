/* eslint-env jest */

const { HMR_WS_PATH, createDevServerConfig } = require('../../vite.shared.js')

describe('vite dev server hmr websocket config', () => {
  test('uses the same custom path on client and server', () => {
    const config = createDevServerConfig({
      port: 8080,
      apiTarget: 'http://127.0.0.1:8081',
      wsTarget: 'http://127.0.0.1:8081'
    })

    expect(HMR_WS_PATH).toBe('/hmr-ws')
    expect(config.hmr).toEqual({
      path: '/hmr-ws'
    })
  })

  test('keeps api, public, and websocket proxies on the same contract', () => {
    const config = createDevServerConfig({
      port: 8080,
      apiTarget: 'http://127.0.0.1:8081',
      wsTarget: 'http://127.0.0.1:8081'
    })

    expect(config.strictPort).toBe(true)
    expect(config.proxy['/api'].target).toBe('http://127.0.0.1:8081')
    expect(config.proxy['/public'].target).toBe('http://127.0.0.1:8081')
    expect(config.proxy['/ws'].target).toBe('http://127.0.0.1:8081')
    expect(config.proxy['/ws'].ws).toBe(true)
  })
})
