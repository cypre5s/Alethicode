<template>
  <ElDialog
    :model-value="modelValue"
    :title="title"
    width="560px"
    :close-on-click-modal="false"
    :append-to-body="true"
    class="beta-feedback-dialog"
    @update:model-value="handleClose"
  >
    <div v-if="step === 'type'" class="bf-step">
      <div class="bf-step-title">问题类型</div>
      <div class="bf-options">
        <button
          v-for="opt in TYPE_OPTIONS"
          :key="opt.value"
          type="button"
          class="bf-option"
          :class="{ 'bf-option-active': form.type === opt.value }"
          @click="selectType(opt.value)"
        >
          <span class="bf-option-label">{{ opt.label }}</span>
        </button>
      </div>
    </div>

    <div v-else-if="step === 'severity'" class="bf-step">
      <div class="bf-step-title">影响程度</div>
      <div class="bf-options">
        <button
          v-for="opt in SEVERITY_OPTIONS"
          :key="opt.value"
          type="button"
          class="bf-option"
          :class="{ 'bf-option-active': form.severity === opt.value }"
          @click="selectSeverity(opt.value)"
        >
          <span class="bf-option-label">{{ opt.label }}</span>
        </button>
      </div>
    </div>

    <div v-else-if="step === 'detail'" class="bf-step">
      <div class="bf-step-title">详细描述</div>
      <ElInput
        v-model="form.description"
        type="textarea"
        :rows="5"
        :maxlength="2000"
        show-word-limit
        placeholder="请描述操作步骤、复现方式与预期结果。"
      />
      <div class="bf-screenshot-hint">
        可选上传屏幕截图（最多 3 张，单张不超过 5 MB）。截图快捷键：Windows Win+Shift+S，macOS Command+Shift+4。
      </div>
      <div
        class="bf-dropzone"
        :class="{ 'bf-dropzone-over': isDragOver }"
        @dragover.prevent="isDragOver = true"
        @dragleave.prevent="isDragOver = false"
        @drop.prevent="handleDrop"
        @click="triggerFilePicker"
      >
        <input
          ref="fileInput"
          type="file"
          accept="image/png,image/jpeg,image/webp"
          multiple
          hidden
          @change="handleFileChange"
        />
        <ElIcon class="bf-dropzone-icon"><Picture /></ElIcon>
        <span v-if="!screenshots.length">点击或拖拽上传图片</span>
        <span v-else>已选 {{ screenshots.length }} 张，可继续添加</span>
      </div>
      <div v-if="screenshots.length" class="bf-thumb-list">
        <div v-for="(file, index) in screenshots" :key="index" class="bf-thumb">
          <span class="bf-thumb-name">{{ file.name }}</span>
          <span class="bf-thumb-size">{{ formatSize(file.size) }}</span>
          <button type="button" class="bf-thumb-remove" @click.stop="removeScreenshot(index)">×</button>
        </div>
      </div>
    </div>

    <div v-else-if="step === 'success'" class="bf-step bf-success">
      <div class="bf-success-icon" aria-hidden="true">
        <ElIcon :size="32"><Check /></ElIcon>
      </div>
      <div class="bf-success-title">反馈已提交</div>
      <div class="bf-success-sub">研发团队会及时跟进，感谢您协助改进平台。</div>
      <div class="bf-success-actions">
        <ElButton type="primary" @click="openWjxFollowup">填写补充问卷（可选）</ElButton>
        <ElButton @click="handleClose(false)">关闭</ElButton>
      </div>
    </div>

    <template v-if="step !== 'success'" #footer>
      <ElButton v-if="step !== 'type'" @click="goBack">上一步</ElButton>
      <ElButton
        v-if="step === 'detail'"
        type="primary"
        :loading="submitting"
        :disabled="!canSubmit"
        @click="submit"
      >
        提交反馈
      </ElButton>
      <ElButton v-else @click="handleClose(false)">取消</ElButton>
    </template>
  </ElDialog>
</template>

<script>
import { mapState } from 'vuex'
import { Picture, Check } from '@element-plus/icons-vue'
import api from '@oj/api'
import { getRecentEvents } from '@/utils/betaTelemetry'

const TYPE_OPTIONS = [
  { value: 'cant_open', label: '页面无法访问' },
  { value: 'button_dead', label: '按钮或交互无响应' },
  { value: 'page_confusing', label: '界面信息表达不清' },
  { value: 'wrong_problem_or_answer', label: '题目或参考答案存在错误' },
  { value: 'ai_unclear', label: 'AI 解释不清晰' },
  { value: 'submit_wrong', label: '代码提交结果异常' },
  { value: 'other', label: '其他' }
]

const SEVERITY_OPTIONS = [
  { value: 'blocker', label: '功能不可用，无法继续使用' },
  { value: 'high', label: '严重影响使用流程' },
  { value: 'medium', label: '轻微影响使用' },
  { value: 'low', label: '改进建议' }
]

const MAX_SCREENSHOTS = 3
const MAX_SCREENSHOT_BYTES = 5 * 1024 * 1024
const ALLOWED_TYPES = ['image/png', 'image/jpeg', 'image/webp']
const WJX_DEFAULT_URL = 'https://v.wjx.cn/vm/mvsfyTf.aspx'

function emptyForm () {
  return {
    type: '',
    severity: '',
    description: ''
  }
}

export default {
  name: 'BetaFeedbackDialog',
  components: { Picture, Check },
  emits: ['update:modelValue', 'submitted'],
  props: {
    modelValue: { type: Boolean, default: false }
  },
  data () {
    return {
      step: 'type',
      form: emptyForm(),
      screenshots: [],
      submitting: false,
      lastReportId: null,
      isDragOver: false,
      TYPE_OPTIONS,
      SEVERITY_OPTIONS
    }
  },
  computed: {
    ...mapState(['website']),
    title () {
      switch (this.step) {
        case 'severity': return '提交反馈 (2/3)'
        case 'detail': return '提交反馈 (3/3)'
        case 'success': return '提交成功'
        default: return '提交反馈 (1/3)'
      }
    },
    canSubmit () {
      return !!this.form.type && !!this.form.severity && !this.submitting
    },
    wjxUrl () {
      const cfg = this.website && (this.website.beta_wjx_url || this.website.betaWjxUrl)
      return cfg || WJX_DEFAULT_URL
    },
    privacyNoticeVersion () {
      const cfg = this.website
      return (cfg && (cfg.beta_privacy_version || cfg.betaPrivacyVersion)) || ''
    }
  },
  watch: {
    modelValue (val) {
      if (val) {
        this.resetState()
      }
    }
  },
  methods: {
    resetState () {
      this.step = 'type'
      this.form = emptyForm()
      this.screenshots = []
      this.submitting = false
      this.lastReportId = null
      this.isDragOver = false
    },
    selectType (value) {
      this.form.type = value
      this.step = 'severity'
    },
    selectSeverity (value) {
      this.form.severity = value
      this.step = 'detail'
    },
    goBack () {
      if (this.step === 'severity') this.step = 'type'
      else if (this.step === 'detail') this.step = 'severity'
    },
    handleClose (val) {
      if (val === false || val === undefined) {
        this.$emit('update:modelValue', false)
      }
    },
    triggerFilePicker () {
      if (this.$refs.fileInput) this.$refs.fileInput.click()
    },
    handleFileChange (event) {
      const files = Array.from(event.target.files || [])
      this.appendScreenshots(files)
      event.target.value = ''
    },
    handleDrop (event) {
      this.isDragOver = false
      const files = Array.from(event.dataTransfer.files || [])
      this.appendScreenshots(files)
    },
    appendScreenshots (files) {
      for (const file of files) {
        if (this.screenshots.length >= MAX_SCREENSHOTS) {
          this.toastError(`最多只能上传 ${MAX_SCREENSHOTS} 张截图`)
          return
        }
        if (file.size > MAX_SCREENSHOT_BYTES) {
          this.toastError(`「${file.name}」超过 5 MB`)
          continue
        }
        if (!ALLOWED_TYPES.includes(file.type)) {
          this.toastError(`「${file.name}」不是允许的图片类型`)
          continue
        }
        this.screenshots.push(file)
      }
    },
    removeScreenshot (index) {
      this.screenshots.splice(index, 1)
    },
    toastError (message) {
      if (this.$error) this.$error(message)
      else if (this.$message) this.$message.error(message)
    },
    formatSize (bytes) {
      if (bytes < 1024) return `${bytes} B`
      if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
      return `${(bytes / 1024 / 1024).toFixed(2)} MB`
    },
    collectBrowserMeta () {
      if (typeof window === 'undefined') return {}
      const nav = window.navigator || {}
      const conn = nav.connection || {}
      return {
        ua: nav.userAgent || '',
        viewport: { w: window.innerWidth, h: window.innerHeight },
        dpr: window.devicePixelRatio || 1,
        lang: nav.language || '',
        online: typeof nav.onLine === 'boolean' ? nav.onLine : true,
        network: conn.effectiveType || ''
      }
    },
    collectRoute () {
      if (typeof window === 'undefined') return ''
      const path = window.location.pathname || ''
      const search = window.location.search || ''
      return path + search
    },
    collectIds () {
      const params = (this.$route && this.$route.params) || {}
      const query = (this.$route && this.$route.query) || {}
      const problemId = parseLong(params.problemID || params.problemId || query.problemID)
      const submissionId = parseLong(params.submissionID || params.submissionId || query.submissionID)
      const workflowSessionId = (this.$store && this.$store.getters && this.$store.getters.tutorWorkflowSessionId) || ''
      return { problemId, submissionId, workflowSessionId }
    },
    async submit () {
      if (!this.canSubmit) return
      this.submitting = true
      const ids = this.collectIds()
      const payload = {
        type: this.form.type,
        severity: this.form.severity,
        description: this.form.description,
        route: this.collectRoute(),
        problem_id: ids.problemId,
        submission_id: ids.submissionId,
        workflow_session_id: ids.workflowSessionId || null,
        browser_meta: this.collectBrowserMeta(),
        recent_actions: getRecentEvents(20),
        wjx_followup_opened: false,
        privacy_notice_version: this.privacyNoticeVersion
      }
      try {
        const res = await api.createBetaFeedback(payload, this.screenshots)
        const id = res && res.data && res.data.data ? res.data.data.id : null
        this.lastReportId = id
        this.step = 'success'
        this.$emit('submitted', { id })
      } catch (err) {
        const msg = (err && err.data && err.data.data) || (err && err.message) || '提交失败，请稍后再试'
        this.toastError(typeof msg === 'string' ? msg : '提交失败')
      } finally {
        this.submitting = false
      }
    },
    openWjxFollowup () {
      const url = new URL(this.wjxUrl)
      url.searchParams.set('source', 'alethicode')
      if (this.lastReportId != null) {
        url.searchParams.set('report_id', String(this.lastReportId))
      }
      if (typeof window !== 'undefined') {
        window.open(url.toString(), '_blank', 'noopener,noreferrer')
      }
    }
  }
}

function parseLong (value) {
  if (value === null || value === undefined || value === '') return null
  const n = Number(value)
  return Number.isFinite(n) ? n : null
}
</script>

<style scoped lang="less">
.bf-step {
  padding: var(--space-1) 0 var(--space-2);
}

.bf-step-title {
  font-size: var(--fs-lg);
  font-weight: 600;
  color: var(--text-strong);
  margin-bottom: var(--space-4);
}

.bf-options {
  display: grid;
  grid-template-columns: 1fr;
  gap: var(--space-2);
}

.bf-option {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  width: 100%;
  min-height: 60px;
  padding: 0 var(--space-4);
  border-radius: var(--radius-md);
  border: 1px solid var(--border-default);
  background: var(--bg-card);
  font-size: var(--fs-md);
  color: var(--text-primary);
  text-align: left;
  cursor: pointer;
  transition: border-color var(--motion-base), background var(--motion-base), color var(--motion-base);

  &:hover {
    border-color: var(--primary-500);
    background: var(--primary-50);
  }

  &:focus-visible {
    outline: 2px solid var(--primary-100);
    outline-offset: 2px;
  }
}

.bf-option-active {
  border-color: var(--primary-color);
  background: var(--primary-50);
  color: var(--primary-700);
  font-weight: 600;
}

.bf-screenshot-hint {
  font-size: var(--fs-sm);
  color: var(--text-secondary);
  line-height: var(--leading-body);
  margin: var(--space-3) 0 var(--space-2);
}

.bf-dropzone {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  border: 1px dashed var(--border-strong);
  border-radius: var(--radius-md);
  padding: var(--space-4);
  text-align: center;
  color: var(--text-secondary);
  font-size: var(--fs-base);
  background: var(--bg-base);
  cursor: pointer;
  transition: border-color var(--motion-base), background var(--motion-base), color var(--motion-base);

  &:hover {
    border-color: var(--primary-500);
    color: var(--primary-color);
    background: var(--primary-50);
  }
}

.bf-dropzone-icon {
  font-size: var(--fs-xl);
  color: var(--text-disabled);
}

.bf-dropzone-over {
  border-color: var(--primary-color);
  background: var(--primary-50);
  color: var(--primary-700);

  .bf-dropzone-icon {
    color: var(--primary-color);
  }
}

.bf-thumb-list {
  margin-top: var(--space-2);
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.bf-thumb {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-1) var(--space-2);
  border-radius: var(--radius-sm);
  background: var(--bg-panel);
  font-size: var(--fs-base);
  color: var(--text-primary);
}

.bf-thumb-name {
  flex: 1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.bf-thumb-size {
  color: var(--text-secondary);
  font-size: var(--fs-sm);
}

.bf-thumb-remove {
  width: 22px;
  height: 22px;
  border-radius: var(--radius-pill);
  border: 0;
  background: var(--border-default);
  color: var(--text-secondary);
  cursor: pointer;
  font-size: var(--fs-md);
  line-height: 1;
  transition: background var(--motion-base), color var(--motion-base);

  &:hover {
    background: var(--border-strong);
    color: var(--text-strong);
  }
}

.bf-success {
  text-align: center;
  padding: var(--space-5) 0 var(--space-2);
}

.bf-success-icon {
  width: 64px;
  height: 64px;
  border-radius: var(--radius-pill);
  background: rgba(16, 185, 129, 0.12);
  color: var(--color-success);
  margin: 0 auto var(--space-4);
  display: flex;
  align-items: center;
  justify-content: center;
}

.bf-success-title {
  font-size: var(--fs-xl);
  font-weight: 700;
  color: var(--text-strong);
  margin-bottom: var(--space-1);
}

.bf-success-sub {
  font-size: var(--fs-base);
  color: var(--text-secondary);
  margin-bottom: var(--space-6);
}

.bf-success-actions {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  align-items: stretch;
}
</style>
