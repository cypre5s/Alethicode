<template>
  <div class="manual-reading-progress" role="progressbar" :aria-valuenow="percent" aria-valuemin="0" aria-valuemax="100">
    <div class="manual-reading-progress__bar" :style="{ transform: `scaleX(${percent / 100})` }" />
  </div>
</template>

<script>
export default {
  name: 'ManualReadingProgress',
  props: {
    target: {
      type: String,
      default: ''
    }
  },
  data () {
    return {
      percent: 0,
      ticking: false
    }
  },
  mounted () {
    window.addEventListener('scroll', this.onScroll, { passive: true })
    window.addEventListener('resize', this.onScroll, { passive: true })
    this.onScroll()
  },
  beforeUnmount () {
    window.removeEventListener('scroll', this.onScroll)
    window.removeEventListener('resize', this.onScroll)
  },
  methods: {
    onScroll () {
      if (this.ticking) return
      this.ticking = true
      requestAnimationFrame(() => {
        const root = this.target ? document.querySelector(this.target) : document.documentElement
        if (!root) {
          this.ticking = false
          return
        }
        const total = root.scrollHeight - window.innerHeight
        const pct = total <= 0 ? 0 : (window.scrollY / total) * 100
        this.percent = Math.max(0, Math.min(100, pct))
        this.ticking = false
      })
    }
  }
}
</script>

<style lang="less" scoped>
.manual-reading-progress {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: transparent;
  z-index: 1100;
  pointer-events: none;
}

.manual-reading-progress__bar {
  height: 100%;
  width: 100%;
  transform-origin: 0 0;
  background: linear-gradient(90deg, #6366f1 0%, #ec4899 100%);
  transition: transform 80ms linear;
  box-shadow: 0 0 8px rgba(124, 58, 237, 0.4);
}
</style>
