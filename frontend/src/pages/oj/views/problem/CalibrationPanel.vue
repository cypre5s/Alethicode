<template>
  <div class="calibration-overlay" v-if="visible">
    <div class="calibration-card">
      <div class="cal-header">
        <div class="cal-header-icon">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="12" cy="12" r="10"/>
            <path d="M12 16v-4M12 8h.01"/>
          </svg>
        </div>
        <div class="cal-header-text">
          <h3>学习基础快速了解</h3>
          <p>回答几个简单问题，帮助 AI 更好地了解你的编程基础</p>
        </div>
      </div>

      <div class="cal-progress">
        <div class="cal-progress-bar">
          <div class="cal-progress-fill" :style="{ width: progressPercent + '%' }"></div>
        </div>
        <span class="cal-progress-text">第 {{ currentIndex + 1 }}/{{ totalQuestions }} 题</span>
      </div>

      <div class="cal-body" v-if="!isComplete">
        <div class="cal-question">
          <p class="cal-question-text">{{ currentPrompt }}</p>
        </div>
        <textarea
          ref="answerInput"
          class="cal-textarea"
          v-model="answerText"
          :placeholder="'用自己的话说说你的理解...'"
          rows="4"
          @keydown.ctrl.enter="handleSubmitAnswer"
        ></textarea>
        <div class="cal-actions">
          <button class="cal-btn-primary" @click="handleSubmitAnswer" :disabled="!answerText.trim() || submitting">
            {{ submitting ? '分析中...' : '回答' }}
          </button>
          <a class="cal-skip-link" @click="handleSkip" href="javascript:void(0)">跳过校准，直接开始</a>
        </div>
      </div>

      <div class="cal-body cal-summary" v-else>
        <div class="cal-summary-icon">
          <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="#52c41a" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/>
            <polyline points="22 4 12 14.01 9 11.01"/>
          </svg>
        </div>
        <h4>校准完成</h4>
        <p class="cal-summary-desc" v-if="calibratedKcs.length">
          <span v-for="(kc, idx) in calibratedKcs" :key="idx" class="cal-kc-tag" :class="kcLevelClass(kc.p_mastery_calibrated)">
            {{ kc.kc_name }}
          </span>
        </p>
        <p class="cal-summary-hint">正在为你定制学习路径...</p>
      </div>
    </div>
  </div>
</template>

<script>
import api from '@oj/api'

export default {
  name: 'CalibrationPanel',
  props: {
    visible: { type: Boolean, default: false },
    initialQuestion: { type: Object, default: null }
  },
  data () {
    return {
      currentIndex: 0,
      totalQuestions: 3,
      currentPrompt: '',
      currentKcGroup: [],
      answerText: '',
      submitting: false,
      isComplete: false,
      calibratedKcs: [],
      accumulated: {}
    }
  },
  computed: {
    progressPercent () {
      if (this.isComplete) return 100
      return Math.round((this.currentIndex / this.totalQuestions) * 100)
    }
  },
  watch: {
    initialQuestion: {
      immediate: true,
      handler (q) {
        if (q) {
          this.currentIndex = q.question_index || 0
          this.totalQuestions = q.total || q.total_questions || 3
          this.currentPrompt = q.prompt || ''
          this.currentKcGroup = q.kc_group || []
        }
      }
    }
  },
  methods: {
    kcLevelClass (p) {
      if (p >= 0.6) return 'cal-kc-good'
      if (p >= 0.3) return 'cal-kc-mid'
      return 'cal-kc-low'
    },
    async handleSubmitAnswer () {
      if (!this.answerText.trim() || this.submitting) return
      this.submitting = true
      try {
        const res = await api.calibrationAnswer({
          question_index: this.currentIndex,
          answer: this.answerText,
          accumulated: this.accumulated
        })
        const data = res.data && res.data.data !== undefined ? res.data.data : res.data
        this.accumulated = data.accumulated || this.accumulated
        if (data.calibration_complete) {
          this.isComplete = true
          this.calibratedKcs = data.calibrated_kcs || []
          setTimeout(() => {
            this.$emit('calibration-done', { calibrated_kcs: this.calibratedKcs })
          }, 3000)
        } else if (data.next_question) {
          this.currentIndex = data.next_question.index
          this.currentPrompt = data.next_question.prompt
          this.currentKcGroup = data.next_question.kc_group || []
          this.totalQuestions = data.next_question.total || this.totalQuestions
          this.answerText = ''
          this.$nextTick(() => {
            if (this.$refs.answerInput) this.$refs.answerInput.focus()
          })
        }
      } catch (e) {
        this.$emit('calibration-done', { skipped: true, error: true })
      } finally {
        this.submitting = false
      }
    },
    async handleSkip () {
      try {
        await api.calibrationSkip({})
      } catch (e) {
        // skip failure is non-fatal
      }
      this.$emit('calibration-done', { skipped: true })
    }
  }
}
</script>

<style lang="less" scoped>
.calibration-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.45);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1200;
  animation: fadeIn 0.3s ease-out;
}

.calibration-card {
  background: #fff;
  border-radius: 16px;
  width: 520px;
  max-width: 92vw;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.15);
  overflow: hidden;
  animation: slideUp 0.35s ease-out;
}

.cal-header {
  background: linear-gradient(135deg, #3b82f6, #6366f1);
  padding: 24px 28px;
  display: flex;
  align-items: flex-start;
  gap: 14px;
}

.cal-header-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.2);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.cal-header-text {
  h3 {
    color: #fff;
    font-size: 18px;
    font-weight: 600;
    margin: 0 0 4px 0;
  }
  p {
    color: rgba(255, 255, 255, 0.8);
    font-size: 13px;
    margin: 0;
    line-height: 1.5;
  }
}

.cal-progress {
  padding: 16px 28px 0;
  display: flex;
  align-items: center;
  gap: 12px;
}

.cal-progress-bar {
  flex: 1;
  height: 6px;
  background: #e5e7eb;
  border-radius: 3px;
  overflow: hidden;
}

.cal-progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #3b82f6, #6366f1);
  border-radius: 3px;
  transition: width 0.4s ease;
}

.cal-progress-text {
  font-size: 12px;
  color: #9ca3af;
  white-space: nowrap;
}

.cal-body {
  padding: 20px 28px 28px;
}

.cal-question-text {
  font-size: 16px;
  font-weight: 500;
  color: #1f2937;
  line-height: 1.6;
  margin: 0 0 16px 0;
}

.cal-textarea {
  width: 100%;
  border: 1.5px solid #e5e7eb;
  border-radius: 10px;
  padding: 12px 14px;
  font-size: 14px;
  line-height: 1.6;
  resize: vertical;
  outline: none;
  transition: border-color 0.2s;
  font-family: inherit;
  box-sizing: border-box;
  &:focus {
    border-color: #3b82f6;
    box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
  }
}

.cal-actions {
  margin-top: 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.cal-btn-primary {
  background: linear-gradient(135deg, #3b82f6, #6366f1);
  color: #fff;
  border: none;
  border-radius: 8px;
  padding: 10px 28px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: opacity 0.2s, transform 0.1s;
  &:hover:not(:disabled) {
    opacity: 0.9;
  }
  &:active:not(:disabled) {
    transform: scale(0.98);
  }
  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
}

.cal-skip-link {
  font-size: 13px;
  color: #9ca3af;
  text-decoration: none;
  cursor: pointer;
  &:hover {
    color: #6b7280;
    text-decoration: underline;
  }
}

.cal-summary {
  text-align: center;
  padding: 36px 28px 40px;
}

.cal-summary-icon {
  margin-bottom: 12px;
}

.cal-summary h4 {
  font-size: 20px;
  font-weight: 600;
  color: #1f2937;
  margin: 0 0 16px 0;
}

.cal-summary-desc {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
  margin: 0 0 16px 0;
}

.cal-kc-tag {
  display: inline-block;
  padding: 4px 12px;
  border-radius: 20px;
  font-size: 13px;
  font-weight: 500;
}

.cal-kc-good {
  background: #ecfdf5;
  color: #059669;
}

.cal-kc-mid {
  background: #fffbeb;
  color: #d97706;
}

.cal-kc-low {
  background: #fef2f2;
  color: #dc2626;
}

.cal-summary-hint {
  font-size: 14px;
  color: #9ca3af;
  margin: 0;
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

@keyframes slideUp {
  from {
    opacity: 0;
    transform: translateY(20px) scale(0.98);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

@media (prefers-reduced-motion: reduce) {
  .calibration-overlay,
  .calibration-card {
    animation: none;
  }
  .cal-progress-fill {
    transition: none;
  }
}
</style>
