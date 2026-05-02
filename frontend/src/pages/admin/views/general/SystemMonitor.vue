<template>
  <div class="view">
    <Panel title="系统监控">
      <el-alert
        :title="grafanaAlert"
        type="info"
        :closable="false"
        show-icon
        class="hint-alert"
      />

      <div class="surface-card">
        <div class="section-head">
          <h4>Grafana 监控面板</h4>
          <p>服务指标、链路状态与基础资源使用情况</p>
        </div>

        <div class="iframe-toolbar">
          <el-button size="small" @click="loadGrafanaConfig" :loading="grafanaConfigLoading">
            重载配置
          </el-button>
          <el-button size="small" @click="reloadGrafana">刷新面板</el-button>
          <el-button size="small" type="primary" @click="openGrafanaInNewTab">
            新标签页打开
          </el-button>
        </div>

        <div class="iframe-shell">
          <iframe
            :key="grafanaKey"
            :src="grafanaPath"
            title="Grafana Observability Dashboard"
            class="grafana-iframe"
          ></iframe>
        </div>
      </div>
    </Panel>
  </div>
</template>

<script>
import api from '../../api.js'

export default {
  name: 'SystemMonitor',
  data () {
    return {
      grafanaPath: '/grafana/',
      grafanaConfigSource: '',
      grafanaConfigLoading: false,
      grafanaKey: 0
    }
  },
  computed: {
    grafanaAlert () {
      if (!this.grafanaConfigSource) {
        return '监控面板与管理后台集成，首次访问若未登录 Grafana 会显示登录页。'
      }
      return `监控地址来源：${this.grafanaConfigSource}。首次访问若未登录 Grafana 会显示登录页。`
    }
  },
  mounted () {
    this.loadGrafanaConfig()
  },
  methods: {
    loadGrafanaConfig () {
      this.grafanaConfigLoading = true
      api.getObservabilityConfig().then(res => {
        const data = (res && res.data && res.data.data) || {}
        this.grafanaPath = this.normalizeGrafanaUrl(data.grafana_url)
        this.grafanaConfigSource = data.source || ''
        this.reloadGrafana()
      }).catch(() => {
        this.grafanaPath = '/grafana/'
        this.grafanaConfigSource = 'fallback'
      }).finally(() => {
        this.grafanaConfigLoading = false
      })
    },
    reloadGrafana () {
      this.grafanaKey += 1
    },
    openGrafanaInNewTab () {
      const resolved = new URL(this.grafanaPath, window.location.origin)
      window.open(resolved.href, '_blank', 'noopener')
    },
    normalizeGrafanaUrl (url) {
      const raw = typeof url === 'string' ? url.trim() : ''
      if (!raw) return '/grafana/'
      const isRelativePath = raw.startsWith('/')
      const isHttpUrl = raw.startsWith('http://') || raw.startsWith('https://')
      if (!isRelativePath && !isHttpUrl) return '/grafana/'
      return raw.endsWith('/') ? raw : `${raw}/`
    }
  }
}
</script>

<style scoped lang="less">
.view {
  display: flex;
  flex-direction: column;
  gap: 24px;
  background:
    radial-gradient(circle at 16% 12%, rgba(59, 130, 246, 0.14), transparent 30%),
    radial-gradient(circle at 84% 8%, rgba(147, 197, 253, 0.2), transparent 28%),
    linear-gradient(180deg, #f8fafc 0%, #f1f5f9 100%);
}

.hint-alert {
  margin-bottom: 14px;
}

.surface-card {
  padding: 14px;
  border-radius: 16px;
  border: 1px solid rgba(37, 99, 235, 0.14);
  background: linear-gradient(162deg, rgba(255, 255, 255, 0.96) 0%, rgba(248, 250, 252, 0.94) 100%);
  box-shadow: 0 16px 28px -24px rgba(15, 23, 42, 0.3);
}

.section-head {
  margin-bottom: 12px;
}

.section-head h4 {
  margin: 0;
  font-size: 14px;
  color: #0f172a;
}

.section-head p {
  margin: 4px 0 0 0;
  font-size: 12px;
  color: #64748b;
}

.iframe-toolbar {
  margin: 8px 0 12px 0;
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.iframe-shell {
  border: 1px solid rgba(37, 99, 235, 0.16);
  border-radius: 14px;
  overflow: hidden;
  background: linear-gradient(180deg, #f8fafc 0%, #eff6ff 100%);
  min-height: 70vh;
  margin-top: 6px;
}

.grafana-iframe {
  display: block;
  width: 100%;
  min-height: 70vh;
  border: 0;
  background: #fff;
}

@media (max-width: 900px) {
  .iframe-shell,
  .grafana-iframe {
    min-height: 65vh;
  }
}
</style>
