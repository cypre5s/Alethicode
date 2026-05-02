<template>
  <teleport to="body">
    <transition name="nkem-fade">
      <div class="nkem-overlay" @click.self="$emit('close')" @keydown.esc="$emit('close')" tabindex="-1">
        <div class="nkem-panel" role="dialog" aria-modal="true">
          <header class="nkem-head">
            <div>
              <div class="nkem-eyebrow">知识点详情</div>
              <div class="nkem-title">{{ currentKc ? currentKc.kc_name : '' }}</div>
              <div v-if="currentKc" class="nkem-sub">
                共 {{ recordCount }} 条记录
                <span v-if="currentKc.error_count" class="nkem-meta-error">{{ currentKc.error_count }} 错</span>
                <span v-if="currentKc.breakthrough_count" class="nkem-meta-bt">{{ currentKc.breakthrough_count }} 悟</span>
              </div>
            </div>
            <button type="button" class="nkem-close" aria-label="关闭" @click="$emit('close')">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M18 6 6 18"/><path d="m6 6 12 12"/></svg>
            </button>
          </header>
          <div v-if="currentKc" class="nkem-body">
            <div v-if="!currentKc.recent_entries || !currentKc.recent_entries.length" class="nkem-empty">暂无记录</div>
            <div
              v-for="entry in currentKc.recent_entries"
              :key="entry.id"
              class="nkem-item"
              :data-kind="entry.entry_type"
            >
              <span class="nkem-type" :class="'nkem-type-' + entry.entry_type">{{ entry.entry_type === 'error' ? '错' : '悟' }}</span>
              <span class="nkem-text">{{ entry.root_cause || '—' }}</span>
              <span class="nkem-time">{{ formatTime(entry.create_time) }}</span>
            </div>
          </div>
        </div>
      </div>
    </transition>
  </teleport>
</template>

<script>
export default {
  name: 'NotebookKcExpandModal',
  emits: ['close'],
  props: {
    kcId: { type: [Number, String], required: true },
    kcGroups: { type: Array, default: () => [] }
  },
  computed: {
    currentKc () {
      return this.kcGroups.find(g => g.kc_id === this.kcId) || null
    },
    recordCount () {
      return (this.currentKc && this.currentKc.recent_entries) ? this.currentKc.recent_entries.length : 0
    }
  },
  methods: {
    formatTime (val) {
      if (!val) return ''
      const d = new Date(val)
      return Number.isNaN(d.getTime()) ? val : d.toLocaleDateString()
    }
  }
}
</script>

<style lang="less" scoped>
.nkem-overlay {
  position: fixed;
  inset: 0;
  z-index: 2000;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(30, 27, 58, 0.42);
  backdrop-filter: blur(4px);
}

.nkem-panel {
  background: var(--nb-bg-surface, #fff);
  border-radius: var(--nb-radius-xl, 22px);
  width: 100%;
  max-width: 580px;
  max-height: 75vh;
  overflow: hidden;
  box-shadow: 0 24px 60px rgba(30, 27, 58, 0.25);
  display: flex;
  flex-direction: column;
}

.nkem-head {
  padding: 20px 24px 16px;
  border-bottom: 1px solid var(--nb-border-soft, rgba(196, 181, 253, 0.32));
  background: var(--nb-hero-bg, linear-gradient(135deg, #f5f3ff 0%, #fdf4ff 100%));
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.nkem-eyebrow {
  display: inline-block;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.6px;
  text-transform: uppercase;
  color: var(--nb-color-primary-strong, #4f46e5);
  background: rgba(196, 181, 253, 0.32);
  padding: 2px 10px;
  border-radius: 999px;
  margin-bottom: 6px;
}

.nkem-title {
  font-size: 18px;
  font-weight: 800;
  color: var(--nb-color-text, #1e1b3a);
  letter-spacing: -0.3px;
}

.nkem-sub {
  font-size: 12px;
  color: var(--nb-color-text-mid, #5b5973);
  margin-top: 4px;
  display: inline-flex;
  gap: 8px;
  align-items: center;
}

.nkem-meta-error,
.nkem-meta-bt {
  font-size: 11px;
  font-weight: 700;
  padding: 1px 8px;
  border-radius: 999px;
}

.nkem-meta-error {
  background: rgba(239, 68, 68, 0.12);
  color: #b91c1c;
}

.nkem-meta-bt {
  background: rgba(124, 58, 237, 0.14);
  color: #5b21b6;
}

.nkem-close {
  border: none;
  background: rgba(255, 255, 255, 0.6);
  color: var(--nb-color-text-mid, #5b5973);
  cursor: pointer;
  width: 32px;
  height: 32px;
  border-radius: 10px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: background 200ms ease;

  &:hover {
    background: #fff;
    color: var(--nb-color-primary-strong, #4f46e5);
  }
}

.nkem-body {
  padding: 14px 22px 22px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.nkem-empty {
  text-align: center;
  color: var(--nb-color-text-dim, #9d9bb1);
  font-size: 13px;
  padding: 32px 0;
}

.nkem-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid transparent;
  transition: all 200ms ease;

  &:hover {
    background: var(--nb-bg-subtle, #fbfaff);
    border-color: var(--nb-border-soft, rgba(196, 181, 253, 0.32));
  }
}

.nkem-type {
  font-size: 10px;
  padding: 2px 8px;
  border-radius: 5px;
  font-weight: 700;
  flex-shrink: 0;

  &.nkem-type-error {
    background: linear-gradient(135deg, #fee2e2, #fecaca);
    color: #b91c1c;
  }

  &.nkem-type-breakthrough {
    background: linear-gradient(135deg, #ede9fe, #ddd6fe);
    color: #5b21b6;
  }
}

.nkem-text {
  flex: 1;
  font-size: 13px;
  color: var(--nb-color-text, #1e1b3a);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  line-height: 1.5;
}

.nkem-time {
  font-size: 11px;
  color: var(--nb-color-text-dim, #9d9bb1);
  white-space: nowrap;
  font-feature-settings: 'tnum';
}

.nkem-fade-enter-active,
.nkem-fade-leave-active {
  transition: opacity 200ms ease;

  .nkem-panel {
    transition: transform 280ms cubic-bezier(0.34, 1.56, 0.64, 1);
  }
}

.nkem-fade-enter-from,
.nkem-fade-leave-to {
  opacity: 0;

  .nkem-panel {
    transform: translateY(12px) scale(0.96);
  }
}

@media (prefers-reduced-motion: reduce) {
  .nkem-item,
  .nkem-fade-enter-active,
  .nkem-fade-leave-active,
  .nkem-fade-enter-active .nkem-panel,
  .nkem-fade-leave-active .nkem-panel {
    transition: none !important;
  }
}
</style>
