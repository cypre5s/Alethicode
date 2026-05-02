<template>
  <transition name="ndd-fade">
    <div v-if="modelValue" class="ndd-overlay" @click.self="close">
      <div class="ndd-panel" role="dialog" :aria-label="title">
        <header class="ndd-head">
          <div>
            <div class="ndd-title">{{ title }}</div>
            <div v-if="subtitle" class="ndd-subtitle">{{ subtitle }}</div>
          </div>
          <button type="button" class="ndd-close" @click="close" aria-label="关闭">×</button>
        </header>
        <section class="ndd-body">
          <div v-if="!items.length" class="ndd-empty">这一天没有错题或复习计划。</div>
          <div v-else class="ndd-list">
            <div v-for="item in items" :key="itemKey(item)" class="ndd-item" :data-kind="item.kind">
              <div class="ndd-item-head">
                <span :class="['ndd-tag', 'ndd-tag-' + (item.kind === 'review' ? 'review' : 'entry')]">
                  {{ item.kind === 'review' ? '复习' : '错题' }}
                </span>
                <span class="ndd-item-title">{{ item.label || (item.kind === 'review' ? '复习包' : '错题记录') }}</span>
                <span v-if="item.kind === 'review' && item.is_due" class="ndd-due">今日到期</span>
              </div>
              <div v-if="item.summary" class="ndd-item-summary">{{ item.summary }}</div>
              <div v-if="item.kind === 'review' && (item.stability != null || item.retrievability != null)" class="ndd-item-meta">
                <span v-if="item.stability != null">稳定度 {{ item.stability.toFixed(2) }}</span>
                <span v-if="item.retrievability != null">回忆度 {{ item.retrievability.toFixed(2) }}</span>
              </div>
              <div class="ndd-item-actions">
                <button
                  v-if="item.kind === 'review' && item.active_package_id"
                  type="button"
                  class="ndd-go-btn"
                  @click="$emit('open-review-package', item)"
                >去复习</button>
                <button
                  v-else-if="item.kind === 'entry' && item.problem_id"
                  type="button"
                  class="ndd-go-btn ndd-go-btn-secondary"
                  @click="$emit('open-problem', item)"
                >重做此题</button>
              </div>
            </div>
          </div>
        </section>
      </div>
    </div>
  </transition>
</template>

<script>
export default {
  name: 'NotebookDayDrawer',
  emits: ['update:modelValue', 'open-review-package', 'open-problem'],
  props: {
    modelValue: { type: Boolean, default: false },
    title: { type: String, default: '当日复习' },
    subtitle: { type: String, default: '' },
    items: { type: Array, default: () => [] }
  },
  mounted () { window.addEventListener('keydown', this.onKey) },
  beforeUnmount () { window.removeEventListener('keydown', this.onKey) },
  methods: {
    close () { this.$emit('update:modelValue', false) },
    onKey (e) { if (e.key === 'Escape' && this.modelValue) this.close() },
    itemKey (item) {
      return (item.kind || 'i') + '-' + (item.id || item.active_package_id || item.problem_id || item.label || Math.random())
    }
  }
}
</script>

<style lang="less" scoped>
.ndd-overlay {
  position: fixed; inset: 0; background: rgba(15, 23, 42, 0.35);
  z-index: 200; display: flex; justify-content: flex-end;
}
.ndd-panel {
  width: 380px; max-width: 100%; background: #fff;
  height: 100%; display: flex; flex-direction: column;
  box-shadow: -8px 0 24px rgba(15, 23, 42, 0.18);
}
.ndd-head {
  padding: 16px 20px; border-bottom: 1px solid #e8eaed;
  display: flex; align-items: flex-start; justify-content: space-between; gap: 12px;
}
.ndd-title { font-size: 15px; font-weight: 700; color: #1a1d2e; }
.ndd-subtitle { font-size: 12px; color: #64748b; margin-top: 2px; }
.ndd-close {
  border: none; background: transparent; cursor: pointer;
  font-size: 22px; line-height: 1; color: #94a3b8; font-family: inherit;
  &:hover { color: #ef4444; }
}
.ndd-body { padding: 14px 20px; overflow-y: auto; flex: 1; }
.ndd-empty { text-align: center; color: #94a3b8; padding: 40px 0; font-size: 13px; }
.ndd-list { display: flex; flex-direction: column; gap: 12px; }
.ndd-item {
  border: 1px solid #e8eaed; border-radius: 10px;
  padding: 12px 14px; background: #fff;
  &[data-kind="review"] { border-left: 3px solid #6366f1; }
  &[data-kind="entry"] { border-left: 3px solid #ef4444; }
}
.ndd-item-head { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.ndd-tag { font-size: 11px; font-weight: 600; padding: 2px 8px; border-radius: 999px; }
.ndd-tag-review { background: #e0e7ff; color: #4338ca; }
.ndd-tag-entry { background: #fee2e2; color: #b91c1c; }
.ndd-item-title { font-size: 13px; font-weight: 600; color: #1a1d2e; }
.ndd-due {
  font-size: 11px; padding: 2px 8px; border-radius: 999px;
  background: #fef2f2; color: #b91c1c; border: 1px solid #fecaca;
}
.ndd-item-summary { font-size: 12px; color: #475569; line-height: 1.6; margin-top: 6px; }
.ndd-item-meta { font-size: 11px; color: #64748b; display: flex; gap: 10px; margin-top: 6px; }
.ndd-item-actions { display: flex; justify-content: flex-end; margin-top: 10px; gap: 8px; }
.ndd-go-btn {
  border: none; padding: 6px 14px; border-radius: 6px;
  background: #1a73e8; color: #fff;
  font-size: 12px; font-weight: 600; cursor: pointer; font-family: inherit;
  transition: background 0.15s;
  &:hover { background: #1558d6; }
}
.ndd-go-btn-secondary { background: #475569; &:hover { background: #334155; } }

.ndd-fade-enter-active, .ndd-fade-leave-active { transition: opacity 0.18s; }
.ndd-fade-enter-from, .ndd-fade-leave-to { opacity: 0; }
</style>
