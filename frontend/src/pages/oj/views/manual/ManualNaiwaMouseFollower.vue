<template>
  <div ref="dotRef" class="manual-naiwa-follower" aria-hidden="true">
    <img :src="src" alt="">
  </div>
</template>

<script>
import { NAIWA_HERO } from './manualContent.js'

export default {
  name: 'ManualNaiwaMouseFollower',
  data () {
    return {
      src: NAIWA_HERO,
      mouseX: 0,
      mouseY: 0,
      currentX: 0,
      currentY: 0,
      rafId: null,
      visible: false
    }
  },
  mounted () {
    this.currentX = window.innerWidth / 2
    this.currentY = window.innerHeight / 2
    this.mouseX = this.currentX
    this.mouseY = this.currentY
    window.addEventListener('mousemove', this.onMove, { passive: true })
    window.addEventListener('mouseleave', this.onLeave)
    window.addEventListener('mouseenter', this.onEnter)
    this.rafId = requestAnimationFrame(this.tick)
  },
  beforeUnmount () {
    window.removeEventListener('mousemove', this.onMove)
    window.removeEventListener('mouseleave', this.onLeave)
    window.removeEventListener('mouseenter', this.onEnter)
    if (this.rafId) cancelAnimationFrame(this.rafId)
  },
  methods: {
    onMove (event) {
      this.mouseX = event.clientX
      this.mouseY = event.clientY
      if (!this.visible) {
        this.visible = true
        if (this.$refs.dotRef) this.$refs.dotRef.style.opacity = '1'
      }
    },
    onLeave () {
      if (this.$refs.dotRef) this.$refs.dotRef.style.opacity = '0'
    },
    onEnter () {
      if (this.$refs.dotRef) this.$refs.dotRef.style.opacity = '1'
    },
    tick () {
      const dx = this.mouseX - this.currentX
      const dy = this.mouseY - this.currentY
      this.currentX += dx * 0.18
      this.currentY += dy * 0.18
      const node = this.$refs.dotRef
      if (node) {
        node.style.transform = `translate3d(${this.currentX - 14}px, ${this.currentY - 14}px, 0)`
      }
      this.rafId = requestAnimationFrame(this.tick)
    }
  }
}
</script>

<style lang="less" scoped>
.manual-naiwa-follower {
  position: fixed;
  left: 0;
  top: 0;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  pointer-events: none;
  z-index: 600;
  opacity: 0;
  transition: opacity 240ms ease;
  filter: drop-shadow(0 4px 10px rgba(99, 102, 241, 0.25));

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    border-radius: 50%;
    background: var(--bg-card);
    pointer-events: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .manual-naiwa-follower { display: none; }
}

@media (max-width: 768px) {
  .manual-naiwa-follower { display: none; }
}
</style>
