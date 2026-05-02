<template>
  <div class="nmb-strip-wrap">
    <div v-if="loading" class="nmb-strip nmb-strip-loading" aria-busy="true">
      <span v-for="i in 4" :key="i" class="nmb-skeleton"></span>
    </div>
    <div v-else-if="!milestones.length" class="nmb-strip nmb-strip-empty">
      <span class="nmb-empty-icon" aria-hidden="true">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="8" r="7"/><polyline points="8.21 13.89 7 23 12 20 17 23 15.79 13.88"/></svg>
      </span>
      <span>持续记录错题，解锁你的第一枚学习徽章</span>
    </div>
    <div v-else class="nmb-strip">
      <div class="nmb-summary">
        <span class="nmb-trophy" aria-hidden="true">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M6 9H4.5a2.5 2.5 0 0 1 0-5H6"/><path d="M18 9h1.5a2.5 2.5 0 0 0 0-5H18"/><path d="M4 22h16"/><path d="M10 14.66V17c0 .55-.47.98-.97 1.21C7.85 18.75 7 20.24 7 22"/><path d="M14 14.66V17c0 .55.47.98.97 1.21C16.15 18.75 17 20.24 17 22"/><path d="M18 2H6v7a6 6 0 0 0 12 0V2Z"/></svg>
        </span>
        <div class="nmb-summary-text">
          <span class="nmb-summary-num">{{ earnedCount }}</span>
          <span class="nmb-summary-total">/ {{ milestones.length }}</span>
          <span class="nmb-summary-label">学习徽章</span>
        </div>
      </div>

      <div class="nmb-pills" role="list">
        <span
          v-for="badge in earnedPreview"
          :key="badge.badge_key"
          role="listitem"
          class="nmb-pill"
          :title="badge.description"
        >
          <span class="nmb-pill-icon" aria-hidden="true">
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="8" r="7"/><polyline points="8.21 13.89 7 23 12 20 17 23 15.79 13.88"/></svg>
          </span>
          <span>{{ badge.label }}</span>
        </span>
        <span v-if="earnedExtra > 0" class="nmb-pill nmb-pill-extra">+{{ earnedExtra }}</span>
      </div>

      <button type="button" class="nmb-view-all" @click="showModal = true">
        查看全部
        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="9 18 15 12 9 6"/></svg>
      </button>
    </div>

    <teleport to="body">
      <transition name="nmb-fade">
        <div v-if="showModal" class="nmb-overlay" @click.self="showModal = false" @keydown.esc="showModal = false" tabindex="-1">
          <div class="nmb-panel" role="dialog" aria-modal="true" aria-label="学习里程碑">
            <header class="nmb-modal-head">
              <div>
                <div class="nmb-modal-title">学习里程碑</div>
                <div class="nmb-modal-sub">已解锁 {{ earnedCount }} / {{ milestones.length }} 枚徽章</div>
              </div>
              <button type="button" class="nmb-close" aria-label="关闭" @click="showModal = false">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M18 6 6 18"/><path d="m6 6 12 12"/></svg>
              </button>
            </header>
            <div class="nmb-grid">
              <div
                v-for="badge in milestones"
                :key="badge.badge_key"
                class="nmb-badge"
                :class="{ 'is-earned': badge.earned }"
              >
                <div class="nmb-badge-icon">
                  <svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" :stroke-width="badge.earned ? 2.2 : 1.6" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="8" r="7"/><polyline points="8.21 13.89 7 23 12 20 17 23 15.79 13.88"/></svg>
                </div>
                <div class="nmb-badge-label">{{ badge.label }}</div>
                <div class="nmb-badge-desc">{{ badge.description }}</div>
                <div v-if="badge.earned" class="nmb-badge-date">{{ formatDate(badge.earned_at) }}</div>
                <div v-else class="nmb-badge-locked">尚未解锁</div>
              </div>
            </div>
          </div>
        </div>
      </transition>
    </teleport>
  </div>
</template>

<script>
import { ajax } from '@oj/api/shared'

const PREVIEW_LIMIT = 4

export default {
  name: 'NotebookMilestoneBadges',
  data () {
    return { showModal: false, loading: false, milestones: [] }
  },
  computed: {
    earned () { return this.milestones.filter(m => m.earned) },
    earnedCount () { return this.earned.length },
    earnedPreview () { return this.earned.slice(0, PREVIEW_LIMIT) },
    earnedExtra () { return Math.max(0, this.earnedCount - PREVIEW_LIMIT) }
  },
  mounted () { this.load() },
  methods: {
    async load () {
      this.loading = true
      try {
        const res = await ajax('ai/tutor/notebook/milestones', 'get')
        this.milestones = (res.data && res.data.data && res.data.data.milestones) || []
      } finally { this.loading = false }
    },
    formatDate (val) {
      if (!val) return ''
      const d = new Date(val)
      return Number.isNaN(d.getTime()) ? val : d.toLocaleDateString()
    }
  }
}
</script>

<style lang="less" scoped>
.nmb-strip-wrap {
  width: 100%;
}

.nmb-strip {
  display: flex;
  align-items: center;
  gap: 14px;
  background: var(--nb-bg-surface);
  border: 1px solid var(--nb-border-soft);
  border-radius: var(--nb-radius-lg);
  padding: 12px 16px;
  box-shadow: var(--nb-shadow-soft);
  flex-wrap: wrap;
}

.nmb-strip-loading {
  min-height: 56px;
}

.nmb-strip-empty {
  color: var(--nb-color-text-dim);
  font-size: 13px;
  background: var(--nb-bg-subtle);
  border-style: dashed;
}

.nmb-empty-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 8px;
  background: rgba(196, 181, 253, 0.32);
  color: var(--nb-color-primary-strong);
}

.nmb-skeleton {
  height: 28px;
  width: 90px;
  border-radius: 999px;
  background: linear-gradient(90deg, #f1f5f9 25%, #e2e8f0 50%, #f1f5f9 75%);
  background-size: 200% 100%;
  animation: nmb-shimmer 1.4s linear infinite;
}

.nmb-summary {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.nmb-trophy {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 10px;
  background: var(--nb-grad-warm);
  color: #fff;
  box-shadow: 0 4px 10px rgba(236, 72, 153, 0.22);
}

.nmb-summary-text {
  display: inline-flex;
  align-items: baseline;
  gap: 4px;
  color: var(--nb-color-text);
}

.nmb-summary-num {
  font-size: 18px;
  font-weight: 800;
  background: var(--nb-grad-primary);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
  font-feature-settings: 'tnum';
}

.nmb-summary-total {
  font-size: 13px;
  color: var(--nb-color-text-dim);
  font-weight: 600;
  font-feature-settings: 'tnum';
}

.nmb-summary-label {
  font-size: 13px;
  color: var(--nb-color-text-mid);
  font-weight: 600;
  margin-left: 4px;
}

.nmb-pills {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  flex: 1;
  min-width: 0;
}

.nmb-pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 5px 12px;
  background: linear-gradient(135deg, rgba(196, 181, 253, 0.32) 0%, rgba(251, 207, 232, 0.32) 100%);
  border: 1px solid var(--nb-border-soft);
  color: var(--nb-color-primary-strong);
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}

.nmb-pill-icon {
  display: inline-flex;
  line-height: 0;
}

.nmb-pill-extra {
  background: var(--nb-bg-subtle);
  color: var(--nb-color-text-mid);
}

.nmb-view-all {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: none;
  background: transparent;
  color: var(--nb-color-primary-strong);
  font-size: 12px;
  font-weight: 600;
  font-family: inherit;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 8px;
  margin-left: auto;
  transition: background var(--nb-transition);

  &:hover {
    background: rgba(196, 181, 253, 0.22);
  }

  &:focus-visible {
    outline: none;
    box-shadow: var(--nb-shadow-glow);
  }
}

.nmb-overlay {
  position: fixed;
  inset: 0;
  z-index: 2000;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(30, 27, 58, 0.42);
  backdrop-filter: blur(4px);
}

.nmb-panel {
  background: #fff;
  border-radius: var(--nb-radius-xl, 22px);
  padding: 24px;
  width: 100%;
  max-width: 560px;
  max-height: 80vh;
  overflow-y: auto;
  box-shadow: 0 24px 60px rgba(30, 27, 58, 0.25);
}

.nmb-modal-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 18px;
}

.nmb-modal-title {
  font-size: 18px;
  font-weight: 800;
  color: var(--nb-color-text, #1e1b3a);
}

.nmb-modal-sub {
  font-size: 12px;
  color: var(--nb-color-text-dim, #9d9bb1);
  margin-top: 2px;
}

.nmb-close {
  border: none;
  background: var(--nb-bg-subtle, #fbfaff);
  color: var(--nb-color-text-mid, #5b5973);
  cursor: pointer;
  width: 32px;
  height: 32px;
  border-radius: 10px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: background var(--nb-transition, 200ms);

  &:hover {
    background: rgba(196, 181, 253, 0.32);
    color: var(--nb-color-primary-strong, #4f46e5);
  }
}

.nmb-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 10px;
}

.nmb-badge {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  gap: 6px;
  padding: 16px 10px;
  border-radius: 14px;
  background: var(--nb-bg-subtle, #fbfaff);
  border: 1px solid var(--nb-border-soft, rgba(196, 181, 253, 0.32));
  color: var(--nb-color-text-dim, #9d9bb1);
  transition: transform var(--nb-transition, 200ms), box-shadow var(--nb-transition, 200ms);

  &.is-earned {
    background: linear-gradient(135deg, #f5f3ff 0%, #fdf4ff 100%);
    border-color: var(--nb-border-mid, rgba(165, 180, 252, 0.45));
    color: var(--nb-color-primary-strong, #4f46e5);

    .nmb-badge-icon {
      background: var(--nb-grad-primary, linear-gradient(135deg, #6366f1, #7c3aed));
      color: #fff;
    }
  }

  &:hover {
    transform: translateY(-2px);
    box-shadow: var(--nb-shadow-mid, 0 6px 18px rgba(99, 102, 241, 0.12));
  }
}

.nmb-badge-icon {
  width: 44px;
  height: 44px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.7);
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.nmb-badge-label {
  font-size: 13px;
  font-weight: 700;
  color: var(--nb-color-text, #1e1b3a);
}

.nmb-badge-desc {
  font-size: 11px;
  color: var(--nb-color-text-dim, #9d9bb1);
  line-height: 1.4;
  min-height: 28px;
}

.nmb-badge-date {
  font-size: 11px;
  color: var(--nb-color-primary, #6366f1);
  font-weight: 600;
}

.nmb-badge-locked {
  font-size: 11px;
  color: var(--nb-color-text-dim, #9d9bb1);
  font-style: italic;
  opacity: 0.7;
}

.nmb-fade-enter-active,
.nmb-fade-leave-active {
  transition: opacity 200ms ease;

  .nmb-panel {
    transition: transform 220ms cubic-bezier(0.34, 1.56, 0.64, 1);
  }
}

.nmb-fade-enter-from,
.nmb-fade-leave-to {
  opacity: 0;

  .nmb-panel {
    transform: translateY(12px) scale(0.96);
  }
}

@keyframes nmb-shimmer {
  0% { background-position: -200% 0; }
  100% { background-position: 200% 0; }
}

@media (prefers-reduced-motion: reduce) {
  .nmb-skeleton,
  .nmb-fade-enter-active,
  .nmb-fade-leave-active,
  .nmb-fade-enter-active .nmb-panel,
  .nmb-fade-leave-active .nmb-panel,
  .nmb-badge,
  .nmb-view-all {
    animation: none !important;
    transition: none !important;
  }
}
</style>
