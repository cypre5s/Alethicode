<template>
  <div class="nkc-card" tabindex="0" role="button" @click="$emit('expand')" @keydown.enter="$emit('expand')">
    <div class="nkc-card-header">
      <span class="nkc-card-name">{{ kc.kc_name }}</span>
      <div class="nkc-card-counts">
        <span v-if="kc.error_count" class="nkc-count nkc-count-error">{{ kc.error_count }} 错</span>
        <span v-if="kc.breakthrough_count" class="nkc-count nkc-count-bt">{{ kc.breakthrough_count }} 悟</span>
      </div>
    </div>

    <div class="nkc-mastery">
      <div class="nkc-mastery-bar" role="progressbar" :aria-valuenow="masteryPct" aria-valuemin="0" aria-valuemax="100">
        <div class="nkc-mastery-fill" :class="masteryClass" :style="{ width: masteryPct + '%' }"></div>
      </div>
      <div class="nkc-mastery-row">
        <span class="nkc-mastery-label">{{ masteryLabel }}</span>
        <span class="nkc-mastery-pct">{{ masteryPct }}<span class="nkc-mastery-unit">%</span></span>
      </div>
    </div>

    <div v-if="kc.recent_entries && kc.recent_entries.length" class="nkc-previews">
      <div v-for="e in kc.recent_entries" :key="e.id" class="nkc-preview-item">
        <span class="nkc-preview-type" :class="'nkc-type-' + e.entry_type">{{ e.entry_type === 'error' ? '错' : '悟' }}</span>
        <span class="nkc-preview-text">{{ e.root_cause || e.entry_type }}</span>
      </div>
    </div>

    <div class="nkc-expand-hint">
      <span>展开全部</span>
      <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="9 18 15 12 9 6"/></svg>
    </div>
  </div>
</template>

<script>
export default {
  name: 'NotebookKcCard',
  emits: ['expand'],
  props: {
    kc: { type: Object, required: true }
  },
  computed: {
    masteryPct () {
      return Math.round((this.kc.mastery_value || 0) * 100)
    },
    masteryClass () {
      const pct = this.masteryPct
      if (pct >= 70) return 'nkc-mastery-high'
      if (pct >= 40) return 'nkc-mastery-mid'
      return 'nkc-mastery-low'
    },
    masteryLabel () {
      const pct = this.masteryPct
      if (pct >= 80) return '已熟练'
      if (pct >= 60) return '基本掌握'
      if (pct >= 40) return '继续加油'
      if (pct >= 20) return '需要练习'
      return '刚起步'
    }
  }
}
</script>

<style lang="less" scoped>
.nkc-card {
  background: var(--nb-bg-surface);
  border: 1px solid var(--nb-border-soft);
  border-radius: var(--nb-radius-lg);
  padding: 16px 18px;
  cursor: pointer;
  transition: transform var(--nb-transition), box-shadow var(--nb-transition), border-color var(--nb-transition);
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 280px;

  &:hover {
    transform: translateY(-3px);
    box-shadow: var(--nb-shadow-mid);
    border-color: var(--nb-border-mid);
  }

  &:focus-visible {
    outline: none;
    box-shadow: var(--nb-shadow-glow);
  }
}

.nkc-card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 8px;
}

.nkc-card-name {
  font-size: 15px;
  font-weight: 700;
  color: var(--nb-color-text);
  line-height: 1.3;
  word-break: break-all;
}

.nkc-card-counts {
  display: flex;
  gap: 4px;
  flex-shrink: 0;
}

.nkc-count {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 999px;
  font-weight: 700;
  font-feature-settings: 'tnum';
  border: 1px solid transparent;

  &.nkc-count-error {
    background: linear-gradient(135deg, #fee2e2, #fecaca);
    color: #b91c1c;
    border-color: rgba(252, 165, 165, 0.6);
  }

  &.nkc-count-bt {
    background: linear-gradient(135deg, #ede9fe, #ddd6fe);
    color: #5b21b6;
    border-color: rgba(196, 181, 253, 0.6);
  }
}

.nkc-mastery {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.nkc-mastery-bar {
  height: 8px;
  background: var(--nb-bg-subtle);
  border-radius: 999px;
  overflow: hidden;
  border: 1px solid var(--nb-border-soft);
}

.nkc-mastery-fill {
  height: 100%;
  border-radius: 999px;
  transition: width 600ms cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
  overflow: hidden;

  &::after {
    content: '';
    position: absolute;
    inset: 0;
    background: linear-gradient(90deg, transparent 0%, rgba(255, 255, 255, 0.5) 50%, transparent 100%);
    animation: nkc-shine 2.4s linear infinite;
  }

  &.nkc-mastery-low {
    background: linear-gradient(90deg, #ef4444 0%, #f59e0b 100%);
  }

  &.nkc-mastery-mid {
    background: linear-gradient(90deg, #f59e0b 0%, #fbbf24 100%);
  }

  &.nkc-mastery-high {
    background: linear-gradient(90deg, #10b981 0%, #06b6d4 100%);
  }
}

.nkc-mastery-row {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
}

.nkc-mastery-label {
  font-size: 11px;
  color: var(--nb-color-text-mid);
  font-weight: 600;
}

.nkc-mastery-pct {
  font-size: 16px;
  font-weight: 800;
  color: var(--nb-color-text);
  font-feature-settings: 'tnum';
  line-height: 1;
}

.nkc-mastery-unit {
  font-size: 11px;
  color: var(--nb-color-text-dim);
  font-weight: 600;
  margin-left: 1px;
}

.nkc-previews {
  display: flex;
  flex-direction: column;
  gap: 6px;
  background: var(--nb-bg-subtle);
  border-radius: 10px;
  padding: 8px 10px;
}

.nkc-preview-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--nb-color-text-mid);
}

.nkc-preview-type {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 4px;
  font-weight: 700;
  flex-shrink: 0;

  &.nkc-type-error {
    background: rgba(239, 68, 68, 0.12);
    color: #dc2626;
  }

  &.nkc-type-breakthrough {
    background: rgba(124, 58, 237, 0.14);
    color: #7c3aed;
  }
}

.nkc-preview-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.nkc-expand-hint {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  font-size: 12px;
  color: var(--nb-color-primary-strong);
  font-weight: 600;
  text-align: center;
  padding-top: 4px;
  border-top: 1px dashed var(--nb-border-soft);
}

@keyframes nkc-shine {
  0% { transform: translateX(-100%); }
  100% { transform: translateX(100%); }
}

@media (prefers-reduced-motion: reduce) {
  .nkc-card,
  .nkc-mastery-fill,
  .nkc-mastery-fill::after {
    transition: none !important;
    animation: none !important;
  }
}
</style>
