import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)

export const HMR_WS_PATH = '/hmr-ws'

export function resolveFrontend(dir) {
  return path.resolve(__dirname, dir)
}

export function createAliasConfig() {
  return {
    '@': resolveFrontend('src'),
    '@oj': resolveFrontend('src/pages/oj'),
    '@admin': resolveFrontend('src/pages/admin'),
    '~': resolveFrontend('src/components')
  }
}

export function createDevServerConfig({ port, apiTarget, wsTarget }) {
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
