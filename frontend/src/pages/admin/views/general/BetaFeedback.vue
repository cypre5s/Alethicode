<template>
  <div class="beta-feedback view">
    <Panel title="公测反馈">
      <div class="bf-toolbar">
        <ElSelect
          v-model="filters.status"
          placeholder="状态"
          clearable
          style="width: 140px"
          @change="reload"
        >
          <ElOption
            v-for="opt in STATUS_OPTIONS"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </ElSelect>
        <ElSelect
          v-model="filters.severity"
          placeholder="严重程度"
          clearable
          style="width: 140px"
          @change="reload"
        >
          <ElOption
            v-for="opt in SEVERITY_OPTIONS"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </ElSelect>
        <ElSelect
          v-model="filters.type"
          placeholder="类型"
          clearable
          style="width: 200px"
          @change="reload"
        >
          <ElOption
            v-for="opt in TYPE_OPTIONS"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </ElSelect>
        <ElButton type="primary" plain @click="reload">刷新</ElButton>
      </div>

      <div class="feedback-summary-grid" aria-label="反馈统计">
        <div class="feedback-summary-card">
          <span class="feedback-summary-card__label">当前筛选总数</span>
          <strong class="feedback-summary-card__value">{{ summaryTotal }}</strong>
        </div>
        <div class="feedback-summary-card">
          <span class="feedback-summary-card__label">本页待处理</span>
          <strong class="feedback-summary-card__value">{{ currentPagePendingCount }}</strong>
        </div>
        <div class="feedback-summary-card">
          <span class="feedback-summary-card__label">本页高优先级</span>
          <strong class="feedback-summary-card__value">{{ currentPageHighPriorityCount }}</strong>
        </div>
        <div class="feedback-summary-card">
          <span class="feedback-summary-card__label">本页截图数</span>
          <strong class="feedback-summary-card__value">{{ currentPageScreenshotCount }}</strong>
        </div>
      </div>

      <ElTable
        v-loading="loading"
        element-loading-text="加载中"
        :data="items"
        :header-cell-style="{ textAlign: 'center' }"
        :cell-style="tableCellStyle"
        style="width: 100%"
      >
        <ElTableColumn prop="id" label="ID" width="80" align="center" />
        <ElTableColumn prop="username" label="用户" width="140" />
        <ElTableColumn label="类型" width="180">
          <template #default="scope">{{ typeLabel(scope.row.type) }}</template>
        </ElTableColumn>
        <ElTableColumn label="严重" width="120">
          <template #default="scope">
            <ElTag :type="severityTagType(scope.row.severity)">{{ severityLabel(scope.row.severity) }}</ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="描述" min-width="240">
          <template #default="scope">
            <span class="bf-desc">{{ truncate(scope.row.description, 80) }}</span>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="route" label="路由" width="180" />
        <ElTableColumn label="截图" width="90" align="center">
          <template #default="scope">{{ scope.row.attachment_count || 0 }}</template>
        </ElTableColumn>
        <ElTableColumn label="邮件" width="100" align="center">
          <template #default="scope">
            <ElTag :type="mailTagType(scope.row.mail_status)">{{ scope.row.mail_status }}</ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="创建时间" width="180">
          <template #default="scope">{{ localtime(scope.row.created_at) }}</template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="280" align="center" fixed="right">
          <template #default="scope">
            <div class="bf-actions">
              <ElSelect
                v-model="scope.row.status"
                size="small"
                class="bf-actions__status"
                @change="onStatusChange(scope.row, $event)"
              >
                <ElOption
                  v-for="opt in STATUS_OPTIONS"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </ElSelect>
              <ElButton size="small" link type="primary" @click="openDetail(scope.row)">详情</ElButton>
              <ElButton size="small" link @click="copyId(scope.row.id)">复制ID</ElButton>
            </div>
          </template>
        </ElTableColumn>
      </ElTable>

      <div class="panel-options">
        <AdminPagination
          :total="total"
          :current-page="currentPage"
          :page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          @update:currentPage="currentPage = $event"
          @update:pageSize="pageSize = $event"
          @change="handlePaginationChange"
        />
      </div>
    </Panel>

    <ElDrawer
      v-model="detailVisible"
      :title="detailTitle"
      direction="rtl"
      size="640px"
    >
      <div v-if="detail" class="bf-detail">
        <div class="bf-detail-row">
          <label>反馈 ID</label>
          <div>{{ detail.id }}</div>
        </div>
        <div class="bf-detail-row">
          <label>用户</label>
          <div>{{ detail.username }} (#{{ detail.reporter_user_id }})</div>
        </div>
        <div class="bf-detail-row">
          <label>类型 / 严重</label>
          <div>{{ typeLabel(detail.type) }} / {{ severityLabel(detail.severity) }}</div>
        </div>
        <div class="bf-detail-row">
          <label>路由</label>
          <div>{{ detail.route }}</div>
        </div>
        <div class="bf-detail-row">
          <label>题号 / 提交</label>
          <div>{{ detail.problem_id || '-' }} / {{ detail.submission_id || '-' }}</div>
        </div>
        <div class="bf-detail-row">
          <label>会话</label>
          <div>{{ detail.workflow_session_id || '-' }}</div>
        </div>
        <div class="bf-detail-row">
          <label>状态</label>
          <div>{{ detail.status }} <span v-if="detail.resolved_at">（{{ localtime(detail.resolved_at) }}）</span></div>
        </div>
        <div class="bf-detail-row">
          <label>邮件</label>
          <div>
            {{ detail.mail_status }}
            <span v-if="detail.mail_error" class="bf-mail-error">— {{ detail.mail_error }}</span>
          </div>
        </div>
        <div class="bf-detail-row">
          <label>描述</label>
          <pre class="bf-detail-desc">{{ detail.description || '（无）' }}</pre>
        </div>
        <div class="bf-detail-row">
          <label>浏览器</label>
          <pre class="bf-detail-json">{{ formatJson(detail.browser_meta) }}</pre>
        </div>
        <div class="bf-detail-row">
          <label>最近操作</label>
          <pre class="bf-detail-json">{{ formatJson(detail.recent_actions) }}</pre>
        </div>
        <div class="bf-detail-row">
          <label>截图</label>
          <div v-if="!detail.attachments || !detail.attachments.length" class="bf-detail-empty">
            （无截图）
          </div>
          <div v-else class="bf-detail-thumbs">
            <a
              v-for="att in detail.attachments"
              :key="att.id"
              :href="screenshotUrl(detail.id, att.id)"
              target="_blank"
              rel="noopener"
            >
              <img :src="screenshotUrl(detail.id, att.id)" :alt="att.file_name" />
            </a>
          </div>
        </div>
        <div class="bf-detail-actions">
          <ElButton type="primary" plain @click="copyDetailLink(detail.id)">复制反馈链接</ElButton>
        </div>
      </div>
    </ElDrawer>
  </div>
</template>

<script>
import api from '../../api.js'
import { utcToLocal } from '@/utils/time'

const STATUS_OPTIONS = [
  { value: 'pending', label: '待处理' },
  { value: 'triaging', label: '处理中' },
  { value: 'fixing', label: '修复中' },
  { value: 'resolved', label: '已解决' },
  { value: 'wontfix', label: '不予处理' }
]

const SEVERITY_OPTIONS = [
  { value: 'blocker', label: '完全阻塞' },
  { value: 'high', label: '严重' },
  { value: 'medium', label: '中等' },
  { value: 'low', label: '低' }
]

const TYPE_OPTIONS = [
  { value: 'cant_open', label: '打不开 / 进不去' },
  { value: 'button_dead', label: '按钮无响应' },
  { value: 'page_confusing', label: '页面看不懂' },
  { value: 'wrong_problem_or_answer', label: '题目/答案错误' },
  { value: 'ai_unclear', label: 'AI 不清楚' },
  { value: 'submit_wrong', label: '提交结果不对' },
  { value: 'other', label: '其他' }
]

export default {
  name: 'BetaFeedback',
  data () {
    return {
      items: [],
      total: 0,
      pageSize: 20,
      currentPage: 1,
      loading: false,
      filters: { status: '', severity: '', type: '' },
      detailVisible: false,
      detail: null,
      STATUS_OPTIONS,
      SEVERITY_OPTIONS,
      TYPE_OPTIONS
    }
  },
  computed: {
    detailTitle () {
      return this.detail ? `反馈 #${this.detail.id}` : '反馈详情'
    },
    tableCellStyle () {
      return { verticalAlign: 'middle' }
    },
    summaryTotal () {
      return this.total || 0
    },
    currentPagePendingCount () {
      return this.items.filter(item => item.status === 'pending').length
    },
    currentPageHighPriorityCount () {
      return this.items.filter(item => item.severity === 'blocker' || item.severity === 'high').length
    },
    currentPageScreenshotCount () {
      return this.items.reduce((sum, item) => sum + (Number(item.attachment_count) || 0), 0)
    }
  },
  mounted () {
    this.reload()
  },
  methods: {
    localtime: utcToLocal,
    reload () {
      this.currentPage = 1
      this.fetchPage()
    },
    handlePaginationChange ({ page, pageSize }) {
      this.currentPage = page
      this.pageSize = pageSize
      this.fetchPage()
    },
    fetchPage () {
      this.loading = true
      const params = {
        offset: (this.currentPage - 1) * this.pageSize,
        limit: this.pageSize
      }
      if (this.filters.status) params.status = this.filters.status
      if (this.filters.severity) params.severity = this.filters.severity
      if (this.filters.type) params.type = this.filters.type
      api.getBetaFeedbackList(params).then(res => {
        const data = (res.data && res.data.data) || {}
        this.items = Array.isArray(data.items) ? data.items : []
        this.total = data.total || 0
      }).finally(() => {
        this.loading = false
      })
    },
    onStatusChange (row, newStatus) {
      const oldStatus = row._lastStatus || row.status
      api.updateBetaFeedbackStatus(row.id, newStatus).then(() => {
        row._lastStatus = newStatus
        if (newStatus === 'resolved' || newStatus === 'wontfix') {
          row.resolved_at = new Date().toISOString()
        }
      }).catch(() => {
        row.status = oldStatus
      })
    },
    openDetail (row) {
      this.detail = null
      this.detailVisible = true
      api.getBetaFeedbackDetail(row.id).then(res => {
        this.detail = (res.data && res.data.data) || null
      })
    },
    screenshotUrl (reportId, attachmentId) {
      return api.getBetaFeedbackScreenshotUrl(reportId, attachmentId)
    },
    typeLabel (value) {
      const opt = TYPE_OPTIONS.find(o => o.value === value)
      return opt ? opt.label : value
    },
    severityLabel (value) {
      const opt = SEVERITY_OPTIONS.find(o => o.value === value)
      return opt ? opt.label : value
    },
    severityTagType (value) {
      switch (value) {
        case 'blocker': return 'danger'
        case 'high': return 'warning'
        case 'medium': return ''
        case 'low': return 'info'
        default: return ''
      }
    },
    mailTagType (value) {
      switch (value) {
        case 'sent': return 'success'
        case 'failed': return 'danger'
        case 'pending': return 'warning'
        case 'disabled': return 'info'
        default: return ''
      }
    },
    truncate (text, max) {
      if (!text) return ''
      return text.length > max ? text.slice(0, max) + '…' : text
    },
    formatJson (value) {
      if (value == null) return ''
      try {
        return JSON.stringify(value, null, 2)
      } catch {
        return String(value)
      }
    },
    async copyId (id) {
      await this.copyToClipboard(String(id))
      this.$success && this.$success(`已复制 ID #${id}`)
    },
    async copyDetailLink (id) {
      const url = `${window.location.origin}/admin/beta-feedback?id=${id}`
      await this.copyToClipboard(url)
      this.$success && this.$success('已复制反馈链接')
    },
    async copyToClipboard (text) {
      if (typeof navigator !== 'undefined' && navigator.clipboard && navigator.clipboard.writeText) {
        try {
          await navigator.clipboard.writeText(text)
          return
        } catch { /* fallthrough */ }
      }
      const textarea = document.createElement('textarea')
      textarea.value = text
      document.body.appendChild(textarea)
      textarea.select()
      try { document.execCommand('copy') } catch { /* silent */ }
      document.body.removeChild(textarea)
    }
  }
}
</script>

<style scoped lang="less">
.beta-feedback {
  padding: var(--space-4);
}

:deep(.el-table__header-wrapper th .cell) {
  white-space: nowrap;
}

.bf-toolbar {
  display: flex;
  gap: var(--admin-toolbar-gap);
  margin-bottom: var(--space-3);
  flex-wrap: wrap;
}

.feedback-summary-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: var(--space-3);
  margin-bottom: var(--space-4);
}

.feedback-summary-card {
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
  background: var(--bg-card);
  padding: var(--space-3);
  box-shadow: var(--shadow-xs);

  &__label {
    display: block;
    color: var(--text-secondary);
    font-size: var(--fs-sm);
    margin-bottom: var(--space-1);
  }

  &__value {
    color: var(--text-strong);
    font-size: 22px;
    line-height: 1.2;
  }
}

.bf-desc {
  word-break: break-word;
  white-space: pre-wrap;
  color: var(--text-primary);
}

.bf-actions {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  white-space: nowrap;

  &__status {
    width: 110px;
    flex: 0 0 110px;
  }

  :deep(.el-button) {
    margin-left: 0;
  }
}

.panel-options {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--space-3);
}

.bf-detail {
  padding: 0 var(--space-5) var(--space-5);

  &-row {
    display: flex;
    gap: var(--space-3);
    padding: var(--space-3) 0;
    border-bottom: 1px solid var(--border-default);

    label {
      flex: 0 0 100px;
      color: var(--text-secondary);
      font-size: var(--fs-base);
      padding-top: var(--space-1);
      font-weight: 500;
    }

    > div {
      flex: 1;
      min-width: 0;
      word-break: break-word;
      color: var(--text-primary);
    }
  }

  &-desc, &-json {
    background: var(--bg-panel);
    border: 1px solid var(--border-default);
    border-radius: var(--radius-sm);
    padding: var(--space-3);
    font-size: var(--fs-sm);
    line-height: var(--leading-body);
    white-space: pre-wrap;
    word-break: break-word;
    margin: 0;
    max-height: 240px;
    overflow: auto;
    font-family: var(--font-mono);
    color: var(--text-primary);
  }

  &-thumbs {
    display: flex;
    gap: var(--space-2);
    flex-wrap: wrap;

    img {
      width: 120px;
      height: 80px;
      object-fit: cover;
      border-radius: var(--radius-sm);
      border: 1px solid var(--border-default);
      cursor: zoom-in;
      transition: border-color var(--motion-base), box-shadow var(--motion-base);

      &:hover {
        border-color: var(--primary-color);
        box-shadow: var(--shadow-sm);
      }
    }
  }

  &-empty {
    color: var(--text-disabled);
    font-size: var(--fs-base);
  }

  &-actions {
    margin-top: var(--space-4);
    display: flex;
    gap: var(--space-2);
  }
}

.bf-mail-error {
  color: var(--color-danger);
  font-size: var(--fs-sm);
  margin-left: var(--space-1);
}
</style>
