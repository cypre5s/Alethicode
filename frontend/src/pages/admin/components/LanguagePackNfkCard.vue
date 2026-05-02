<template>
  <div class="nfk-card">
    <div class="nfk-card-header">
      <div class="nfk-card-title">
        NFK 训练数据就绪度
        <el-tag
          :type="levelTagType"
          size="small"
          effect="dark"
          class="level-tag"
        >{{ readiness.readiness_level || '-' }}</el-tag>
      </div>
      <el-button
        size="small"
        @click="refresh"
        :loading="loading"
      >刷新</el-button>
    </div>

    <div v-if="loading" class="nfk-loading">加载中...</div>
    <div v-else-if="error" class="nfk-error">{{ error }}</div>
    <div v-else-if="!hasData" class="nfk-empty">暂无数据</div>
    <div v-else>
      <div class="nfk-metric-row">
        <div class="nfk-metric">
          <div class="nfk-metric-label">学生数</div>
          <div class="nfk-metric-value">{{ readiness.student_count || 0 }}</div>
        </div>
        <div class="nfk-metric">
          <div class="nfk-metric-label">题目数</div>
          <div class="nfk-metric-value">{{ readiness.problem_count || 0 }}</div>
        </div>
        <div class="nfk-metric">
          <div class="nfk-metric-label">KC 覆盖题数</div>
          <div class="nfk-metric-value">{{ readiness.covered_problem_count || 0 }}</div>
        </div>
        <div class="nfk-metric">
          <div class="nfk-metric-label">KC 数</div>
          <div class="nfk-metric-value">{{ readiness.kc_count || 0 }}</div>
        </div>
        <div class="nfk-metric">
          <div class="nfk-metric-label">交互数</div>
          <div class="nfk-metric-value">{{ readiness.interaction_count || 0 }}</div>
        </div>
        <div class="nfk-metric">
          <div class="nfk-metric-label">KC 覆盖率</div>
          <div class="nfk-metric-value">{{ formatCoverage(readiness.kc_coverage) }}</div>
        </div>
      </div>

      <div class="nfk-action-row" v-if="readiness.next_action">
        {{ readiness.next_action }}
      </div>

      <div class="nfk-download">
        <el-button
          size="small"
          type="primary"
          :disabled="!languagePackId || downloadingCsv"
          :loading="downloadingCsv"
          @click="downloadCsv"
        >下载训练数据 CSV</el-button>
        <span class="nfk-download-hint">
          下载后请上传到 AutoDL 训练；当前阶段不支持页面内触发训练。
        </span>
      </div>
    </div>
  </div>
</template>

<script>
import api from '../api.js'

export default {
  name: 'LanguagePackNfkCard',
  props: {
    languagePackId: {
      type: [Number, String, null],
      default: null
    }
  },
  data () {
    return {
      loading: false,
      downloadingCsv: false,
      error: '',
      readiness: {}
    }
  },
  computed: {
    hasData () {
      return this.readiness && Object.keys(this.readiness).length > 0
    },
    levelTagType () {
      const level = (this.readiness.readiness_level || '').toUpperCase()
      if (level === 'HOT') return 'success'
      if (level === 'WARM') return 'warning'
      if (level === 'COLD') return 'info'
      return 'info'
    }
  },
  watch: {
    languagePackId: {
      immediate: true,
      handler (value) {
        if (value === null || value === undefined || value === '') {
          this.readiness = {}
          this.error = ''
          return
        }
        this.refresh()
      }
    }
  },
  methods: {
    refresh () {
      if (this.languagePackId === null || this.languagePackId === undefined || this.languagePackId === '') {
        return
      }
      this.loading = true
      this.error = ''
      api.getNfkTrainingReadiness(this.languagePackId).then(res => {
        const data = (res && res.data && res.data.data) || {}
        this.readiness = data
      }).catch(err => {
        this.error = this.formatError(err)
        this.readiness = {}
      }).finally(() => {
        this.loading = false
      })
    },
    downloadCsv () {
      if (!this.languagePackId || this.downloadingCsv) return
      this.downloadingCsv = true
      const url = api.nfkTrainingDataDownloadUrl(this.languagePackId)
      window.open(url, '_blank', 'noopener')
      setTimeout(() => {
        this.downloadingCsv = false
      }, 2000)
    },
    formatCoverage (value) {
      const num = Number(value)
      if (!Number.isFinite(num)) return '-'
      return `${(num * 100).toFixed(1)}%`
    },
    formatError (err) {
      if (!err) return '加载失败'
      if (err.message) return err.message
      if (err.data && err.data.data) return String(err.data.data)
      return '加载失败'
    }
  }
}
</script>

<style scoped lang="less">
.nfk-card {
  padding: 12px 16px;
  background: #f8fafc;
  border: 1px solid rgba(148, 163, 184, 0.22);
  border-radius: 8px;
}
.nfk-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.nfk-card-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  display: flex;
  align-items: center;
  gap: 8px;
}
.level-tag {
  font-weight: 500;
}
.nfk-loading,
.nfk-error,
.nfk-empty {
  padding: 12px 0;
  color: #606266;
  font-size: 13px;
}
.nfk-error {
  color: #f56c6c;
}
.nfk-metric-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}
.nfk-metric {
  flex: 1 1 120px;
  min-width: 120px;
  padding: 10px 12px;
  background: #fff;
  border: 1px solid rgba(148, 163, 184, 0.12);
  border-radius: 6px;
}
.nfk-metric-label {
  font-size: 12px;
  color: #606266;
  margin-bottom: 4px;
}
.nfk-metric-value {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}
.nfk-action-row {
  margin-top: 12px;
  padding: 10px 12px;
  background: rgba(64, 158, 255, 0.08);
  border-left: 3px solid #409eff;
  border-radius: 4px;
  font-size: 13px;
  color: #303133;
}
.nfk-download {
  margin-top: 16px;
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.nfk-download-hint {
  font-size: 12px;
  color: #606266;
}
</style>
