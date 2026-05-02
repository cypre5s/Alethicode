<template>
  <BaseAgentCard v-if="data" accent="encouragement" :icon="heartIcon" title="加油打气">
    <template #body>
      <div v-if="character" class="ec-char-row">
        <img :src="characterSprite" class="ec-char-img" :alt="character.name" />
        <div class="ec-char-bubble">
          <span class="ec-char-name" :style="{ color: character.color }">{{ character.name }}</span>
          <span class="ec-char-line">{{ characterLine }}</span>
        </div>
      </div>

      <div class="ec-text" v-html="renderMarkdown(data.encouragement || '')"></div>

      <div v-if="coursewareRefs.length" class="ec-refs">
        <button
          v-for="(ref, ri) in coursewareRefs"
          :key="ri"
          class="ec-ref-chip"
          type="button"
          @click="$emit('open-courseware-ref', ref)"
        >
          <el-icon :size="12"><Reading /></el-icon>
          {{ ref.document_title || '课件' }} · P{{ ref.slide_number || ref.page_no }}
        </button>
      </div>

      <div v-if="recoveryProblems.length" class="ec-alt">
        <span class="ec-alt-label">试试相似的题目：</span>
        <a
          v-for="rp in recoveryProblems"
          :key="rp.problem_key || rp.problem_display_id"
          class="ec-alt-link"
          @click="$emit('open-recovery-problem', rp)"
        >
          {{ rp.title || rp.problem_display_id }}
        </a>
      </div>
    </template>
  </BaseAgentCard>
</template>

<script>
import { markRaw, h } from 'vue'
import { Reading } from '@element-plus/icons-vue'
import BaseAgentCard from './BaseAgentCard.vue'
import marked from 'marked'
import { sanitize } from '@/utils/sanitize'

const HeartIcon = {
  render () {
    return h('svg', {
      width: '14', height: '14', viewBox: '0 0 24 24', fill: 'none',
      stroke: 'currentColor', 'stroke-width': '2',
      'stroke-linecap': 'round', 'stroke-linejoin': 'round'
    }, [
      h('path', { d: 'M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z' })
    ])
  }
}

export default {
  name: 'EncouragementCard',
  components: { BaseAgentCard, Reading },
  emits: ['open-courseware-ref', 'open-recovery-problem'],
  props: {
    data: { type: Object, default: () => ({}) },
    character: { type: Object, default: null },
    characterSprite: { type: String, default: '' },
    characterLine: { type: String, default: '' }
  },
  data () {
    return { heartIcon: markRaw(HeartIcon) }
  },
  computed: {
    coursewareRefs () {
      return Array.isArray(this.data && this.data.courseware_refs) ? this.data.courseware_refs : []
    },
    recoveryProblems () {
      return Array.isArray(this.data && this.data.recovery_problems) ? this.data.recovery_problems : []
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
.ec-char-row { display: flex; align-items: center; gap: 10px; }
.ec-char-img {
  width: 48px; height: 60px; border-radius: 10px;
  object-fit: cover; object-position: top center; flex-shrink: 0;
}
.ec-char-bubble {
  display: flex; flex-direction: column; gap: 2px;
  padding: 8px 12px; border-radius: 10px; background: rgba(255, 255, 255, 0.7);
}
.ec-char-name { font-size: var(--card-font-label); font-weight: 700; }
.ec-char-line { font-size: var(--card-font-body); color: #555; line-height: 1.4; }

.ec-text {
  font-size: var(--card-font-body); line-height: 1.6;
  color: var(--text-primary, #333);
}

.ec-refs { display: flex; flex-wrap: wrap; gap: 6px; }
.ec-ref-chip {
  display: inline-flex; align-items: center; gap: 4px;
  padding: 5px 12px; border-radius: 20px;
  border: 1px solid var(--card-accent-border);
  background: var(--card-accent-bg);
  color: var(--card-accent); font-size: var(--card-font-body);
  cursor: pointer; transition: background 0.2s;
  font-family: inherit;
  &:hover { background: var(--card-accent-bg-strong); }
}

.ec-alt {
  padding-top: 8px;
  border-top: 1px solid var(--card-accent-border);
}
.ec-alt-label {
  font-size: var(--card-font-body); color: var(--text-secondary, #888); margin-right: 6px;
}
.ec-alt-link {
  font-size: var(--card-font-body); color: var(--card-accent); cursor: pointer;
  margin-right: 8px; text-decoration: underline;
  text-decoration-color: var(--card-accent-border);
  &:hover { text-decoration-color: var(--card-accent); }
}
</style>
