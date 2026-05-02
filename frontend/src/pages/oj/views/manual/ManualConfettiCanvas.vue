<template>
  <canvas
    ref="canvasRef"
    class="manual-confetti-canvas"
    :class="{ 'is-active': active }"
    aria-hidden="true"
  />
</template>

<script>
const PARTICLE_LIMIT = 200
const COLORS = ['#6366f1', '#7c3aed', '#ec4899', '#f59e0b', '#10b981', '#06b6d4']

export default {
  name: 'ManualConfettiCanvas',
  data () {
    return {
      ctx: null,
      particles: [],
      rafId: null,
      active: false,
      width: 0,
      height: 0,
      dpr: 1
    }
  },
  mounted () {
    const canvas = this.$refs.canvasRef
    if (!canvas) return
    this.ctx = canvas.getContext('2d')
    this.handleResize()
    window.addEventListener('resize', this.handleResize, { passive: true })
  },
  beforeUnmount () {
    window.removeEventListener('resize', this.handleResize)
    if (this.rafId) cancelAnimationFrame(this.rafId)
  },
  methods: {
    handleResize () {
      const canvas = this.$refs.canvasRef
      if (!canvas) return
      this.dpr = Math.min(window.devicePixelRatio || 1, 2)
      this.width = window.innerWidth
      this.height = window.innerHeight
      canvas.width = this.width * this.dpr
      canvas.height = this.height * this.dpr
      canvas.style.width = `${this.width}px`
      canvas.style.height = `${this.height}px`
      this.ctx.setTransform(this.dpr, 0, 0, this.dpr, 0, 0)
    },
    /**
     * 在 (x, y) 坐标处喷出一束彩纸；支持多次叠加但总粒子数封顶 200。
     */
    burst ({ x, y, count = 60, spread = 60 } = {}) {
      const cx = typeof x === 'number' ? x : this.width / 2
      const cy = typeof y === 'number' ? y : this.height / 2
      const remaining = PARTICLE_LIMIT - this.particles.length
      const actual = Math.max(0, Math.min(count, remaining))
      for (let i = 0; i < actual; i += 1) {
        const angle = (Math.random() - 0.5) * Math.PI + (-Math.PI / 2)
        const speed = 4 + Math.random() * 8
        const offset = (Math.random() - 0.5) * spread
        this.particles.push({
          x: cx + offset,
          y: cy,
          vx: Math.cos(angle) * speed + (Math.random() - 0.5) * 1.5,
          vy: Math.sin(angle) * speed - Math.random() * 2,
          gravity: 0.2 + Math.random() * 0.15,
          friction: 0.985,
          color: COLORS[Math.floor(Math.random() * COLORS.length)],
          size: 4 + Math.random() * 6,
          rotation: Math.random() * Math.PI * 2,
          spin: (Math.random() - 0.5) * 0.3,
          life: 0,
          maxLife: 80 + Math.random() * 60
        })
      }
      if (!this.rafId) {
        this.active = true
        this.tick()
      }
    },
    tick () {
      if (!this.ctx) return
      this.ctx.clearRect(0, 0, this.width, this.height)
      const next = []
      for (const p of this.particles) {
        p.vx *= p.friction
        p.vy = p.vy * p.friction + p.gravity
        p.x += p.vx
        p.y += p.vy
        p.rotation += p.spin
        p.life += 1
        const alive = p.life < p.maxLife && p.y < this.height + 40
        if (!alive) continue
        const alpha = 1 - p.life / p.maxLife
        this.ctx.save()
        this.ctx.translate(p.x, p.y)
        this.ctx.rotate(p.rotation)
        this.ctx.globalAlpha = Math.max(0, alpha)
        this.ctx.fillStyle = p.color
        this.ctx.fillRect(-p.size / 2, -p.size / 4, p.size, p.size / 2)
        this.ctx.restore()
        next.push(p)
      }
      this.particles = next
      if (this.particles.length === 0) {
        this.rafId = null
        this.active = false
        return
      }
      this.rafId = requestAnimationFrame(this.tick)
    },
    stop () {
      if (this.rafId) cancelAnimationFrame(this.rafId)
      this.rafId = null
      this.particles = []
      this.active = false
      if (this.ctx) this.ctx.clearRect(0, 0, this.width, this.height)
    }
  }
}
</script>

<style lang="less" scoped>
.manual-confetti-canvas {
  position: fixed;
  inset: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  z-index: 800;
  opacity: 0;
  transition: opacity 200ms ease;

  &.is-active {
    opacity: 1;
  }
}

@media (prefers-reduced-motion: reduce) {
  .manual-confetti-canvas {
    display: none;
  }
}
</style>
