<template>
  <teleport to="body">
    <div class="nbr-overlay" @click.self="$emit('close')" @keydown.esc="$emit('close')">
      <div class="nbr-panel" role="dialog" aria-modal="true" aria-label="顿悟复习">
        <div class="nbr-header">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 2v8"/><path d="m4.93 10.93 1.41 1.41"/><path d="M2 18h2"/><path d="M20 18h2"/><path d="m19.07 10.93-1.41 1.41"/><path d="M22 22H2"/><path d="m8 22 4-10 4 10"/></svg>
          <span class="nbr-title">回顾你的顿悟</span>
        </div>

        <p class="nbr-insight">{{ entry.breakthrough_insight }}</p>

        <div class="nbr-actions">
          <button type="button" class="nbr-btn nbr-btn-remember" :disabled="loading" @click="rate('good')">
            我还记得
          </button>
          <button type="button" class="nbr-btn nbr-btn-review" :disabled="loading" @click="rate('again')">
            再看看
          </button>
        </div>
      </div>
    </div>
  </teleport>
</template>

<script>
import { ajax } from '@oj/api/shared'

export default {
  name: 'BreakthroughReviewModal',
  emits: ['close', 'rated'],
  props: {
    entry: { type: Object, required: true }
  },
  data () {
    return { loading: false }
  },
  methods: {
    async rate (rating) {
      this.loading = true
      try {
        await ajax(`ai/tutor/notebook/breakthrough/${this.entry.id}/rating`, 'post', { data: { rating } })
        this.$emit('rated')
      } catch {
        this.$emit('close')
      } finally {
        this.loading = false
      }
    }
  }
}
</script>

<style lang="less" scoped>
.nbr-overlay {
  position: fixed; inset: 0; z-index: 2000;
  display: flex; align-items: center; justify-content: center;
  background: rgba(30, 27, 58, 0.42); backdrop-filter: blur(4px);
}
.nbr-panel {
  background: #fff; border-radius: 22px; padding: 26px;
  width: 100%; max-width: 440px;
  box-shadow: 0 24px 60px rgba(30, 27, 58, 0.22);
  display: flex; flex-direction: column; gap: 18px;
}
.nbr-header {
  display: flex; align-items: center; gap: 10px;
  color: #7c3aed;
}
.nbr-title {
  font-size: 17px; font-weight: 800; color: #1e1b3a;
  letter-spacing: -0.2px;
}
.nbr-insight {
  font-size: 14px; color: #1e1b3a; line-height: 1.75;
  background: linear-gradient(135deg, #faf5ff 0%, #fdf4ff 100%);
  border: 1px solid rgba(196, 181, 253, 0.5);
  border-radius: 14px;
  padding: 14px 16px; margin: 0;
}
.nbr-actions { display: flex; gap: 10px; }
.nbr-btn {
  flex: 1; border: none; padding: 11px 18px; border-radius: 999px;
  font-size: 14px; font-weight: 700; cursor: pointer; font-family: inherit;
  min-height: 44px; transition: all 200ms ease;
  &:disabled { opacity: 0.5; cursor: not-allowed; }
  &.nbr-btn-remember {
    background: linear-gradient(135deg, #10b981, #06b6d4); color: #fff;
    box-shadow: 0 4px 12px rgba(16, 185, 129, 0.32);
    &:hover { transform: translateY(-1px); box-shadow: 0 6px 16px rgba(16, 185, 129, 0.42); }
  }
  &.nbr-btn-review {
    background: #fbfaff; color: #5b5973; border: 1px solid rgba(196, 181, 253, 0.4);
    &:hover { background: #fff; color: #4f46e5; border-color: rgba(165, 180, 252, 0.6); }
  }
}
@media (prefers-reduced-motion: reduce) { .nbr-btn { transition: none; } }
</style>
