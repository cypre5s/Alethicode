<template>
  <button
    v-show="visible"
    type="button"
    class="manual-back-to-top"
    aria-label="回到顶部"
    @click="scrollUp"
  >
    <svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true">
      <path fill="currentColor" d="M12 4l8 8h-5v8h-6v-8H4z"/>
    </svg>
  </button>
</template>

<script>
export default {
  name: 'ManualBackToTop',
  props: {
    threshold: {
      type: Number,
      default: 240
    }
  },
  data () {
    return {
      visible: false,
      ticking: false
    }
  },
  mounted () {
    window.addEventListener('scroll', this.onScroll, { passive: true })
    this.onScroll()
  },
  beforeUnmount () {
    window.removeEventListener('scroll', this.onScroll)
  },
  methods: {
    onScroll () {
      if (this.ticking) return
      this.ticking = true
      requestAnimationFrame(() => {
        this.visible = window.scrollY > this.threshold
        this.ticking = false
      })
    },
    scrollUp () {
      window.scrollTo({ top: 0, behavior: 'smooth' })
    }
  }
}
</script>

<style lang="less" scoped>
.manual-back-to-top {
  position: fixed;
  right: 24px;
  bottom: 96px;
  width: 44px;
  height: 44px;
  border-radius: 50%;
  border: 1px solid var(--border-default);
  background: var(--bg-card);
  color: var(--text-secondary);
  cursor: pointer;
  z-index: 700;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  box-shadow: var(--shadow-md);
  transition: transform 0.18s ease, background 0.18s ease, color 0.18s ease;

  &:hover, &:focus-visible {
    color: #fff;
    background: var(--warm-grad-primary);
    transform: translateY(-2px);
    outline: none;
  }
}

@media (max-width: 640px) {
  .manual-back-to-top {
    right: 12px;
    bottom: 80px;
  }
}
</style>
