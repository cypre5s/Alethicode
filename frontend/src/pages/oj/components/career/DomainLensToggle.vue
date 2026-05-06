<template>
  <div v-if="majorCode" class="domain-lens-toggle">
    <button
      class="lens-btn"
      :class="{ active: showVariant }"
      :disabled="loading"
      @click="toggle"
    >
      <svg class="lens-icon" viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <circle cx="11" cy="11" r="8"/>
        <path d="m21 21-4.3-4.3"/>
      </svg>
      {{ loading ? '加载中…' : (showVariant ? '切回原版' : '我专业版') }}
    </button>
  </div>
</template>

<script>
import api from '@oj/api'
import { notify } from '@/utils/notifications'

export default {
  name: 'DomainLensToggle',
  props: {
    problemId: { type: [Number, String], required: true }
  },
  emits: ['variant-loaded', 'variant-cleared'],
  data () {
    return {
      majorCode: null,
      showVariant: false,
      loading: false,
      variant: null
    }
  },
  async created () {
    // profile 加载失败时静默：toggle 仅在 majorCode 非空时显示，
    // 拿不到 profile 等于「没填专业」，按钮不显示是预期降级路径
    try {
      const res = await api.getCareerProfile()
      const d = res.data.data
      if (d && d.major_code) {
        this.majorCode = d.major_code
      }
    } catch { /* expected: no profile / 401, button stays hidden */ }
  },
  methods: {
    async toggle () {
      if (this.showVariant) {
        this.showVariant = false
        this.$emit('variant-cleared')
        return
      }
      this.loading = true
      try {
        const res = await api.getCodingLensVariant(this.problemId, this.majorCode)
        if (res.data.data) {
          this.variant = res.data.data
          this.showVariant = true
          this.$emit('variant-loaded', this.variant)
        } else {
          // 后端返回 success 但 data=null：variant_not_available（rollback / critic 拒绝）
          notify.warning('专业化版本暂时不可用，已保留原题')
        }
      } catch {
        // 用户的明确动作（点击切换）失败必须提示，否则按钮看起来「点了没反应」
        notify.warning('专业化版本加载失败，请稍后再试')
      }
      this.loading = false
    }
  }
}
</script>

<style scoped>
.domain-lens-toggle {
  display: inline-block;
}
.lens-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  border: 1px solid #c7d2fe;
  border-radius: 8px;
  background: #eef2ff;
  color: #4338ca;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}
.lens-btn:hover:not(:disabled) {
  background: #e0e7ff;
  border-color: #a5b4fc;
}
.lens-btn.active {
  background: #6366f1;
  color: #fff;
  border-color: #6366f1;
}
.lens-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.lens-icon {
  flex-shrink: 0;
}
</style>
