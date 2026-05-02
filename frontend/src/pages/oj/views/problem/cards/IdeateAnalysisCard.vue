<template>
  <BaseAgentCard v-if="data" accent="ideate" :icon="bulbIcon" title="思路分析">
    <template #body>
      <div v-if="data.analysis" class="ia-desc ia-md" v-html="renderMarkdown(data.analysis)"></div>

      <div v-if="cleanedSteps.length" class="ia-steps">
        <div v-for="(step, idx) in cleanedSteps" :key="idx" class="ia-step">
          <div class="ia-step-num">{{ idx + 1 }}</div>
          <div class="ia-step-text ia-md" v-html="renderMarkdown(step)"></div>
        </div>
      </div>

      <div v-if="data.guiding_questions && data.guiding_questions.length" class="ia-questions">
        <div class="ia-q-label">启发问题</div>
        <ul class="ia-q-list">
          <li v-for="(q, idx) in data.guiding_questions" :key="idx">{{ q }}</li>
        </ul>
      </div>

      <div v-if="data.misconception_alert" class="ia-warn">
        <div class="ia-warn-label">
          <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor"
               stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/>
            <line x1="12" y1="9" x2="12" y2="13"/>
            <line x1="12" y1="17" x2="12.01" y2="17"/>
          </svg>
          注意
        </div>
        <span class="ia-md" v-html="renderMarkdown(data.misconception_alert)"></span>
      </div>
    </template>

    <template #body v-if="data && data.kc_error_refs && data.kc_error_refs.length">
      <div class="ia-kc-refs">
        <div class="ia-kc-title">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 19.5v-15A2.5 2.5 0 0 1 6.5 2H20v20H6.5a2.5 2.5 0 0 1 0-5H20"/></svg>
          这道题涉及你薄弱的知识点：
        </div>
        <div class="ia-kc-chips">
          <span v-for="ref in data.kc_error_refs" :key="ref.kc_id" class="ia-kc-chip">
            <span class="ia-kc-name">{{ ref.kc_name }}</span>
            <span class="ia-kc-count">{{ ref.error_count }} 次错过</span>
          </span>
        </div>
      </div>
    </template>

    <template #foot v-if="cleanedSteps.length >= 2 && canRequestSkeleton">
      <button class="ia-btn-primary" @click="$emit('request-skeleton')">
        <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor"
             stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
          <polyline points="16 18 22 12 16 6"/>
          <polyline points="8 6 2 12 8 18"/>
        </svg>
        生成骨架代码
      </button>
    </template>
  </BaseAgentCard>
</template>

<script>
import { markRaw, h } from 'vue'
import BaseAgentCard from './BaseAgentCard.vue'
import marked from 'marked'
import { sanitize } from '@/utils/sanitize'

const BulbIcon = {
  render () {
    return h('svg', {
      width: '14', height: '14', viewBox: '0 0 24 24', fill: 'none',
      stroke: 'currentColor', 'stroke-width': '2',
      'stroke-linecap': 'round', 'stroke-linejoin': 'round'
    }, [
      h('circle', { cx: '12', cy: '12', r: '10' }),
      h('path', { d: 'M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3' }),
      h('line', { x1: '12', y1: '17', x2: '12.01', y2: '17' })
    ])
  }
}

export default {
  name: 'IdeateAnalysisCard',
  components: { BaseAgentCard },
  props: {
    data: { type: Object, default: () => null },
    canRequestSkeleton: { type: Boolean, default: true }
  },
  data () {
    return { bulbIcon: markRaw(BulbIcon) }
  },
  computed: {
    cleanedSteps () {
      if (!this.data || !Array.isArray(this.data.steps)) return []
      return this.data.steps.map(s => String(s || '').replace(/^\s*\d+\s*[.、)）]\s*/, ''))
    }
  },
  methods: {
    renderMarkdown (text) {
      if (!text) return ''
      let html = sanitize(marked(text))
      return html.replace(/(\d+)\^([{(]?[-\w.+]+[})]?)/g, '$1<sup>$2</sup>')
    }
  }
}
</script>

<style lang="less" scoped>
.ia-desc {
  font-size: var(--card-font-body); color: var(--text-secondary); line-height: 1.8;
  border-left: 3px solid var(--card-accent); padding: 6px 0 6px 12px;
  background: var(--card-accent-bg); border-radius: 0 8px 8px 0;
}
.ia-steps { display: flex; flex-direction: column; gap: 8px; }
.ia-step {
  display: flex; align-items: flex-start; gap: 12px;
  padding: 10px 12px; border-radius: 10px; border: 1px solid transparent; transition: all 0.15s;
  &:hover { background: var(--card-accent-bg); border-color: var(--card-accent-border); }
}
.ia-step-num {
  width: 22px; height: 22px; border-radius: 50%; flex-shrink: 0;
  background: var(--card-accent); display: flex; align-items: center; justify-content: center;
  font-size: 10px; font-weight: 700; color: #fff; margin-top: 1px;
  box-shadow: 0 1px 3px var(--card-accent-bg-strong);
}
.ia-step-text { font-size: var(--card-font-body); color: var(--text-primary); line-height: 1.7; }
.ia-questions {
  background: var(--card-accent-bg); border: 1px solid var(--card-accent-border);
  border-radius: 10px; padding: 12px 14px;
}
.ia-q-label {
  font-size: var(--card-font-label); font-weight: 600; color: var(--card-accent);
  letter-spacing: 0.4px; text-transform: uppercase; margin-bottom: 6px;
}
.ia-q-list {
  list-style: none; padding: 0; margin: 0;
  font-size: var(--card-font-body); color: var(--text-primary); line-height: 1.7;
  li {
    margin: 4px 0; padding-left: 16px; position: relative;
    &::before { content: '?'; position: absolute; left: 0; font-weight: 700; color: var(--card-accent); }
  }
}
.ia-warn {
  background: var(--card-accent-bg); border: 1px solid var(--card-accent-border);
  border-radius: 10px; padding: 12px 14px;
  font-size: var(--card-font-body); color: var(--card-accent); line-height: var(--card-line-height);
}
.ia-warn-label {
  font-size: var(--card-font-label); font-weight: 600; color: var(--card-accent);
  letter-spacing: 0.3px; text-transform: uppercase; margin-bottom: 6px;
  display: flex; align-items: center; gap: 6px;
}
.ia-btn-primary {
  display: flex; align-items: center; justify-content: center; gap: 8px;
  width: 100%; padding: 11px 16px; border-radius: 10px;
  font-size: var(--card-font-body); font-weight: 600; font-family: inherit;
  background: var(--card-accent); color: #fff; border: none; cursor: pointer;
  transition: filter 0.15s; box-shadow: 0 2px 6px var(--card-accent-bg-strong);
  &:hover { filter: brightness(0.92); }
}
.ia-md {
  :deep(p) { margin: 0 0 6px; &:last-child { margin-bottom: 0; } }
  :deep(strong) { font-weight: 700; }
  :deep(em) { font-style: italic; }
  :deep(ul), :deep(ol) { margin: 4px 0 6px; padding-left: 18px; }
  :deep(li) { margin: 2px 0; }
  :deep(code) {
    background: var(--card-accent-bg-strong); border: 1px solid var(--card-accent-border);
    border-radius: 4px; padding: 1px 5px; font-family: var(--font-mono);
    font-size: var(--card-font-code); color: var(--card-accent);
  }
}
.ia-kc-refs { margin-top: 10px; }
.ia-kc-title {
  display: flex; align-items: center; gap: 6px;
  font-size: 12px; font-weight: 600; color: #475569; margin-bottom: 6px;
}
.ia-kc-chips { display: flex; flex-wrap: wrap; gap: 6px; }
.ia-kc-chip {
  display: inline-flex; align-items: center; gap: 4px;
  background: #fef2f2; border: 1px solid #fecaca; color: #dc2626;
  padding: 4px 10px; border-radius: 6px; font-size: 12px;
}
.ia-kc-name { font-weight: 600; }
.ia-kc-count { opacity: 0.7; }
</style>
