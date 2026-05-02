<template>
  <div class="flowing-text" ref="containerRef">
    <canvas ref="canvasRef" class="flowing-text__canvas" :width="canvasWidth" :height="canvasHeight"></canvas>
    <div class="flowing-text__fallback" v-if="!canvasSupported" aria-live="polite">
      <span
        v-for="(ch, i) in displayChars"
        :key="i"
        class="flowing-text__char"
        :style="{ animationDelay: `${i * 40}ms` }"
      >{{ ch }}</span>
    </div>
  </div>
</template>

<script>
import { prepareWithSegments, layoutWithLines } from '@chenglou/pretext'

const FLOWING_TIPS = [
  '每天写一点，胜过周末爆肝',
  '看错题比看答案更有用',
  'AI 是辅助，不是答案',
  '用自己的话讲一遍代码',
  '不会的概念用课件问答兜底',
  '提问越具体，回答越精准',
  '先审题，再写码，最后提交',
  '写完代码自己读一遍，比盲从 AI 安全得多'
]

export default {
  name: 'ManualFlowingText',
  data () {
    return {
      canvasSupported: true,
      canvasWidth: 760,
      canvasHeight: 38,
      displayChars: [],
      currentTipIndex: 0,
      animFrame: null,
      tipTimer: null,
      prepared: null,
      startTime: 0,
      opacity: 1,
      transitioning: false
    }
  },
  mounted () {
    this.checkCanvas()
    if (this.canvasSupported) {
      this.measureContainer()
      this.initPretext()
      window.addEventListener('resize', this.onResize)
    }
  },
  beforeUnmount () {
    if (this.animFrame) cancelAnimationFrame(this.animFrame)
    if (this.tipTimer) clearTimeout(this.tipTimer)
    window.removeEventListener('resize', this.onResize)
  },
  methods: {
    checkCanvas () {
      const canvas = this.$refs.canvasRef
      if (!canvas || !canvas.getContext) {
        this.canvasSupported = false
        this.displayChars = FLOWING_TIPS[0].split('')
        return
      }
    },
    measureContainer () {
      const container = this.$refs.containerRef
      if (!container) return
      const rect = container.getBoundingClientRect()
      const dpr = window.devicePixelRatio || 1
      this.canvasWidth = Math.floor(rect.width * dpr)
      this.canvasHeight = Math.floor(38 * dpr)
    },
    async initPretext () {
      try {
        this.startTime = performance.now()
        await this.prepareTip(this.currentTipIndex)
        this.animate()
        this.scheduleTipChange()
      } catch (err) {
        console.warn('[FlowingText] pretext init failed, using fallback', err)
        this.canvasSupported = false
        this.displayChars = FLOWING_TIPS[0].split('')
      }
    },
    async prepareTip (index) {
      const text = FLOWING_TIPS[index]
      const dpr = window.devicePixelRatio || 1
      const fontSize = 15 * dpr
      const font = `${fontSize}px "PingFang SC", "Microsoft YaHei", "Noto Sans SC", system-ui, sans-serif`
      this.prepared = prepareWithSegments(text, font)
    },
    animate () {
      const canvas = this.$refs.canvasRef
      if (!canvas) return
      const ctx = canvas.getContext('2d')
      if (!ctx) return

      const dpr = window.devicePixelRatio || 1
      const fontSize = 15 * dpr
      const font = `${fontSize}px "PingFang SC", "Microsoft YaHei", "Noto Sans SC", system-ui, sans-serif`

      const draw = (timestamp) => {
        const elapsed = (timestamp - this.startTime) / 1000
        ctx.clearRect(0, 0, this.canvasWidth, this.canvasHeight)

        if (!this.prepared) {
          this.animFrame = requestAnimationFrame(draw)
          return
        }

        const text = FLOWING_TIPS[this.currentTipIndex]
        const chars = text.split('')
        ctx.font = font
        ctx.textBaseline = 'middle'

        const totalWidth = ctx.measureText(text).width
        const startX = (this.canvasWidth - totalWidth) / 2
        const centerY = this.canvasHeight / 2

        let xOffset = startX
        for (let i = 0; i < chars.length; i++) {
          const ch = chars[i]
          const charWidth = ctx.measureText(ch).width
          const phase = i * 0.3
          const yOffset = Math.sin(elapsed * 2.2 + phase) * 4 * dpr
          const alpha = this.opacity * (0.7 + 0.3 * Math.sin(elapsed * 1.8 + phase * 0.7))

          ctx.fillStyle = this.getCharColor(i, chars.length, elapsed, alpha)
          ctx.fillText(ch, xOffset, centerY + yOffset)
          xOffset += charWidth
        }

        this.animFrame = requestAnimationFrame(draw)
      }

      this.animFrame = requestAnimationFrame(draw)
    },
    getCharColor (index, total, elapsed, alpha) {
      const t = (index / total + elapsed * 0.08) % 1
      const r = Math.floor(99 + 137 * t)
      const g = Math.floor(102 - 30 * t)
      const b = Math.floor(241 - 88 * t)
      return `rgba(${r}, ${g}, ${b}, ${alpha})`
    },
    scheduleTipChange () {
      this.tipTimer = setTimeout(() => {
        this.fadeOutAndSwitch()
      }, 4000)
    },
    fadeOutAndSwitch () {
      if (this.transitioning) return
      this.transitioning = true

      const fadeOut = (start) => {
        const progress = (performance.now() - start) / 400
        this.opacity = Math.max(0, 1 - progress)
        if (progress < 1) {
          requestAnimationFrame(() => fadeOut(start))
        } else {
          this.currentTipIndex = (this.currentTipIndex + 1) % FLOWING_TIPS.length
          this.prepareTip(this.currentTipIndex)
          this.fadeIn()
        }
      }
      fadeOut(performance.now())
    },
    fadeIn () {
      const fadeIn = (start) => {
        const progress = (performance.now() - start) / 400
        this.opacity = Math.min(1, progress)
        if (progress < 1) {
          requestAnimationFrame(() => fadeIn(start))
        } else {
          this.opacity = 1
          this.transitioning = false
          this.scheduleTipChange()
        }
      }
      fadeIn(performance.now())
    },
    onResize () {
      this.measureContainer()
    }
  }
}
</script>

<style lang="less" scoped>
.flowing-text {
  width: 100%;
  overflow: hidden;
  border-radius: var(--radius-pill);
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.04), rgba(236, 72, 153, 0.04));
  border: 1px solid rgba(99, 102, 241, 0.1);
  position: relative;
  height: 38px;
  backdrop-filter: blur(8px);
}

.flowing-text__canvas {
  display: block;
  width: 100%;
  height: 38px;
}

.flowing-text__fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  gap: 0;
  overflow: hidden;
}

.flowing-text__char {
  display: inline-block;
  font-size: 14px;
  font-weight: 500;
  color: var(--warm-primary-strong);
  animation: flowing-char-wave 2s ease-in-out infinite;
}

@keyframes flowing-char-wave {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-3px); }
}
</style>
