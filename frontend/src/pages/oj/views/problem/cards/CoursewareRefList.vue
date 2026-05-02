<template>
  <div v-if="refs.length" class="cwl-block" :class="['cwl-' + tone]">
    <div class="cwl-toggle" @click="expanded = !expanded">
      <span>课件参考（第 {{ refs[0].chapter }} 章）</span>
      <el-icon :size="12"><component :is="expanded ? ArrowUp : ArrowDown" /></el-icon>
    </div>
    <div v-show="expanded" class="cwl-list">
      <button
        v-for="(ref, idx) in refs"
        :key="'cw-' + idx"
        type="button"
        class="cwl-item"
        :class="{ 'is-previewable': isPreviewable(ref) }"
        :disabled="!isPreviewable(ref)"
        @click="handleClick(ref)"
      >
        <span v-if="ref.slide_number" class="cwl-slide">P{{ ref.slide_number }}</span>
        <span class="cwl-preview">{{ ref.preview }}</span>
        <span v-if="isPreviewable(ref)" class="cwl-action">查看课件</span>
      </button>
    </div>
  </div>
</template>

<script>
import { markRaw } from 'vue'
import { ArrowUp, ArrowDown } from '@element-plus/icons-vue'

export default {
  name: 'CoursewareRefList',
  emits: ['open-courseware-ref'],
  props: {
    refs: { type: Array, default: () => [] },
    tone: { type: String, default: 'accent' }
  },
  data () {
    return {
      expanded: false,
      ArrowUp: markRaw(ArrowUp),
      ArrowDown: markRaw(ArrowDown)
    }
  },
  methods: {
    isPreviewable (ref) { return !!(ref && ref.document_id && ref.page_no) },
    handleClick (ref) {
      if (!this.isPreviewable(ref)) return
      this.$emit('open-courseware-ref', ref)
    }
  }
}
</script>

<style lang="less" scoped>
.cwl-block {
  border: 0.5px solid var(--card-accent-border);
  border-radius: 8px; overflow: hidden;
  background: var(--card-accent-bg);
}
.cwl-toggle {
  display: flex; align-items: center; justify-content: space-between;
  padding: 8px 12px; cursor: pointer;
  font-size: var(--card-font-body); font-weight: 500;
  color: var(--card-accent);
  &:hover { background: var(--card-accent-bg-strong); }
}
.cwl-list { padding: 6px 12px 10px; }
.cwl-item {
  font-size: var(--card-font-label); color: var(--text-secondary); line-height: 1.6;
  padding: 8px 0; display: flex; gap: 6px; width: 100%;
  border: none; background: transparent; text-align: left; font-family: inherit; cursor: default;
  &.is-previewable { cursor: pointer; &:hover { color: var(--card-accent); } }
}
.cwl-slide { font-weight: 600; color: var(--card-accent); flex-shrink: 0; }
.cwl-preview { flex: 1; min-width: 0; }
.cwl-action { flex-shrink: 0; color: var(--card-accent); font-weight: 600; }
</style>
