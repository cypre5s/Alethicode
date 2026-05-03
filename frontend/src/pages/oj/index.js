import { createApp, defineAsyncComponent } from 'vue'
import App from './App.vue'
import router from './router'
import store from '@/store'
import i18n from '@/i18n'
import { GOOGLE_ANALYTICS_ID } from '@/utils/constants'

import ElementPlus, { ElMessage, ElLoading } from 'element-plus'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import 'element-plus/dist/index.css'

import Panel from '@oj/components/Panel.vue'
import VerticalMenu from '@oj/components/verticalMenu/VerticalMenu.vue'
import VerticalMenuItem from '@oj/components/verticalMenu/VerticalMenuItem.vue'
import '@/styles/index.less'
import './elementPlusTheme.less'

import highlight from '@/plugins/highlight'
import analytics from '@/plugins/analytics'
import katex from '@/plugins/katex'
import clipboard from '@/plugins/clipboard'
import { FRONTEND_ENV } from '@/utils/runtimeEnv'
import { installDevRuntimeErrorFilter } from '@/utils/runtimeErrorFilter'
import { initNotifications } from '@/utils/notifications'
import { showSettingsToast } from '@/utils/settingsToast'
import api from '@oj/api'
import { initBetaTelemetry } from '@/utils/betaTelemetry'
import { initSentry } from '@/utils/sentry'

const app = createApp(App)
initSentry({ app, router })
const ECharts = defineAsyncComponent(() => import('@oj/components/ECharts.vue'))

if (FRONTEND_ENV.isDevelopment) {
  installDevRuntimeErrorFilter()
}

app.use(ElementPlus, { locale: zhCn })

app.use(clipboard)
app.use(highlight)
app.use(katex)
if (FRONTEND_ENV.isProduction) {
  app.use(analytics, {
    id: GOOGLE_ANALYTICS_ID,
    router,
    deferPageview: true
  })
}

app.component('ECharts', ECharts)
app.component(VerticalMenu.name, VerticalMenu)
app.component(VerticalMenuItem.name, VerticalMenuItem)
app.component('OjPanel', Panel)

let loadingInstance = null
app.config.globalProperties.$error = (s) => {
  const msg = s || 'An error occurred'
  ElMessage({ message: typeof msg === 'string' ? msg : String(msg), type: 'error', duration: 2000 })
}
app.config.globalProperties.$info = (s) => {
  const msg = s || 'Info'
  ElMessage({ message: typeof msg === 'string' ? msg : String(msg), type: 'info', duration: 2000 })
}
app.config.globalProperties.$success = (s) => {
  const msg = s || 'Success'
  ElMessage({ message: typeof msg === 'string' ? msg : String(msg), type: 'success', duration: 2000 })
}
app.config.globalProperties.$loadingStart = () => {
  loadingInstance = ElLoading.service({ fullscreen: true, background: 'rgba(255,255,255,0.6)' })
}
app.config.globalProperties.$loadingFinish = () => {
  if (loadingInstance) { loadingInstance.close(); loadingInstance = null }
}
app.config.globalProperties.$settingsToast = showSettingsToast

initNotifications({
  error: app.config.globalProperties.$error,
  info: app.config.globalProperties.$info,
  success: app.config.globalProperties.$success,
  loadingStart: app.config.globalProperties.$loadingStart,
  loadingFinish: app.config.globalProperties.$loadingFinish
})

app.use(router)
app.use(store)
app.use(i18n)

const isAuthenticatedGetter = () => !!(store && store.getters && store.getters.isAuthenticated)

initBetaTelemetry({ apiClient: api, router, isAuthenticated: isAuthenticatedGetter })

import('web-vitals').then(({ onCLS, onFCP, onINP, onLCP, onTTFB }) => {
  const sendVital = (m) => {
    if (!isAuthenticatedGetter()) return
    Promise.resolve(api.reportBetaWebVital({
      metric: m.name,
      value: m.value,
      rating: m.rating,
      navigationType: m.navigationType,
      route: window.location.pathname + window.location.search
    })).catch(() => { /* silent */ })
  }
  onCLS(sendVital)
  onFCP(sendVital)
  onINP(sendVital)
  onLCP(sendVital)
  onTTFB(sendVital)
}).catch(() => { /* web-vitals optional */ })

// 2C4G 容量优化（2026-04-30）：注册 Service Worker，让浏览器本地缓存大部分 GET API。
// dev 模式下 vite-plugin-pwa 不生成 SW，所以这里只在生产构建启用。
// 模块加载失败（dev/虚拟模块缺失）时静默跳过。
async function bootstrapServiceWorker () {
  if (typeof window === 'undefined' || !('serviceWorker' in navigator)) return
  if (!FRONTEND_ENV.isProduction) return
  try {
    const { registerSW } = await import('virtual:pwa-register')
    registerSW({
      immediate: true,
      onNeedRefresh () {
        ElMessage({
          message: '检测到新版本，建议刷新页面以获取最新功能',
          type: 'info',
          duration: 5000
        })
      },
      onOfflineReady () {
        ElMessage({ message: '已就绪：可离线浏览缓存内容', type: 'success', duration: 2000 })
      },
      onRegisteredSW (swUrl, registration) {
        if (!registration) return
        // 30 分钟主动检查一次新版本
        setInterval(() => registration.update().catch(() => { /* silent */ }), 30 * 60 * 1000)
      }
    })
  } catch (_e) {
    // dev / virtual:pwa-register 缺失时静默
  }
}
bootstrapServiceWorker()

app.mount('#app')
