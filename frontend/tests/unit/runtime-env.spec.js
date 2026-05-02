/* eslint-env jest */

const fs = require('fs')
const path = require('path')
const babel = require('@babel/core')

function loadRuntimeEnvModule() {
  const filePath = path.resolve(__dirname, '../../src/utils/runtimeEnv.js')
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

describe('runtime env contract', () => {
  test('maps Vite env flags and version without reading process.env', () => {
    const { resolveFrontendEnv } = loadRuntimeEnvModule()
    const env = resolveFrontendEnv({
      DEV: true,
      PROD: false,
      VITE_APP_VERSION: '2026.03.29'
    })

    expect(env).toEqual({
      appVersion: '2026.03.29',
      isDevelopment: true,
      isProduction: false
    })
  })

  test('falls back to dev version string when VITE_APP_VERSION is missing', () => {
    const { resolveFrontendEnv } = loadRuntimeEnvModule()
    const env = resolveFrontendEnv({
      DEV: false,
      PROD: true
    })

    expect(env.appVersion).toBe('dev')
    expect(env.isProduction).toBe(true)
  })
})
