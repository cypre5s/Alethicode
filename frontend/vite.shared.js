const path = require('path')

const HMR_WS_PATH = '/hmr-ws'
function resolveFrontend(dir) {
  return path.resolve(__dirname, dir)
}

function createAliasConfig() {
  return {
    '@': resolveFrontend('src'),
    '@oj': resolveFrontend('src/pages/oj'),
    '@admin': resolveFrontend('src/pages/admin'),
    '~': resolveFrontend('src/components')
  }
}

function shouldReportRuntimeError(error) {
  const resizeObserverNoiseMessages = [
    'ResizeObserver loop limit exceeded',
    'ResizeObserver loop completed with undelivered notifications.'
  ]
  const message = typeof error === 'string'
    ? error
    : (error && typeof error.message === 'string' ? error.message : '')
  return !resizeObserverNoiseMessages.some((noise) => message.includes(noise))
}

function createDevServerConfig({ port, apiTarget, wsTarget }) {
  return {
    host: '0.0.0.0',
    port: Number(port || 8080),
    strictPort: true,
    hmr: {
      path: HMR_WS_PATH
    },
    proxy: {
      '/api': {
        target: apiTarget,
        changeOrigin: true
      },
      '/public': {
        target: apiTarget,
        changeOrigin: true
      },
      '/ws': {
        target: wsTarget,
        changeOrigin: true,
        ws: true
      }
    }
  }
}

module.exports = {
  HMR_WS_PATH,
  createAliasConfig,
  createDevServerConfig,
  resolveFrontend,
  shouldReportRuntimeError
}
