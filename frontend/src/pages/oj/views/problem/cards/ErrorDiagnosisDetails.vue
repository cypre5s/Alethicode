<template>
  <div class="edd-root">
    <div v-if="hasFirstFailedTestCaseEvidence" class="edd-failed-case">
      <button type="button" class="edd-failed-case-toggle" @click="firstFailedExpanded = !firstFailedExpanded">
        <span>看第一个错误测试点</span>
        <el-icon :size="12"><component :is="firstFailedExpanded ? ArrowUp : ArrowDown" /></el-icon>
      </button>
      <div v-show="firstFailedExpanded" class="edd-failed-case-content">
        <div v-if="firstFailedTestCase.input" class="edd-fc-row">
          <div class="edd-fc-label">测试输入</div>
          <pre class="edd-fc-value">{{ firstFailedTestCase.input }}</pre>
        </div>
        <div v-if="firstFailedTestCase.expected_output" class="edd-fc-row">
          <div class="edd-fc-label">期望输出</div>
          <pre class="edd-fc-value">{{ firstFailedTestCase.expected_output }}</pre>
        </div>
        <div v-if="firstFailedTestCase.actual_output" class="edd-fc-row">
          <div class="edd-fc-label">你的输出</div>
          <pre class="edd-fc-value">{{ firstFailedTestCase.actual_output }}</pre>
        </div>
      </div>
    </div>

    <div v-if="hasVisibleSimilarErrors" class="edd-similar">
      <div class="edd-similar-head">
        <span>历史相似错误</span>
        <span class="edd-similar-badge">重复模式</span>
      </div>
      <div v-if="similarErrorSummary" class="edd-similar-summary" v-html="similarErrorSummaryHtml"></div>
      <div v-if="filteredSimilarErrorRefs.length" class="edd-similar-list">
        <div v-for="(ref, idx) in filteredSimilarErrorRefs" :key="'similar-' + idx" class="edd-similar-item">
          <div class="edd-similar-title">{{ ref.title || ref.source_label || '历史记录' }}</div>
          <div v-if="ref.summary" class="edd-similar-text">{{ ref.summary }}</div>
          <div class="edd-similar-meta">
            <span v-if="ref.source_type">{{ ref.source_type }}</span>
            <span v-if="ref.score !== undefined && ref.score !== null">相似度 {{ formatScore(ref.score) }}</span>
          </div>
        </div>
      </div>
    </div>

    <div v-if="hasTrace" class="edd-trace">
      <div class="edd-trace-toggle" @click="traceExpanded = !traceExpanded">
        <span class="edd-trace-toggle-label">
          <el-icon :size="14" class="edd-trace-toggle-icon"><View /></el-icon>
          <span>执行轨迹（{{ executionTrace.length }} 步）</span>
        </span>
        <el-icon :size="12"><component :is="traceExpanded ? ArrowUp : ArrowDown" /></el-icon>
      </div>
      <div v-show="traceExpanded" class="edd-trace-content">
        <TraceStepPanel
          ref="tracePanel"
          :trace-data="executionTrace"
          :code="studentCode"
          :critical-steps="parsedCriticalSteps"
        />
      </div>
    </div>

    <div v-if="canRequestExecutionTrace || !strategyFeedbackSent" class="edd-action-panel">
      <button v-if="canRequestExecutionTrace" type="button" class="edd-trace-btn" @click="$emit('request-execution-trace')">
        <svg class="edd-trace-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <polygon points="6 4 20 12 6 20 6 4"/>
        </svg>
        <span>看程序怎么一步步跑</span>
      </button>

      <div v-if="!strategyFeedbackSent" class="edd-preference-group">
        <span class="edd-fb-label">这种解释方式适合你吗？</span>
        <div class="edd-preference-actions">
          <button type="button" class="edd-fb-btn edd-fb-positive" @click="$emit('feedback', 'positive')">适合我</button>
          <button type="button" class="edd-fb-btn edd-fb-negative" @click="$emit('feedback', 'negative')">换种方式</button>
        </div>
      </div>
    </div>
    <div v-else class="edd-fb-thanks">感谢反馈，我会记住你的偏好</div>
  </div>
</template>

<script>
import { markRaw } from 'vue'
import { ArrowUp, ArrowDown, View } from '@element-plus/icons-vue'
import TraceStepPanel from '../TraceStepPanel.vue'
import marked from 'marked'
import { sanitize } from '@/utils/sanitize'

export default {
  name: 'ErrorDiagnosisDetails',
  components: { TraceStepPanel, ArrowUp, ArrowDown, View },
  emits: ['feedback', 'request-execution-trace', 'jump-to-step'],
  props: {
    firstFailedTestCase: { type: Object, default: () => ({}) },
    similarErrorSummary: { type: String, default: '' },
    filteredSimilarErrorRefs: { type: Array, default: () => [] },
    repeatPatternDetected: { type: Boolean, default: false },
    executionTrace: { type: Array, default: () => [] },
    studentCode: { type: String, default: '' },
    parsedCriticalSteps: { type: Array, default: () => [] },
    showTrace: { type: Boolean, default: false },
    canRequestExecutionTrace: { type: Boolean, default: true },
    strategyFeedbackSent: { type: Boolean, default: false }
  },
  data () {
    return {
      ArrowUp: markRaw(ArrowUp),
      ArrowDown: markRaw(ArrowDown),
      firstFailedExpanded: false,
      traceExpanded: false
    }
  },
  computed: {
    hasFirstFailedTestCaseEvidence () {
      const c = this.firstFailedTestCase || {}
      return !!(c.input || c.expected_output || c.actual_output)
    },
    hasVisibleSimilarErrors () {
      return this.repeatPatternDetected && this.filteredSimilarErrorRefs.length > 0
    },
    hasTrace () { return this.showTrace },
    similarErrorSummaryHtml () {
      if (!this.similarErrorSummary) return ''
      const withSup = String(this.similarErrorSummary)
        .replace(/(\d+)\^([{(]?[-\w.+]+[})]?)/g, '$1<sup>$2</sup>')
      return sanitize(marked(withSup))
    }
  },
  methods: {
    formatScore (score) {
      if (typeof score !== 'number') return '--'
      return (score <= 1 ? Math.round(score * 100) : Math.round(score)) + '%'
    },
    expandTraceAndJump (step) {
      this.traceExpanded = true
      this.$nextTick(() => {
        if (this.$refs.tracePanel) this.$refs.tracePanel.jumpToStep(step)
      })
    }
  }
}
</script>

<style lang="less" scoped>
.edd-root {
  display: flex;
  flex-direction: column;
  gap: var(--card-body-gap, var(--space-3));
}

.edd-failed-case {
  border: 1px solid rgba(59, 130, 246, 0.22);
  border-radius: var(--radius-md);
  background: linear-gradient(180deg, rgba(239, 246, 255, 0.88), rgba(248, 250, 252, 0.96));
  overflow: hidden;
}
.edd-failed-case-toggle {
  width: 100%;
  border: none;
  background: transparent;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  padding: 10px var(--space-3);
  color: var(--primary-700);
  font-size: var(--card-font-body, var(--fs-base));
  font-weight: 600;
  cursor: pointer;
  text-align: left;
  &:hover { background: rgba(219, 234, 254, 0.5); }
}
.edd-failed-case-content {
  border-top: 1px solid rgba(59, 130, 246, 0.16);
  padding: var(--space-3);
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}
.edd-fc-row { display: flex; flex-direction: column; gap: var(--space-1); }
.edd-fc-label {
  font-size: var(--card-font-label, var(--fs-sm));
  font-weight: 600;
  color: var(--primary-700);
}
.edd-fc-value {
  margin: 0;
  border-radius: var(--radius-sm);
  padding: 10px;
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid rgba(148, 163, 184, 0.28);
  color: var(--text-strong);
  font-size: var(--card-font-body, var(--fs-base));
  line-height: var(--leading-body);
  white-space: pre-wrap;
  word-break: break-word;
  font-family: var(--font-mono);
}

.edd-similar {
  border: 1px solid rgba(217, 119, 6, 0.30);
  border-radius: var(--radius-md);
  padding: var(--space-3);
  background: linear-gradient(180deg, rgba(255, 247, 237, 0.92), rgba(255, 251, 235, 0.96));
}
.edd-similar-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--space-2);
  margin-bottom: var(--space-2);
  color: #9a3412;
  font-size: var(--card-font-body, var(--fs-base));
  font-weight: 600;
}
.edd-similar-badge {
  display: inline-flex;
  align-items: center;
  height: var(--tag-height);
  border-radius: var(--tag-radius);
  padding: 0 8px;
  background: rgba(217, 119, 6, 0.14);
  font-size: var(--card-font-label, var(--fs-sm));
}
.edd-similar-summary {
  font-size: var(--card-font-body, var(--fs-base));
  color: #7c2d12;
  line-height: var(--leading-loose);
}
.edd-similar-list {
  margin-top: var(--space-3);
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}
.edd-similar-item {
  border-radius: var(--radius-sm);
  padding: var(--space-3);
  background: rgba(255, 255, 255, 0.78);
  border: 1px solid rgba(217, 119, 6, 0.18);
}
.edd-similar-title {
  font-size: var(--card-font-body, var(--fs-base));
  font-weight: 600;
  color: #9a3412;
}
.edd-similar-text {
  margin-top: var(--space-1);
  font-size: var(--card-font-label, var(--fs-sm));
  line-height: var(--leading-body);
  color: #7c2d12;
}
.edd-similar-meta {
  margin-top: 6px;
  display: flex;
  gap: var(--space-2);
  flex-wrap: wrap;
  font-size: var(--card-font-label, var(--fs-sm));
  color: #b45309;
}

.edd-trace {
  border: 1px solid rgba(99, 102, 241, 0.28);
  border-radius: var(--radius-sm);
  overflow: hidden;
  background: rgba(99, 102, 241, 0.04);
}
.edd-trace-toggle {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-2) var(--space-3);
  cursor: pointer;
  font-size: var(--card-font-body, var(--fs-base));
  font-weight: 500;
  color: var(--warm-primary);
  &:hover { background: rgba(99, 102, 241, 0.08); }
}
.edd-trace-toggle-label {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
}
.edd-trace-toggle-icon { color: inherit; }
.edd-trace-content { border-top: 1px solid rgba(99, 102, 241, 0.18); }

.edd-action-panel {
  padding: var(--space-3);
  border-radius: var(--radius-md);
  background: linear-gradient(180deg, rgba(248, 250, 252, 0.96), rgba(241, 245, 249, 0.78));
  border: 1px solid var(--border-default);
  display: grid;
  gap: var(--space-3);
}
.edd-trace-btn {
  width: 100%;
  height: var(--control-height-md);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  border: none;
  border-radius: var(--radius-sm);
  padding: 0 14px;
  background: linear-gradient(135deg, var(--primary-600) 0%, var(--color-info) 100%);
  color: #fff;
  font-size: var(--fs-base);
  font-weight: 600;
  letter-spacing: 0.02em;
  cursor: pointer;
  box-shadow: 0 6px 14px rgba(37, 99, 235, 0.18);
  transition: transform var(--motion-fast), box-shadow var(--motion-fast), filter var(--motion-fast);
  &:hover {
    transform: translateY(-1px);
    box-shadow: 0 8px 18px rgba(37, 99, 235, 0.22);
    filter: brightness(1.04);
  }
  &:active { transform: translateY(0); filter: brightness(0.98); }
}
.edd-trace-icon { flex-shrink: 0; }

.edd-preference-group {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--space-3);
  padding: 2px 2px 0;
}
.edd-fb-label {
  font-size: var(--fs-sm);
  color: var(--text-secondary);
  line-height: var(--leading-tight);
}
.edd-preference-actions {
  display: inline-flex;
  gap: var(--space-2);
}
.edd-fb-btn {
  height: 30px;
  padding: 0 12px;
  border-radius: var(--radius-sm);
  font-size: var(--fs-sm);
  font-weight: 600;
  cursor: pointer;
  border: 1px solid var(--border-default);
  background: var(--bg-card);
  transition: background var(--motion-fast), border-color var(--motion-fast), color var(--motion-fast), transform var(--motion-fast);
  &:hover { transform: translateY(-1px); }
}
.edd-fb-positive {
  color: #15803d;
  &:hover {
    background: #f0fdf4;
    border-color: #86efac;
    color: #166534;
  }
}
.edd-fb-negative {
  color: #b91c1c;
  &:hover {
    background: #fef2f2;
    border-color: #fca5a5;
    color: #991b1b;
  }
}
.edd-fb-thanks {
  font-size: var(--fs-sm);
  color: var(--text-disabled);
  text-align: center;
  padding: var(--space-2) 6px;
}
@media (max-width: 480px) {
  .edd-preference-group { grid-template-columns: 1fr; gap: var(--space-2); }
  .edd-preference-actions { display: grid; grid-template-columns: 1fr 1fr; }
}
@media (prefers-reduced-motion: reduce) {
  .edd-trace-btn,
  .edd-fb-btn {
    transition: none;
    &:hover { transform: none; }
  }
}
</style>
