import { createApp } from 'vue'
import App from './App.vue'
import store from '@/store'
import i18n from '@/i18n'
import ElementPlus, { ElMessage } from 'element-plus'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import 'element-plus/dist/index.css'

import router from './router'
import { GOOGLE_ANALYTICS_ID } from '@/utils/constants'
import analytics from '@/plugins/analytics'
import katex from '@/plugins/katex'

import Panel from './components/Panel.vue'
import IconBtn from './components/btn/IconBtn.vue'
import Save from './components/btn/Save.vue'
import Cancel from './components/btn/Cancel.vue'
import AdminPagination from '@/components/Pagination.vue'
import LanguagePackNfkCard from './components/LanguagePackNfkCard.vue'
import './style.less'
import './elementPlusTheme.less'
import { installLegacyIconBridge } from './legacyIconBridge'
import { FRONTEND_ENV } from '@/utils/runtimeEnv'
import { installDevRuntimeErrorFilter } from '@/utils/runtimeErrorFilter'
import { initNotifications } from '@/utils/notifications'
import { initSentry } from '@/utils/sentry'

const app = createApp(App)
initSentry({ app, router })

if (FRONTEND_ENV.isDevelopment) {
  installDevRuntimeErrorFilter()
}

installLegacyIconBridge(app)

app.use(analytics, {
  id: GOOGLE_ANALYTICS_ID,
  router
})
app.use(katex)
app.component(IconBtn.name, IconBtn)
app.component(Panel.name, Panel)
app.component(Save.name, Save)
app.component(Cancel.name, Cancel)
app.component('AdminPagination', AdminPagination)
app.component(LanguagePackNfkCard.name, LanguagePackNfkCard)

app.use(ElementPlus, { locale: zhCn })

app.config.globalProperties.$error = (msg) => {
  ElMessage({
    message: msg,
    type: 'error'
  })
}

app.config.globalProperties.$warning = (msg) => {
  ElMessage({
    message: msg,
    type: 'warning'
  })
}

app.config.globalProperties.$success = (msg) => {
  if (!msg) {
    ElMessage({
      message: '操作成功',
      type: 'success'
    })
  } else {
    ElMessage({
      message: msg,
      type: 'success'
    })
  }
}

initNotifications({
  error: app.config.globalProperties.$error,
  warning: app.config.globalProperties.$warning,
  success: app.config.globalProperties.$success
})

app.use(router)
app.use(store)
app.use(i18n)
app.mount('#app')
