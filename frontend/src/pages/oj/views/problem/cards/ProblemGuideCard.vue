<template>
  <BaseAgentCard v-if="data" accent="guide" :icon="readingIcon" title="审题引导">
    <template #body>
      <div v-if="data.problem_restatement" class="gc-summary gc-md" v-html="renderMarkdown(data.problem_restatement)"></div>
      <div v-if="data.input_output_focus" class="gc-desc gc-md" v-html="renderMarkdown(data.input_output_focus)"></div>

      <div v-if="data.key_observation" class="gc-hint">
        <div class="gc-hint-label">关键观察</div>
        <div class="gc-md" v-html="renderMarkdown(data.key_observation)"></div>
      </div>

      <div v-if="data.starter_questions && data.starter_questions.length" class="gc-questions">
        <div class="gc-hint-label">建议练习方向</div>
        <ul class="gc-q-list">
          <li v-for="(q, idx) in data.starter_questions" :key="idx">
            <button v-if="canStartIdeate" type="button" class="gc-q-btn" @click="$emit('ask-question', q)">{{ q }}</button>
            <span v-else>{{ q }}</span>
          </li>
        </ul>
      </div>

      <ReasoningChain v-if="data.reasoning_chain" :chain="data.reasoning_chain" />
      <ToolCallTimeline v-if="data.tool_calls" :calls="data.tool_calls" />
      <EvidenceRefs
        :courseware-refs="data.courseware_refs || []"
        :memories="data.memory_refs || []"
        @open-courseware-ref="$emit('open-courseware-ref', $event)"
      />

      <CoursewareRefList :refs="coursewareRefs" @open-courseware-ref="$emit('open-courseware-ref', $event)" />
    </template>

    <template #foot v-if="data.starter_questions && data.starter_questions.length && canStartIdeate">
      <button class="gc-btn" @click="$emit('start-ideate', data.starter_questions[0])">
        思路分析
        <svg width="11" height="11" viewBox="0 0 24 24" fill="none"
             stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
          <line x1="5" y1="12" x2="19" y2="12"/>
          <polyline points="12 5 19 12 12 19"/>
        </svg>
      </button>
    </template>
  </BaseAgentCard>
</template>

<script>
import { markRaw } from 'vue'
import { Reading } from '@element-plus/icons-vue'
import BaseAgentCard from './BaseAgentCard.vue'
import CoursewareRefList from './CoursewareRefList.vue'
import ReasoningChain from './ReasoningChain.vue'
import ToolCallTimeline from './ToolCallTimeline.vue'
import EvidenceRefs from './EvidenceRefs.vue'
import marked from 'marked'
import { sanitize } from '@/utils/sanitize'

export default {
  name: 'ProblemGuideCard',
  components: { BaseAgentCard, CoursewareRefList, ReasoningChain, ToolCallTimeline, EvidenceRefs },
  props: {
    data: {
      type: Object,
      default: () => ({
        problem_restatement: '', input_output_focus: '', key_observation: '',
        starter_questions: [], related_kcs: [], courseware_refs: []
      })
    },
    canStartIdeate: { type: Boolean, default: true }
  },
  data () {
    return { readingIcon: markRaw(Reading) }
  },
  computed: {
    coursewareRefs () { return (this.data && this.data.courseware_refs) || [] }
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
.gc-summary {
  font-size: var(--card-font-body); font-weight: 500; color: var(--text-primary);
  line-height: 1.7; border-left: 3px solid var(--card-accent);
  padding: 6px 0 6px 12px; background: var(--card-accent-bg);
  border-radius: 0 8px 8px 0;
}
.gc-desc { font-size: var(--card-font-body); color: var(--text-secondary); line-height: 1.8; }
.gc-hint {
  background: linear-gradient(135deg, rgba(249, 250, 251, 0.8), rgba(243, 244, 246, 0.6));
  border: 1px solid var(--card-accent-border); border-radius: 10px; padding: 12px 14px;
  font-size: var(--card-font-body); color: var(--text-secondary); line-height: var(--card-line-height);
}
.gc-hint-label {
  font-size: var(--card-font-label); font-weight: 600; color: var(--card-accent);
  letter-spacing: 0.4px; text-transform: uppercase; margin-bottom: 6px;
}
.gc-q-list { list-style: none; padding: 0; margin: 8px 0 0; display: flex; flex-direction: column; gap: 8px; }
.gc-q-list li {
  font-size: var(--card-font-body); color: var(--text-primary); line-height: 1.6;
  padding: 6px 10px; background: var(--card-accent-bg); border-radius: 8px;
  border: 1px solid transparent; transition: border-color 0.15s;
  &:hover { border-color: var(--card-accent-border); }
}
.gc-q-btn {
  all: unset; cursor: pointer; color: var(--card-accent);
  font-size: var(--card-font-body); line-height: 1.6; font-weight: 500;
  &:hover { text-decoration: underline; }
}
.gc-md {
  :deep(p) { margin: 0 0 6px; &:last-child { margin-bottom: 0; } }
  :deep(strong) { font-weight: 700; }
  :deep(em) { font-style: italic; }
  :deep(ul), :deep(ol) { margin: 4px 0 8px; padding-left: 20px; }
  :deep(li) { margin: 3px 0; }
  :deep(code) {
    background: var(--card-accent-bg-strong); border: 1px solid var(--card-accent-border);
    border-radius: 4px; padding: 1px 5px; font-family: var(--font-mono);
    font-size: var(--card-font-code); color: var(--card-accent);
  }
}
.gc-btn {
  display: inline-flex; align-items: center; gap: 6px;
  font-size: var(--card-font-body); font-weight: 600; color: #fff; cursor: pointer;
  padding: 7px 16px; border-radius: 8px; border: none; background: var(--card-accent);
  transition: filter 0.15s; font-family: inherit; box-shadow: 0 1px 3px rgba(37, 99, 235, 0.3);
  &:hover { filter: brightness(0.92); }
}

</style>
