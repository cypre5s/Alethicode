<template>
  <BaseAgentCard v-if="data" accent="danger" :icon="alertIcon" title="错误诊断">
    <template #body>
      <div v-if="isStringData" class="ed-encourage">
        <span>{{ data }}</span>
      </div>

      <template v-else>
        <div v-if="misconceptionHits.length" class="ed-ast-zone">
          <div class="ed-ast-header">⚠️ 自动检测到的问题</div>
          <div v-for="(hit, idx) in misconceptionHits" :key="'ast-' + idx" class="ed-ast-item">
            <div class="ed-ast-name">{{ hit.misconception_name || hit.name }}</div>
            <div v-if="hit.description" class="ed-ast-desc">{{ hit.description }}</div>
            <div v-if="hit.correction_hint" class="ed-ast-hint">💡 {{ hit.correction_hint }}</div>
          </div>
        </div>

        <div v-if="misconceptionInfo.length" class="ed-misconception-block">
          <div class="ed-misconception-toggle" @click="misconceptionExpanded = !misconceptionExpanded">
            <span>📌 常见易错点（{{ misconceptionInfo.length }}）</span>
            <el-icon :size="12"><component :is="misconceptionExpanded ? ArrowUp : ArrowDown" /></el-icon>
          </div>
          <div v-show="misconceptionExpanded" class="ed-misconception-list">
            <div v-for="(m, idx) in misconceptionInfo" :key="'mc-' + idx" class="ed-misconception-item">
              <div class="ed-misconception-name">{{ m.name }}</div>
              <div v-if="m.description" class="ed-misconception-desc">{{ m.description }}</div>
              <div v-if="m.trigger_count" class="ed-misconception-count">你已经在这个问题上遇到过 {{ m.trigger_count }} 次了</div>
              <div v-if="m.correction_hint" class="ed-misconception-hint">💡 {{ m.correction_hint }}</div>
            </div>
          </div>
        </div>

        <div v-if="data.encouragement" class="ed-encourage">
          <span class="ed-md" v-html="renderMarkdown(data.encouragement)"></span>
        </div>

        <div v-if="data.root_cause" class="ed-error-title ed-md" v-html="renderMarkdown(data.root_cause)"></div>

        <div v-if="data.what_program_is_doing || data.expected_behavior" class="ed-compare">
          <div v-if="data.what_program_is_doing" class="ed-box ed-box-current">
            <div class="ed-box-label">程序现在在做什么</div>
            <div class="ed-box-text ed-md" v-html="renderMarkdown(data.what_program_is_doing)"></div>
          </div>
          <div v-if="data.expected_behavior" class="ed-box ed-box-expected">
            <div class="ed-box-label">题目希望它做什么</div>
            <div class="ed-box-text ed-md" v-html="renderMarkdown(data.expected_behavior)"></div>
          </div>
        </div>

        <CoursewareRefList :refs="coursewareRefs" @open-courseware-ref="$emit('open-courseware-ref', $event)" />

        <div v-if="data.fix_direction" class="ed-guide">
          <span class="ed-md" v-html="renderStepLinks(renderMarkdown(data.fix_direction))"></span>
        </div>

        <div v-if="inlineVisualize" class="ed-inline-visualize">
          <VisualizeRenderer :data="inlineVisualize" />
        </div>

        <ErrorDiagnosisDetails
          ref="details"
          :first-failed-test-case="firstFailedTestCase"
          :similar-error-summary="data.similar_error_summary || ''"
          :filtered-similar-error-refs="filteredSimilarErrorRefs"
          :repeat-pattern-detected="repeatPatternDetected"
          :execution-trace="executionTrace"
          :student-code="studentCode"
          :parsed-critical-steps="parsedCriticalSteps"
          :show-trace="hasTrace"
          :can-request-execution-trace="canRequestExecutionTrace"
          :strategy-feedback-sent="strategyFeedbackSent"
          @feedback="submitStrategyFeedback"
          @request-execution-trace="$emit('request-execution-trace')"
        />

        <div v-if="similarErrorRefs.length" class="ed-similar-refs">
          <div class="ed-similar-refs-title">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 19.5v-15A2.5 2.5 0 0 1 6.5 2H20v20H6.5a2.5 2.5 0 0 1 0-5H20"/></svg>
            你过去也在这里栽过
          </div>
          <button
            v-for="ref in similarErrorRefs"
            :key="ref.source_id"
            type="button"
            class="ed-similar-chip"
            :class="'chip-' + ref.source_type"
            @click="$emit('open-notebook-entry', ref)"
          >
            <span class="ed-similar-date">{{ formatRelativeDate(ref.entry_date) }}</span>
            <span class="ed-similar-excerpt">{{ ref.excerpt }}</span>
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M5 12h14"/><path d="m12 5 7 7-7 7"/></svg>
          </button>
        </div>

        <div class="ed-card-footer-actions">
          <button type="button" class="ed-secondary-action" @click="$emit('add-to-notebook', { error_taxonomy: data.error_taxonomy || '', root_cause: data.root_cause || '', entry_type: 'error' })">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 19.5v-15A2.5 2.5 0 0 1 6.5 2H20v20H6.5a2.5 2.5 0 0 1 0-5H20"/></svg>
            加入错题本 + 写反思
          </button>
        </div>

        <ReasoningChain v-if="data.reasoning_chain" :chain="data.reasoning_chain" />
        <ToolCallTimeline v-if="data.tool_calls" :calls="data.tool_calls" />
        <EvidenceRefs
          :courseware-refs="data.courseware_refs || []"
          :memories="data.memory_refs || []"
          :similar-errors="data.similar_error_refs || []"
          @open-ref="$emit('open-courseware-ref', $event)"
        />
      </template>
    </template>
  </BaseAgentCard>
</template>

<script>
import { markRaw, h } from 'vue'
import BaseAgentCard from './BaseAgentCard.vue'
import ErrorDiagnosisDetails from './ErrorDiagnosisDetails.vue'
import CoursewareRefList from './CoursewareRefList.vue'
import ReasoningChain from './ReasoningChain.vue'
import ToolCallTimeline from './ToolCallTimeline.vue'
import EvidenceRefs from './EvidenceRefs.vue'
import VisualizeRenderer from './visualize/VisualizeRenderer.vue'
import api from '@oj/api'
import { ArrowUp, ArrowDown } from '@element-plus/icons-vue'
import marked from 'marked'
import { sanitize } from '@/utils/sanitize'

const MIN_SIMILAR_ERROR_SCORE = 0.35

const AlertIcon = {
  render () {
    return h('svg', {
      width: '14', height: '14', viewBox: '0 0 24 24', fill: 'none',
      stroke: 'currentColor', 'stroke-width': '2',
      'stroke-linecap': 'round', 'stroke-linejoin': 'round'
    }, [
      h('circle', { cx: '12', cy: '12', r: '10' }),
      h('line', { x1: '12', y1: '8', x2: '12', y2: '12' }),
      h('line', { x1: '12', y1: '16', x2: '12.01', y2: '16' })
    ])
  }
}

export default {
  name: 'ErrorDiagnosisCard',
  components: { BaseAgentCard, ErrorDiagnosisDetails, CoursewareRefList, ReasoningChain, ToolCallTimeline, EvidenceRefs, VisualizeRenderer, ArrowUp, ArrowDown },
  emits: ['request-execution-trace', 'open-courseware-ref', 'add-to-notebook', 'open-notebook-entry'],
  props: {
    data: {
      type: [Object, String],
      default: () => ({
        error_taxonomy: '', root_cause: '', what_program_is_doing: '',
        expected_behavior: '', fix_direction: '', related_kcs: [],
        encouragement: '', hint_level: 1,
        misconception_hits: [], misconception_info: [], courseware_refs: []
      })
    },
    executionTrace: { type: Array, default: () => [] },
    studentCode: { type: String, default: '' },
    canRequestExecutionTrace: { type: Boolean, default: true }
  },
  data () {
    return {
      ArrowUp: markRaw(ArrowUp),
      ArrowDown: markRaw(ArrowDown),
      alertIcon: markRaw(AlertIcon),
      misconceptionExpanded: false,
      strategyFeedbackSent: false
    }
  },
  computed: {
    isStringData () { return typeof this.data === 'string' },
    misconceptionHits () { return (this.data && this.data.misconception_hits) || [] },
    misconceptionInfo () { return (this.data && this.data.misconception_info) || [] },
    coursewareRefs () { return (this.data && this.data.courseware_refs) || [] },
    similarErrorRefs () { return (this.data && this.data.similar_error_refs) || [] },
    filteredSimilarErrorRefs () {
      return this.similarErrorRefs.filter((ref) => {
        if (typeof ref.score !== 'number') return false
        const s = ref.score <= 1 ? ref.score : ref.score / 100
        return s >= MIN_SIMILAR_ERROR_SCORE
      })
    },
    repeatPatternDetected () { return !!(this.data && this.data.repeat_pattern_detected) },
    firstFailedTestCase () { return (this.data && this.data.first_failed_test_case) || {} },
    inlineVisualize () {
      return this.data && typeof this.data === 'object' && this.data.visualize && typeof this.data.visualize === 'object'
        ? this.data.visualize
        : null
    },
    hasTrace () {
      return this.executionTrace.length > 0 &&
        this.data && this.data.error_taxonomy === 'logic_error'
    },
    parsedCriticalSteps () {
      if (!this.data || !this.data.fix_direction) return []
      const matches = this.data.fix_direction.match(/第\s*(\d+)\s*步/g)
      if (!matches) return []
      return matches.map(m => parseInt(m.replace(/[^\d]/g, ''), 10) - 1)
        .filter(n => n >= 0 && n < this.executionTrace.length)
    }
  },
  methods: {
    async submitStrategyFeedback (rating) {
      this.strategyFeedbackSent = true
      try { await api.submitStrategyFeedback('error_diagnosis', rating) } catch (e) { console.warn('Strategy feedback failed:', e) }
    },
    renderMarkdown (text) {
      if (!text) return ''
      const withSup = String(text).replace(/(\d+)\^([{(]?[-\w.+]+[})]?)/g, '$1<sup>$2</sup>')
      return sanitize(marked(withSup))
    },
    renderStepLinks (text) {
      if (!text || !this.hasTrace) return text || ''
      return text.replace(/第\s*(\d+)\s*步/g, (match, num) => {
        const step = parseInt(num, 10) - 1
        if (step >= 0 && step < this.executionTrace.length) {
          return `<a class="ed-step-link" data-step="${step}" href="javascript:void(0)">${match}</a>`
        }
        return match
      })
    },
    formatRelativeDate (isoDate) {
      if (!isoDate) return ''
      const d = new Date(isoDate)
      if (Number.isNaN(d.getTime())) return ''
      const days = Math.floor((Date.now() - d.getTime()) / 86400000)
      if (days === 0) return '今天'
      if (days === 1) return '昨天'
      if (days < 7) return days + ' 天前'
      if (days < 30) return Math.floor(days / 7) + ' 周前'
      return (d.getMonth() + 1) + '月' + d.getDate() + '日'
    },
    handleStepClick (e) {
      const link = e.target.closest('.ed-step-link')
      if (!link) return
      const step = parseInt(link.dataset.step, 10)
      if (isNaN(step)) return
      if (this.$refs.details) this.$refs.details.expandTraceAndJump(step)
    }
  },
  mounted () { this.$el.addEventListener('click', this.handleStepClick) },
  beforeUnmount () { this.$el.removeEventListener('click', this.handleStepClick) }
}
</script>

<style lang="less" scoped>
.ed-encourage {
  background: var(--card-accent-bg); border: 1px solid var(--card-accent-border);
  border-radius: 10px; padding: 12px 14px;
  font-size: var(--card-font-body); color: var(--card-accent); line-height: var(--card-line-height);
}
.ed-error-title {
  font-size: var(--card-font-body); font-weight: 500; color: var(--text-primary); line-height: 1.5;
  border-left: 2.5px solid var(--card-accent); padding-left: 10px;
}
.ed-compare { display: flex; flex-direction: column; gap: 8px; }
.ed-box { border-radius: 8px; padding: 10px 12px; font-size: var(--card-font-body); line-height: 1.7; border: 0.5px solid; }
.ed-box-label {
  font-size: var(--card-font-label); font-weight: 500; letter-spacing: 0.3px;
  text-transform: uppercase; margin-bottom: 4px;
}
.ed-box-current { background: var(--card-accent-bg); border-color: var(--card-accent-border); color: var(--card-accent); }
.ed-box-expected { background: rgba(37, 99, 235, 0.06); border-color: rgba(37, 99, 235, 0.2); color: var(--primary-color); }

.ed-guide {
  background: rgba(245, 158, 11, 0.08); border: 0.5px solid rgba(245, 158, 11, 0.22);
  border-radius: 8px; padding: 10px 12px;
  font-size: var(--card-font-body); color: var(--warning-color); line-height: 1.7;
}

.ed-inline-visualize {
  border-radius: 10px;
  overflow: hidden;
}

.ed-ast-zone {
  background: rgba(245, 158, 11, 0.06);
  border: 0.5px solid rgba(245, 158, 11, 0.22);
  border-radius: 8px; padding: 12px;
}
.ed-ast-header { font-size: var(--card-font-body); font-weight: 600; color: #b45309; margin-bottom: 8px; }
.ed-ast-item {
  padding: 8px 10px; background: rgba(245, 158, 11, 0.08);
  border: 0.5px solid rgba(245, 158, 11, 0.22); border-radius: 6px;
  margin-bottom: 6px; &:last-child { margin-bottom: 0; }
}
.ed-ast-name { font-size: var(--card-font-body); font-weight: 600; color: #92400e; }
.ed-ast-desc { font-size: var(--card-font-label); color: #78350f; margin-top: 3px; line-height: 1.5; }
.ed-ast-hint { font-size: var(--card-font-label); color: #b45309; margin-top: 4px; font-style: italic; }

.ed-misconception-block { border: 0.5px solid var(--card-accent-border); border-radius: 8px; overflow: hidden; }
.ed-misconception-toggle {
  display: flex; align-items: center; justify-content: space-between;
  padding: 8px 12px; background: var(--card-accent-bg); cursor: pointer;
  font-size: var(--card-font-body); font-weight: 500; color: var(--text-secondary);
  &:hover { background: var(--card-accent-bg-strong); }
}
.ed-misconception-list { padding: 8px 12px; }
.ed-misconception-item { padding: 6px 0; border-bottom: 0.5px solid var(--border-color); &:last-child { border-bottom: none; } }
.ed-misconception-name { font-size: var(--card-font-body); font-weight: 600; color: var(--text-primary); }
.ed-misconception-desc { font-size: var(--card-font-label); color: var(--text-secondary); margin-top: 2px; line-height: 1.5; }
.ed-misconception-count { font-size: var(--card-font-label); color: var(--card-accent); margin-top: 3px; }
.ed-misconception-hint { font-size: var(--card-font-label); color: var(--warning-color); margin-top: 3px; font-style: italic; }

.ed-md {
  :deep(p) { margin: 0 0 5px; &:last-child { margin-bottom: 0; } }
  :deep(strong) { font-weight: 700; }
  :deep(em) { font-style: italic; }
  :deep(ul), :deep(ol) { margin: 3px 0 5px; padding-left: 16px; }
  :deep(li) { margin: 1px 0; }
  :deep(code) {
    background: rgba(0,0,0,0.06); border-radius: 3px; padding: 1px 4px;
    font-family: var(--font-mono); font-size: var(--card-font-code);
  }
}

:deep(.ed-step-link) {
  color: var(--primary-color, #6366f1); font-weight: 600;
  text-decoration: underline; text-decoration-style: dotted;
  text-underline-offset: 2px; cursor: pointer;
  &:hover { text-decoration-style: solid; }
}

.ed-similar-refs { margin-top: 12px; display: flex; flex-direction: column; gap: 8px; }
.ed-similar-refs-title {
  display: flex; align-items: center; gap: 6px;
  font-size: 13px; font-weight: 600; color: #92400e;
}
.ed-similar-chip {
  display: flex; align-items: center; gap: 8px;
  background: #fef3c7; border: 1px solid #fcd34d; color: #92400e;
  padding: 8px 14px; border-radius: 8px; font-size: 13px;
  cursor: pointer; font-family: inherit; min-height: 44px;
  transition: box-shadow 200ms, background 200ms; text-align: left;
  &:hover { box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08); background: #fde68a; }
  &.chip-similar_memory { background: #dbeafe; border-color: #93c5fd; color: #1e40af; &:hover { background: #bfdbfe; } }
}
.ed-similar-date { font-weight: 600; white-space: nowrap; flex-shrink: 0; }
.ed-similar-excerpt { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.ed-card-footer-actions {
  margin-top: 2px;
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  flex-wrap: wrap;
}
.ed-secondary-action {
  display: inline-flex; align-items: center; gap: 6px;
  background: rgba(239, 246, 255, 0.7);
  border: 1px solid rgba(191, 219, 254, 0.85);
  color: #1d4ed8;
  padding: 0 12px; border-radius: 8px; font-size: 12.5px; font-weight: 600;
  cursor: pointer; font-family: inherit; height: 32px;
  transition: background 180ms ease, border-color 180ms ease, box-shadow 180ms ease, color 180ms ease, transform 180ms ease;
  svg { opacity: 0.85; }
  &:hover {
    background: #dbeafe; border-color: #93c5fd; color: #1e3a8a;
    box-shadow: 0 4px 10px rgba(37, 99, 235, 0.1);
    transform: translateY(-1px);
    svg { opacity: 1; }
  }
  &:active { transform: translateY(0); }
}
@media (prefers-reduced-motion: reduce) {
  .ed-similar-chip, .ed-secondary-action {
    transition: none;
    &:hover { transform: none; }
  }
}
</style>
