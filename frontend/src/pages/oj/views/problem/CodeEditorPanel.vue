<template>
  <div class="code-editor-panel-root">
    <el-card :body-style="{padding: '20px'}" id="submit-code" shadow="never" style="margin: 0;">
    <CodeMirror :initial-value="code"
                ref="editor"
                :languages="problem.languages"
                :language="language"
                :theme="theme"
                @change="onCodeChange"
                @resetCode="onResetTemplate"
                @changeTheme="onThemeChange"
                @changeLang="onLangChange"
                @submit="onSubmitCode"
                @debug="onDebugCode">
    </CodeMirror>
    <el-row justify="space-between" style="margin-top: 10px;">
      <el-col :span="12">
        <div class="status" v-if="statusVisible">
            <span>{{$t('m.Status')}}</span>
            <el-tag :type="submissionStatus.elType" :class="{'judge-pulsing': isJudging}" @click="handleRoute('/status/'+submissionId)" effect="light">
              <el-icon v-if="isJudging" class="is-loading" :size="12" style="margin-right: 4px;"><Loading /></el-icon>
              {{isJudging ? judgePhaseText : $t('m.' + submissionStatus.text.replace(/ /g, "_"))}}
            </el-tag>
        </div>
        <div v-else-if="problem.my_status === 0">
          <el-alert type="success" show-icon :closable="false">{{$t('m.You_have_solved_the_problem')}}</el-alert>
        </div>
      </el-col>

      <el-col :span="12">
        <template v-if="captchaRequired">
          <div class="captcha-container">
            <el-tooltip v-if="captchaRequired" content="Click to refresh" placement="top">
              <img :src="captchaSrc" @click="onGetCaptcha"/>
            </el-tooltip>
            <el-input :model-value="captchaCode" @update:modelValue="onCaptchaInput" class="captcha-code"/>
          </div>
        </template>
        <div class="button-group">
          <el-button
            v-if="aiTutorEnabled && consecutiveErrors >= 3 && canOpenAiChat"
            type="primary"
            size="small"
            plain
            @click="onToggleAI">
            <el-icon :size="14"><ChatDotRound /></el-icon>
            AI 对话助手
          </el-button>
        </div>
      </el-col>
    </el-row>
    </el-card>

    <el-card :body-style="{padding: '20px'}" class="debug-panel" shadow="never">
    <template #header><div>
      <el-icon><BugIcon /></el-icon>
      <span class="card-title">调试面板</span>
    </div></template>
    <div class="debug-content">
      <div class="debug-inputs">
        <div class="debug-section">
          <h4>Input</h4>
          <el-input type="textarea"
                 :model-value="debugInput"
                 @update:modelValue="onDebugInputChange"
                 class="flex-textarea"
                 placeholder="请输入测试数据..."
                 style="font-family: 'Courier New', monospace;"></el-input>
        </div>
        <div class="debug-section">
          <h4>Output</h4>
          <el-input type="textarea"
                 :model-value="debugOutput"
                 class="flex-textarea"
                 :readonly="true"
                 :placeholder="debugging ? '代码运行中...' : '运行结果将在此处显示'"
                 :class="{'debug-error-textarea': debugError}"
                 style="font-family: 'Courier New', monospace;"></el-input>
        </div>
      </div>
      <div class="debug-footer">
        <div class="debug-button-group">
          <el-button type="primary" :loading="debugging" @click="onDebugCode"
                  :disabled="problemSubmitDisabled">
            <el-icon><BugIcon /></el-icon>
            <span v-if="debugging">调试中</span>
            <span v-else>调试</span>
          </el-button>
          <el-button type="warning" :loading="submitting" @click="onSubmitCode"
                  :disabled="problemSubmitDisabled || submitted">
            <el-icon><Edit /></el-icon>
            <span v-if="submitting">{{$t('m.Submitting')}}</span>
            <span v-else>{{$t('m.Submit')}}</span>
          </el-button>
        </div>
      </div>
    </div>
    </el-card>
  </div>
</template>

<script>
import CodeMirror from '@oj/components/CodeMirror.vue'
import { JUDGE_STATUS } from '@/utils/constants'
import { h } from 'vue'
import { ChatDotRound, Edit, Loading } from '@element-plus/icons-vue'

const BugIcon = {
  name: 'BugIcon',
  render () {
    return h('svg', {
      xmlns: 'http://www.w3.org/2000/svg',
      viewBox: '0 0 24 24',
      fill: 'currentColor',
      width: '1em',
      height: '1em'
    }, [
      h('path', { d: 'M19 8h-1.81a5.98 5.98 0 0 0-1.82-2.43l1.34-1.34-1.42-1.42-1.78 1.78a5.98 5.98 0 0 0-2.02-.55V2h-2v2.04c-.7.1-1.38.31-2.02.55L5.69 2.81 4.27 4.23l1.34 1.34A5.98 5.98 0 0 0 3.81 8H2v2h1.09a6.08 6.08 0 0 0 .03 1H2v2h1.09c.18.72.48 1.38.89 1.96L2 16.94l1.41 1.41 1.56-1.56c.38.28.79.51 1.22.68A5.99 5.99 0 0 0 8 18.92V20h2v-1.08c.33.05.66.08 1 .08s.67-.03 1-.08V20h2v-1.08a5.99 5.99 0 0 0 1.81-.86c.43-.17.84-.4 1.22-.68l1.56 1.56L20 17.53l-1.98-1.98c.41-.58.71-1.24.89-1.96H20v-2h-1.09a6.08 6.08 0 0 0 .03-1H20V8h-1zm-7 8c-2.21 0-4-1.79-4-4s1.79-4 4-4 4 1.79 4 4-1.79 4-4 4z' })
    ])
  }
}

export default {
  name: 'CodeEditorPanel',
  components: {
    CodeMirror,
    ChatDotRound,
    BugIcon,
    Edit,
    Loading
  },
  props: {
    code: {
      type: String,
      required: true
    },
    language: {
      type: String,
      default: ''
    },
    theme: {
      type: String,
      default: 'solarized'
    },
    problem: {
      type: Object,
      default: () => ({
        title: '',
        languages: [],
        template: {},
        my_status: null,
        id: null
      })
    },
    statusVisible: {
      type: Boolean,
      default: false
    },
    submissionId: {
      type: String,
      default: ''
    },
    result: {
      type: Object,
      default: () => ({ result: 9 })
    },
    submitting: {
      type: Boolean,
      default: false
    },
    submitted: {
      type: Boolean,
      default: false
    },
    problemSubmitDisabled: {
      type: Boolean,
      default: false
    },
    debugging: {
      type: Boolean,
      default: false
    },
    debugInput: {
      type: String,
      default: ''
    },
    debugOutput: {
      type: String,
      default: ''
    },
    debugError: {
      type: Boolean,
      default: false
    },
    chatVisible: {
      type: Boolean,
      default: false
    },
    consecutiveErrors: {
      type: Number,
      default: 0
    },
    captchaRequired: {
      type: Boolean,
      default: false
    },
    captchaSrc: {
      type: String,
      default: ''
    },
    captchaCode: {
      type: String,
      default: ''
    },
    submissionExists: {
      type: Boolean,
      default: false
    },
    aiTutorEnabled: {
      type: Boolean,
      default: true
    },
    canOpenAiChat: {
      type: Boolean,
      default: true
    },
    canRequestDiagnosis: {
      type: Boolean,
      default: true
    }
  },
  computed: {
    submissionStatus () {
      const r = (this.result && this.result.result) != null ? String(this.result.result) : '9'
      const status = JUDGE_STATUS[r] || JUDGE_STATUS['9']
      const typeMap = { error: 'danger', success: 'success', warning: 'warning', info: 'info' }
      return {
        text: status.name,
        color: status.color,
        elType: typeMap[status.type] || ''
      }
    },
    isJudging () {
      const r = this.result && this.result.result
      return r === 6 || r === 7 || r === 9
    },
    judgePhaseText () {
      const r = this.result && this.result.result
      if (r === 9) return '提交中...'
      if (r === 6) return '排队等待...'
      if (r === 7) return '评测中...'
      return '处理中...'
    }
  },
  created () {
    this._lastSnapshotCode = this.code || ''
    this._snapshotTimer = null
    this._totalKeystrokes = 0
    this._totalInserted = 0
    this._totalDeleted = 0
    this._pasteCount = 0
    this._lastEditTime = Date.now()
    this._activeEditMs = 0
  },
  mounted () {
    this._startSnapshotInterval()
    this._attachPasteListener()
  },
  beforeUnmount () {
    const el = this.$el
    if (el) {
      el.removeEventListener('paste', this._onPaste)
    }
    if (this._snapshotTimer) {
      clearInterval(this._snapshotTimer)
      this._snapshotTimer = null
    }
  },
  methods: {
    _startSnapshotInterval () {
      this._snapshotTimer = setInterval(() => {
        if (this.code !== this._lastSnapshotCode) {
          this._reportSnapshot('interval')
        }
      }, 60000)
    },
    _attachPasteListener () {
      this.$nextTick(() => {
        const el = this.$el
        if (el) {
          el.addEventListener('paste', this._onPaste)
        }
      })
    },
    _onPaste (e) {
      const text = (e.clipboardData || window.clipboardData || {}).getData('text') || ''
      if (text.length >= 20) {
        this._pasteCount++
        this.$nextTick(() => {
          this._reportSnapshot('paste')
        })
      }
    },
    _reportSnapshot (trigger) {
      const code = this.code || ''
      const oldLen = (this._lastSnapshotCode || '').length
      const newLen = code.length
      const data = {
        problem_id: this.problem && this.problem.id,
        code: code,
        trigger: trigger,
        char_count: newLen,
        line_count: code.split('\n').length,
        diff_chars_added: Math.max(0, newLen - oldLen),
        diff_chars_deleted: Math.max(0, oldLen - newLen)
      }
      this._lastSnapshotCode = code
      this.$emit('code-snapshot', data)
    },
    getEditStats () {
      return {
        total_keystrokes: this._totalKeystrokes,
        total_inserted: this._totalInserted,
        total_deleted: this._totalDeleted,
        paste_count: this._pasteCount,
        active_edit_ms: this._activeEditMs,
        delete_ratio: this._totalInserted > 0
          ? Math.round((this._totalDeleted / this._totalInserted) * 100) / 100
          : 0
      }
    },
    resetEditStats () {
      this._totalKeystrokes = 0
      this._totalInserted = 0
      this._totalDeleted = 0
      this._pasteCount = 0
      this._activeEditMs = 0
    },
    onCodeChange (val) {
      const oldLen = (this.code || '').length
      const newLen = (val || '').length
      this._totalKeystrokes++
      if (newLen > oldLen) this._totalInserted += (newLen - oldLen)
      if (newLen < oldLen) this._totalDeleted += (oldLen - newLen)
      const now = Date.now()
      if (now - this._lastEditTime < 5000) {
        this._activeEditMs += (now - this._lastEditTime)
      }
      this._lastEditTime = now
      this.$emit('update:code', val)
    },
    onLangChange (lang) {
      this.$emit('change-lang', lang)
    },
    onThemeChange (t) {
      this.$emit('change-theme', t)
    },
    onResetTemplate () {
      this.$emit('reset-template')
    },
    onSubmitCode () {
      this.$emit('submit-code')
    },
    onDebugCode () {
      this.$emit('debug-code')
    },
    onToggleAI () {
      this.$emit('toggle-ai')
    },
    onRequestDiagnosis () {
      this.$emit('request-diagnosis')
    },
    onGetCaptcha () {
      this.$emit('get-captcha')
    },
    onCaptchaInput (val) {
      this.$emit('update:captchaCode', val)
    },
    onDebugInputChange (val) {
      this.$emit('update:debugInput', val)
    },
    handleRoute (route) {
      this.$router.push(route)
    }
  }
}
</script>

<style lang="less" scoped>
.code-editor-panel-root {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 24px;
  overflow: hidden;
}

#submit-code {
  flex: 0 0 auto;
}

.status {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
}


.captcha-container {
  display: flex;
  align-items: center;
  margin-bottom: 8px;

  img {
    cursor: pointer;
    height: 36px;
    margin-right: 8px;
  }

  .captcha-code {
    width: 120px;
  }
}

.button-group {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 6px;
}

.ai-trigger-btn {
  &.ai-breathing {
    animation: breathe 3s infinite ease-in-out;
    color: var(--primary-color);
  }
  &.ai-active {
    color: var(--primary-color);
    background: rgba(59, 130, 246, 0.1) !important;
  }
}

@keyframes breathe {
  0% { transform: scale(1); opacity: 0.7; }
  50% { transform: scale(1.1); opacity: 1; }
  100% { transform: scale(1); opacity: 0.7; }
}

.debug-panel {
  flex: 1 1 auto;
  min-height: 0;
  overflow: hidden;

  :deep(.el-card__body) {
    flex: 1;
    min-height: 0;
    display: flex;
    flex-direction: column;
    overflow: hidden;
  }

  .card-title {
    margin-left: 6px;
  }
}

.debug-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.debug-inputs {
  display: flex;
  flex-direction: row;
  gap: 12px;
  align-items: stretch;
  flex: 1;
  min-height: 0;
}

.debug-section {
  flex: 1;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;

  h4 {
    margin: 0 0 10px 0;
    font-size: 14px;
    font-weight: 500;
    color: var(--text-primary);
    flex-shrink: 0;
  }
}

.flex-textarea {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;

  :deep(.el-textarea) {
    flex: 1;
    min-height: 0;
    display: flex;
    flex-direction: column;
  }

  :deep(textarea) {
    height: 100% !important;
    min-height: 0;
    resize: none;
  }
}

.debug-footer {
  margin-top: auto;
  padding-top: 8px;
}

.debug-button-group {
  display: flex;
  justify-content: flex-start;
  align-items: center;
  gap: 10px;
}

@media (max-width: 992px) {
  .debug-inputs {
    flex-direction: column;
  }
}

:deep(.debug-error-textarea textarea) {
  border-color: var(--danger-color) !important;
  background: #fff2f0 !important;
  color: var(--danger-color) !important;
}

.judge-pulsing {
  animation: judge-pulse 1.5s ease-in-out infinite;
}

@keyframes judge-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.6; }
}

</style>
