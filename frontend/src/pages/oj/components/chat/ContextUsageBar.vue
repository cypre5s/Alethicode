<template>
  <div class="context-usage-bar" :class="severityClass" v-if="hasData || loading">
    <div class="context-usage-meta">
      <span class="context-usage-label">{{ loading ? '加载中…' : displayText }}</span>
      <button
        v-if="showCompactHint"
        type="button"
        class="context-usage-compact-btn"
        @click="$emit('compact-click')"
      >
        /compact 整理上下文
      </button>
    </div>
    <div class="context-usage-track" v-if="!loading">
      <div class="context-usage-fill" :style="fillStyle"></div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'ContextUsageBar',
  emits: ['compact-click'],
  props: {
    tokensUsed: { type: Number, default: 0 },
    tokensLimit: { type: Number, default: 0 },
    modelName: { type: String, default: '' },
    loading: { type: Boolean, default: false }
  },
  computed: {
    hasData () {
      return this.tokensLimit > 0
    },
    ratio () {
      if (!this.tokensLimit || this.tokensLimit <= 0) return 0
      const used = Math.max(0, this.tokensUsed || 0)
      return Math.min(1, used / this.tokensLimit)
    },
    severityClass () {
      const r = this.ratio
      if (r >= 0.8) return 'is-danger'
      if (r >= 0.5) return 'is-warning'
      return 'is-safe'
    },
    fillStyle () {
      return { width: `${(this.ratio * 100).toFixed(1)}%` }
    },
    showCompactHint () {
      return this.ratio >= 0.8
    },
    displayText () {
      if (!this.hasData) return ''
      const used = Math.round((this.tokensUsed || 0) / 100) / 10
      const limit = Math.round((this.tokensLimit || 0) / 100) / 10
      const model = this.modelName ? ` · ${this.modelName}` : ''
      return `${used}k / ${limit}k${model}`
    }
  }
}
</script>

<style lang="less" scoped>
.context-usage-bar {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 6px 10px;
  border: 1px solid var(--border-color, #e5e7eb);
  border-radius: 6px;
  background: var(--bg-card, #fff);
}

.context-usage-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-size: 11.5px;
  color: var(--text-secondary, #475569);
}

.context-usage-label {
  font-family: var(--font-mono, ui-monospace, SFMono-Regular, Menlo, monospace);
}

.context-usage-compact-btn {
  border: 1px solid var(--color-warning, #d97706);
  color: var(--color-warning, #d97706);
  background: transparent;
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 4px;
  cursor: pointer;
  transition: background-color 150ms ease, color 150ms ease;

  &:hover {
    background: var(--color-warning, #d97706);
    color: #fff;
  }
}

.context-usage-track {
  position: relative;
  width: 100%;
  height: 4px;
  background: var(--bg-panel, #f1f5f9);
  border-radius: 2px;
  overflow: hidden;
}

.context-usage-fill {
  height: 100%;
  border-radius: 2px;
  transition: width 200ms ease, background-color 200ms ease;
}

.is-safe .context-usage-fill { background: var(--color-success, #10b981); }
.is-warning .context-usage-fill { background: var(--color-warning, #f59e0b); }
.is-danger .context-usage-fill { background: var(--color-danger, #ef4444); }

@media (prefers-reduced-motion: reduce) {
  .context-usage-fill,
  .context-usage-compact-btn {
    transition: none;
  }
}
</style>
