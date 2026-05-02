<template>
  <BaseAgentCard v-if="data" accent="review" :icon="readingIcon" title="知识点回顾">
    <template #body>
      <div v-if="relatedKcs.length" class="krc-tags">
        <span v-for="(kc, ki) in relatedKcs" :key="ki" class="krc-tag">{{ kc }}</span>
      </div>

      <div class="krc-body" v-html="renderMarkdown(data.review_content || '')"></div>

      <div v-if="practiceSuggestions.length" class="krc-practice">
        <span class="krc-practice-label">建议练习方向</span>
        <ul class="krc-practice-list">
          <li v-for="(s, si) in practiceSuggestions" :key="si">{{ s }}</li>
        </ul>
      </div>

      <div v-if="coursewareRefs.length" class="krc-refs">
        <button
          v-for="(ref, ri) in coursewareRefs"
          :key="ri"
          class="krc-ref-chip"
          type="button"
          @click="$emit('open-courseware-ref', ref)"
        >
          <el-icon :size="12"><Reading /></el-icon>
          {{ ref.document_title || '课件' }} · P{{ ref.slide_number || ref.page_no }}
        </button>
      </div>
    </template>

    <template #foot>
      <div class="krc-feedback">
        <button
          v-for="opt in feedbackOptions"
          :key="opt.value"
          type="button"
          :class="['krc-fb-btn', { 'is-active': feedbackValue === opt.value }]"
          @click="$emit('feedback', opt.value)"
        >{{ opt.label }}</button>
      </div>
    </template>
  </BaseAgentCard>
</template>

<script>
import { markRaw } from 'vue'
import { Reading } from '@element-plus/icons-vue'
import BaseAgentCard from './BaseAgentCard.vue'
import marked from 'marked'
import { sanitize } from '@/utils/sanitize'

const FEEDBACK_OPTIONS = [
  { value: 'helpful', label: '👍 有帮助' },
  { value: 'unhelpful', label: '👎 没帮助' },
  { value: 'confusing', label: '❓ 看不懂' }
]

export default {
  name: 'KnowledgeReviewCard',
  components: { BaseAgentCard, Reading },
  emits: ['feedback', 'open-courseware-ref'],
  props: {
    data: { type: Object, default: () => ({}) },
    feedbackValue: { type: String, default: '' }
  },
  data () {
    return {
      readingIcon: markRaw(Reading),
      feedbackOptions: FEEDBACK_OPTIONS
    }
  },
  computed: {
    relatedKcs () { return Array.isArray(this.data && this.data.related_kcs) ? this.data.related_kcs : [] },
    practiceSuggestions () {
      return Array.isArray(this.data && this.data.practice_suggestions) ? this.data.practice_suggestions : []
    },
    coursewareRefs () {
      return Array.isArray(this.data && this.data.courseware_refs) ? this.data.courseware_refs : []
    }
  },
  methods: {
    renderMarkdown (text) {
      if (!text) return ''
      return sanitize(marked(text))
    }
  }
}
</script>

<style lang="less" scoped>
.krc-tags { display: flex; flex-wrap: wrap; gap: 6px; }
.krc-tag {
  display: inline-block; padding: 3px 10px; border-radius: 999px;
  font-size: var(--card-font-body); background: var(--card-accent-bg-strong);
  color: var(--card-accent); border: 1px solid var(--card-accent-border);
}
.krc-body {
  font-size: var(--card-font-body); line-height: 1.65; color: var(--text-primary, #1f2937);
  :deep(h1), :deep(h2), :deep(h3) {
    margin: 10px 0 6px; font-size: var(--card-font-body);
    color: var(--card-accent);
  }
  :deep(ul), :deep(ol) { padding-left: 18px; margin: 4px 0 10px; }
  :deep(code) {
    padding: 1px 5px; border-radius: 4px;
    background: var(--card-accent-bg-strong);
    font-size: var(--card-font-code);
  }
  :deep(p) { margin: 0 0 6px; &:last-child { margin-bottom: 0; } }
}
.krc-practice {
  background: var(--card-accent-bg); border: 1px solid var(--card-accent-border);
  border-radius: 10px; padding: 10px 12px;
}
.krc-practice-label {
  display: block; font-size: var(--card-font-label); font-weight: 600;
  color: var(--card-accent); letter-spacing: 0.4px;
  text-transform: uppercase; margin-bottom: 6px;
}
.krc-practice-list { padding-left: 18px; margin: 0; font-size: var(--card-font-body); color: var(--text-primary); }
.krc-practice-list li { margin: 3px 0; line-height: 1.6; }
.krc-refs { display: flex; flex-wrap: wrap; gap: 6px; }
.krc-ref-chip {
  display: inline-flex; align-items: center; gap: 4px;
  padding: 5px 12px; border-radius: 20px;
  border: 1px solid var(--card-accent-border);
  background: var(--card-accent-bg);
  color: var(--card-accent); font-size: var(--card-font-body);
  cursor: pointer; transition: background 0.2s, border-color 0.2s;
  font-family: inherit;
  &:hover { background: var(--card-accent-bg-strong); }
}

.krc-feedback { display: flex; gap: 8px; flex-wrap: wrap; }
.krc-fb-btn {
  border: 1px solid var(--card-accent-border);
  background: transparent; color: var(--card-accent);
  padding: 5px 12px; border-radius: 6px;
  font-size: var(--card-font-body); cursor: pointer; min-height: 30px;
  transition: all 0.15s;
  &:hover { background: var(--card-accent-bg); }
  &.is-active { background: var(--card-accent); color: #fff; border-color: var(--card-accent); }
}
</style>
