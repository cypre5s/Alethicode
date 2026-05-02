<template>
  <div v-if="hasEvidence" class="ev-wrap">
    <div class="ev-toggle" @click="expanded = !expanded" role="button" tabindex="0" @keydown.enter="expanded = !expanded">
      <span class="ev-toggle-icon">{{ expanded ? '▾' : '▸' }}</span>
      <span class="ev-toggle-label">参考依据（{{ totalCount }}）</span>
    </div>
    <transition name="ev-slide">
      <div v-show="expanded" class="ev-list">
        <div v-for="(ref, i) in normalizedRefs" :key="i" class="ev-item" @click="$emit('open-ref', ref)">
          <span class="ev-type-badge" :class="'ev-type-' + ref.type">{{ ref.typeLabel }}</span>
          <span class="ev-title">{{ ref.title }}</span>
        </div>
      </div>
    </transition>
  </div>
</template>

<script>
const TYPE_LABELS = {
  courseware: '课件',
  memory: '记忆',
  similar_error: '相似错误',
  submission: '提交记录'
}

export default {
  name: 'EvidenceRefs',
  props: {
    coursewareRefs: { type: Array, default: () => [] },
    memories: { type: Array, default: () => [] },
    similarErrors: { type: Array, default: () => [] }
  },
  emits: ['open-ref'],
  data () {
    return { expanded: false }
  },
  computed: {
    normalizedRefs () {
      const refs = []
      for (const c of this.coursewareRefs) {
        refs.push({
          type: 'courseware',
          typeLabel: TYPE_LABELS.courseware,
          title: c.page_title || c.title || '课件页',
          raw: c
        })
      }
      for (const m of this.memories) {
        refs.push({
          type: 'memory',
          typeLabel: TYPE_LABELS.memory,
          title: m.memory_value || m.summary || '学习记忆',
          raw: m
        })
      }
      for (const e of this.similarErrors) {
        refs.push({
          type: 'similar_error',
          typeLabel: TYPE_LABELS.similar_error,
          title: e.description || e.summary || '相似错误',
          raw: e
        })
      }
      return refs
    },
    totalCount () {
      return this.normalizedRefs.length
    },
    hasEvidence () {
      return this.totalCount > 0
    }
  }
}
</script>

<style scoped>
.ev-wrap {
  margin-top: 8px;
  border: 1px solid var(--border-color, #e2e8f0);
  border-radius: 8px;
  overflow: hidden;
}

.ev-toggle {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  cursor: pointer;
  font-size: 12px;
  color: var(--text-secondary, #64748b);
  background: rgba(248, 250, 252, 0.5);
  min-height: 44px;
  user-select: none;
}

.ev-toggle:hover {
  background: rgba(241, 245, 249, 0.8);
}

.ev-toggle-icon { font-size: 10px; }
.ev-toggle-label { font-weight: 500; }

.ev-list { padding: 4px 0; }

.ev-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  cursor: pointer;
  transition: background 0.1s;
  min-height: 36px;
}

.ev-item:hover {
  background: rgba(241, 245, 249, 0.8);
}

.ev-type-badge {
  font-size: 10px;
  padding: 2px 6px;
  border-radius: 4px;
  font-weight: 600;
  flex-shrink: 0;
}

.ev-type-courseware {
  background: #dbeafe;
  color: #1d4ed8;
}

.ev-type-memory {
  background: #ede9fe;
  color: #6d28d9;
}

.ev-type-similar_error {
  background: #fef3c7;
  color: #92400e;
}

.ev-title {
  font-size: 12px;
  color: var(--text-primary, #1e293b);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ev-slide-enter-active,
.ev-slide-leave-active {
  transition: max-height 0.25s ease, opacity 0.2s ease;
  max-height: 400px;
  overflow: hidden;
}

.ev-slide-enter-from,
.ev-slide-leave-to {
  max-height: 0;
  opacity: 0;
}
</style>
