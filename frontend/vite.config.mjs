import fs from 'node:fs/promises'
import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'
import { VitePWA } from 'vite-plugin-pwa'
import {
  createAliasConfig,
  createDevServerConfig,
  resolveFrontend
} from './vite.shared.mjs'

function createHistoryFallbackPlugin() {
  return {
    name: 'alethicode-history-fallback',
    configureServer(server) {
      server.middlewares.use(async (req, res, next) => {
        if (!req.url || req.method !== 'GET') {
          next()
          return
        }

        const accept = req.headers.accept || ''
        const wantsHtml = accept.includes('text/html')
        const urlPath = req.url.split('?')[0]
        const isAssetRequest = urlPath.includes('.')
        const isBackendRequest = req.url.startsWith('/api') ||
          req.url.startsWith('/ws') ||
          req.url.startsWith('/public') ||
          req.url.startsWith('/@vite') ||
          req.url.startsWith('/node_modules')

        if (!wantsHtml || isAssetRequest || isBackendRequest) {
          next()
          return
        }

        const htmlPath = req.url.startsWith('/admin/')
          ? resolveFrontend('admin/index.html')
          : resolveFrontend('index.html')
        const rawHtml = await fs.readFile(htmlPath, 'utf8')
        const transformedHtml = await server.transformIndexHtml(req.url, rawHtml)
        res.statusCode = 200
        res.setHeader('Content-Type', 'text/html')
        res.end(transformedHtml)
      })
    }
  }
}

export default defineConfig(({ command }) => {
  const fallbackTarget = process.env.TARGET || 'http://127.0.0.1:8081'
  const apiTarget = process.env.API_TARGET || fallbackTarget
  const wsTarget = process.env.WS_TARGET || apiTarget
  const isDev = command === 'serve'
  const isProd = command === 'build'

  return {
    plugins: [
      vue(),
      createHistoryFallbackPlugin(),
      // 2C4G 容量优化（2026-04-30）：Service Worker 客户端缓存。把 GET 请求
      // 缓存推到每个学生的浏览器，跨用户走 Nginx proxy_cache，单用户走 SW，
      // 让 backend 收到的 GET 请求量降到原始的 ~10-20%。
      // dev 模式不启动 SW，避免与 vite HMR 冲突。
      VitePWA({
        registerType: 'autoUpdate',
        // 我们在 src/pages/oj/index.js 里手动 registerSW，以提供 onNeedRefresh /
        // onOfflineReady 用户提示。设 false 避免插件自动注入后导致重复注册。
        injectRegister: false,
        strategies: 'generateSW',
        srcDir: 'src',
        filename: 'sw.js',
        manifest: {
          name: 'Alethicode',
          short_name: 'Alethicode',
          description: 'Alethicode OJ + AI 导学',
          theme_color: '#ffffff',
          background_color: '#ffffff',
          display: 'standalone',
          start_url: '/',
          icons: [
            { src: '/favicon.ico', sizes: '64x64', type: 'image/x-icon' }
          ]
        },
        workbox: {
          globPatterns: ['**/*.{js,mjs,css,woff2,ttf,eot,ico,svg,png,jpg}'],
          globIgnores: ['**/admin/**'],
          maximumFileSizeToCacheInBytes: 5 * 1024 * 1024,
          navigateFallback: '/index.html',
          navigateFallbackDenylist: [/^\/api\//, /^\/ws\//, /^\/admin\//, /^\/public\//, /^\/grafana\//],
          cleanupOutdatedCaches: true,
          clientsClaim: true,
          skipWaiting: true,
          runtimeCaching: [
            {
              urlPattern: ({ request }) => ['style', 'script', 'worker', 'font', 'image'].includes(request.destination),
              handler: 'CacheFirst',
              options: {
                cacheName: 'static-assets',
                expiration: { maxEntries: 200, maxAgeSeconds: 30 * 24 * 60 * 60 },
                cacheableResponse: { statuses: [0, 200] }
              }
            },
            {
              urlPattern: ({ url, request }) =>
                request.method === 'GET' && /\/api\/(website|language)(\/|$)/.test(url.pathname),
              handler: 'StaleWhileRevalidate',
              options: {
                cacheName: 'api-config',
                expiration: { maxEntries: 32, maxAgeSeconds: 60 * 60 },
                cacheableResponse: { statuses: [0, 200] }
              }
            },
            {
              urlPattern: ({ url, request }) =>
                request.method === 'GET' && /\/api\/announcement(\/|$|\?)/.test(url.pathname + url.search),
              handler: 'StaleWhileRevalidate',
              options: {
                cacheName: 'api-announcement',
                expiration: { maxEntries: 64, maxAgeSeconds: 5 * 60 },
                cacheableResponse: { statuses: [0, 200] }
              }
            },
            {
              urlPattern: ({ url, request }) =>
                request.method === 'GET' && /\/api\/problem(\/|$|\?)/.test(url.pathname + url.search),
              handler: 'StaleWhileRevalidate',
              options: {
                cacheName: 'api-problem',
                expiration: { maxEntries: 256, maxAgeSeconds: 60 },
                cacheableResponse: { statuses: [0, 200] }
              }
            },
            {
              urlPattern: ({ url, request }) =>
                request.method === 'GET' && /\/api\/submission(\/|$|\?)/.test(url.pathname + url.search),
              handler: 'NetworkFirst',
              options: {
                cacheName: 'api-submission',
                networkTimeoutSeconds: 5,
                expiration: { maxEntries: 128, maxAgeSeconds: 10 },
                cacheableResponse: { statuses: [0, 200] }
              }
            },
            {
              urlPattern: ({ url, request }) =>
                request.method === 'GET' && /\/api\/profile(\/|$)/.test(url.pathname),
              handler: 'NetworkFirst',
              options: {
                cacheName: 'api-profile',
                networkTimeoutSeconds: 3,
                expiration: { maxEntries: 32, maxAgeSeconds: 60 },
                cacheableResponse: { statuses: [0, 200] }
              }
            },
            {
              urlPattern: ({ url }) => /^\/public\/avatar\//.test(url.pathname),
              handler: 'CacheFirst',
              options: {
                cacheName: 'avatar-images',
                expiration: { maxEntries: 512, maxAgeSeconds: 7 * 24 * 60 * 60 },
                cacheableResponse: { statuses: [0, 200] }
              }
            }
          ]
        },
        devOptions: {
          enabled: false
        }
      })
    ],
    appType: 'custom',
    resolve: {
      alias: createAliasConfig(),
      extensions: ['.mjs', '.js', '.ts', '.jsx', '.tsx', '.json', '.vue']
    },
    publicDir: resolveFrontend('public'),
    server: createDevServerConfig({
      port: process.env.PORT || 8080,
      apiTarget,
      wsTarget
    }),
    define: {
      __APP_VERSION__: JSON.stringify(process.env.VERSION || 'dev'),
      __APP_DEV__: JSON.stringify(isDev),
      __APP_PROD__: JSON.stringify(isProd),
      __VUE_OPTIONS_API__: true,
      __VUE_PROD_DEVTOOLS__: false,
      __VUE_I18N_FULL_INSTALL__: true,
      __VUE_I18N_LEGACY_API__: false,
      __INTLIFY_PROD_DEVTOOLS__: false
    },
    build: {
      outDir: resolveFrontend('dist'),
      assetsDir: 'static',
      minify: 'terser',
      terserOptions: {
        compress: {
          drop_console: true,
          drop_debugger: true
        },
        mangle: true
      },
      rollupOptions: {
        input: {
          index: resolveFrontend('index.html'),
          admin: resolveFrontend('admin/index.html')
        },
        output: {
          manualChunks(id) {
            if (id.includes('node_modules/element-plus')) {
              return 'vendor-element-plus'
            }
            if (id.includes('node_modules/@codemirror') || id.includes('node_modules/@lezer') || id.includes('node_modules/codemirror')) {
              return 'vendor-codemirror'
            }
            if (id.includes('node_modules/echarts') || id.includes('node_modules/zrender')) {
              return 'vendor-echarts'
            }
            if (id.includes('node_modules/katex')) {
              return 'vendor-katex'
            }
            if (id.includes('node_modules/highlight.js')) {
              return 'vendor-hljs'
            }
            if (id.includes('node_modules/d3')) {
              return 'vendor-d3'
            }
            if (id.includes('node_modules/pdfjs-dist')) {
              return 'vendor-pdfjs'
            }
            if (id.includes('node_modules/vue') || id.includes('node_modules/@vue') || id.includes('node_modules/vuex') || id.includes('node_modules/vue-router') || id.includes('node_modules/vue-i18n')) {
              return 'vendor-vue'
            }
          }
        }
      }
    }
  }
})
