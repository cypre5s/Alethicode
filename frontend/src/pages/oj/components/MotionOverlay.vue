<template>
  <Teleport to="body">
    <Transition name="mo-fade" @after-leave="$emit('done')">
      <div v-if="active" class="mo-backdrop" @click="dismiss">
        <canvas ref="cvs" class="mo-canvas" />
        <Transition name="mo-text">
          <div v-if="showText" class="mo-text">经纬天下</div>
        </Transition>
      </div>
    </Transition>
  </Teleport>
</template>

<script>
const W_COUNT = 24
const F_COUNT = 18
const DURATION = 5000
const TEXT_DELAY = 1600

function lerp (a, b, t) { return a + (b - a) * t }

export default {
  name: 'MotionOverlay',
  emits: ['done'],
  data: () => ({ active: false, showText: false }),
  methods: {
    play () {
      this.active = true
      this.$nextTick(() => {
        this._startAnim()
        setTimeout(() => { this.showText = true }, TEXT_DELAY)
        this._timer = setTimeout(() => this.dismiss(), DURATION)
      })
    },
    dismiss () {
      clearTimeout(this._timer)
      cancelAnimationFrame(this._raf)
      this.active = false
      this.showText = false
    },
    _startAnim () {
      const cvs = this.$refs.cvs
      if (!cvs) return
      const dpr = window.devicePixelRatio || 1
      const w = window.innerWidth
      const h = window.innerHeight
      cvs.width = w * dpr
      cvs.height = h * dpr
      cvs.style.width = w + 'px'
      cvs.style.height = h + 'px'
      const ctx = cvs.getContext('2d')
      ctx.scale(dpr, dpr)

      const warps = Array.from({ length: W_COUNT }, (_, i) => ({
        x: (w / (W_COUNT + 1)) * (i + 1),
        phase: Math.random() * Math.PI * 2,
        hue: 35 + Math.random() * 15
      }))
      const wefts = Array.from({ length: F_COUNT }, (_, i) => ({
        y: (h / (F_COUNT + 1)) * (i + 1),
        phase: Math.random() * Math.PI * 2,
        hue: 200 + Math.random() * 30
      }))

      const t0 = performance.now()
      const frame = (now) => {
        const elapsed = now - t0
        const progress = Math.min(elapsed / DURATION, 1)
        ctx.clearRect(0, 0, w, h)

        const growWarp = Math.min(progress * 3, 1)
        const growWeft = Math.min(Math.max(progress - 0.15, 0) * 3, 1)
        const shimmer = Math.sin(elapsed * 0.002) * 0.15 + 0.85

        ctx.lineWidth = 1.5
        for (const warp of warps) {
          const alpha = shimmer * growWarp * (progress < 0.85 ? 1 : lerp(1, 0, (progress - 0.85) / 0.15))
          ctx.strokeStyle = `hsla(${warp.hue}, 70%, 55%, ${alpha})`
          ctx.beginPath()
          const len = h * growWarp
          for (let py = 0; py <= len; py += 4) {
            const dx = Math.sin(py * 0.012 + warp.phase + elapsed * 0.001) * 6
            if (py === 0) ctx.moveTo(warp.x + dx, py)
            else ctx.lineTo(warp.x + dx, py)
          }
          ctx.stroke()
        }

        ctx.lineWidth = 1.5
        for (const weft of wefts) {
          const alpha = shimmer * growWeft * (progress < 0.85 ? 1 : lerp(1, 0, (progress - 0.85) / 0.15))
          ctx.strokeStyle = `hsla(${weft.hue}, 60%, 50%, ${alpha})`
          ctx.beginPath()
          const len = w * growWeft
          for (let px = 0; px <= len; px += 4) {
            const dy = Math.sin(px * 0.01 + weft.phase + elapsed * 0.0008) * 5
            if (px === 0) ctx.moveTo(px, weft.y + dy)
            else ctx.lineTo(px, weft.y + dy)
          }
          ctx.stroke()
        }

        if (progress > 0.3 && progress < 0.85) {
          const sparkCount = 8
          for (let i = 0; i < sparkCount; i++) {
            const sx = Math.random() * w
            const sy = Math.random() * h
            const sr = 1.5 + Math.random() * 2
            const sa = (Math.random() * 0.4 + 0.2) * shimmer
            ctx.fillStyle = `hsla(42, 90%, 65%, ${sa})`
            ctx.beginPath()
            ctx.arc(sx, sy, sr, 0, Math.PI * 2)
            ctx.fill()
          }
        }

        if (progress < 1) this._raf = requestAnimationFrame(frame)
      }
      this._raf = requestAnimationFrame(frame)
    }
  },
  beforeUnmount () {
    clearTimeout(this._timer)
    cancelAnimationFrame(this._raf)
  }
}
</script>

<style scoped>
.mo-backdrop {
  position: fixed;
  inset: 0;
  z-index: 99999;
  background: rgba(10, 12, 20, 0.88);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}
.mo-canvas {
  position: absolute;
  inset: 0;
}
.mo-text {
  position: relative;
  z-index: 1;
  font-size: clamp(2.4rem, 6vw, 5rem);
  font-weight: 700;
  letter-spacing: 0.35em;
  color: #f5d27c;
  text-shadow: 0 0 30px rgba(245, 210, 124, 0.5), 0 0 60px rgba(245, 210, 124, 0.2);
  user-select: none;
}
.mo-fade-enter-active { transition: opacity 0.5s ease; }
.mo-fade-leave-active { transition: opacity 0.8s ease; }
.mo-fade-enter-from,
.mo-fade-leave-to { opacity: 0; }
.mo-text-enter-active { transition: all 1.2s cubic-bezier(0.22, 1, 0.36, 1); }
.mo-text-enter-from { opacity: 0; transform: scale(0.7) translateY(20px); }
</style>
